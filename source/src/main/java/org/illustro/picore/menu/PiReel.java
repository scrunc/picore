package org.illustro.picore.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Standardized animated reward reel and crate opening reveal engine.
 * Plays a ticking carousel spin animation across a slot row, settles on final rewards,
 * and handles safe item collection (auto-collecting on close if uncollected).
 */
public class PiReel {

    private static final int[] DEFAULT_REEL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int DEFAULT_COLLECT_SLOT = 22;

    private final Plugin plugin;
    private final String title;
    private final List<ItemStack> rewards = new ArrayList<>();
    private final List<ItemStack> poolIcons = new ArrayList<>();
    private int[] reelSlots = DEFAULT_REEL_SLOTS;
    private int collectSlot = DEFAULT_COLLECT_SLOT;
    private int spinTicks = 30;
    private Consumer<Player> onFinish;

    public PiReel(Plugin plugin, String title) {
        this.plugin = plugin;
        this.title = title != null ? title : "&6&lReward Reveal";
    }

    public static PiReel create(Plugin plugin, String title) {
        return new PiReel(plugin, title);
    }

    public PiReel rewards(List<ItemStack> rewards) {
        this.rewards.clear();
        if (rewards != null) this.rewards.addAll(rewards);
        return this;
    }

    public PiReel poolIcons(List<ItemStack> icons) {
        this.poolIcons.clear();
        if (icons != null) this.poolIcons.addAll(icons);
        return this;
    }

    public PiReel slots(int[] slots) {
        if (slots != null && slots.length > 0) this.reelSlots = slots;
        return this;
    }

    public PiReel collectSlot(int slot) {
        this.collectSlot = slot;
        return this;
    }

    public PiReel spinDuration(int ticks) {
        this.spinTicks = Math.max(10, ticks);
        return this;
    }

    public PiReel onFinish(Consumer<Player> handler) {
        this.onFinish = handler;
        return this;
    }

    public void play(Player player) {
        if (player == null || !player.isOnline()) return;

        PiMenu menu = PiMenu.create(title, 3);
        menu.frame(Material.GRAY_STAINED_GLASS_PANE);

        // Marker indicator above center slot
        menu.button(4, PiButton.create(Material.LIME_STAINED_GLASS_PANE, "&a&l▼ &7Winning Item &a&l▼"));

        // If player closes early, auto-give remaining rewards
        menu.onClose(p -> {
            for (ItemStack it : rewards) {
                if (it != null && it.getType() != Material.AIR) {
                    p.getInventory().addItem(it.clone());
                }
            }
            rewards.clear();
        });

        menu.open(player);

        final List<ItemStack> displayPool = !poolIcons.isEmpty() ? poolIcons : (!rewards.isEmpty() ? rewards : List.of(new ItemStack(Material.GOLD_INGOT)));
        final Random rng = new Random();

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getOpenInventory() == null) {
                    cancel();
                    return;
                }

                if (elapsed < spinTicks) {
                    // Spin animation
                    for (int slot : reelSlots) {
                        ItemStack randomIcon = displayPool.get(rng.nextInt(displayPool.size())).clone();
                        menu.updateSlot(player, slot, PiButton.create(randomIcon));
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.8f + (elapsed / (float) spinTicks));
                    elapsed += 2;
                    return;
                }

                // Settle on final rewards
                for (int slot : reelSlots) {
                    menu.updateSlot(player, slot, PiButton.create(Material.GRAY_STAINED_GLASS_PANE, " "));
                }

                for (int i = 0; i < rewards.size() && i < reelSlots.length; i++) {
                    int slot = reelSlots[i];
                    ItemStack reward = rewards.get(i);
                    menu.updateSlot(player, slot, PiButton.create(reward)
                            .onClick((p, e) -> {
                                p.getInventory().addItem(reward.clone());
                                rewards.remove(reward);
                                menu.updateSlot(p, slot, PiButton.create(new org.bukkit.inventory.ItemStack(Material.AIR)));
                                PiSound.success(p);
                                if (rewards.isEmpty()) {
                                    p.closeInventory();
                                }
                            }));
                }

                // Collect All Button
                menu.button(collectSlot, PiButton.create(Material.LIME_DYE, "&a&lCOLLECT ALL", "&7Click to collect all rewards.")
                        .onClick((p, e) -> {
                            for (ItemStack it : new ArrayList<>(rewards)) {
                                if (it != null && it.getType() != Material.AIR) {
                                    p.getInventory().addItem(it.clone());
                                }
                            }
                            rewards.clear();
                            PiSound.success(p);
                            p.closeInventory();
                        }));
                menu.refresh(player);

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                if (onFinish != null) onFinish.accept(player);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }
}
