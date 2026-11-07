package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Standard 4-row (36-slot) Daily Rewards & Streak GUI for PiCore.
 * Displays a 7-day progression ladder powered by PrimalVar.
 * Handles visual states for Claimed, Ready-to-Claim (glinting), and Locked tiers.
 */
public class PiDailyMenu {

    public record DailyTier(
            int day,
            String title,
            int coins,
            int exp,
            Material material,
            String bonusText
    ) {}

    // Default 7-day reward ladder (matches primalvar ladder spec)
    private static final List<DailyTier> DEFAULT_LADDER = List.of(
            new DailyTier(1, "Starter Supply", 250, 10, Material.IRON_INGOT, null),
            new DailyTier(2, "Explorer Pack", 500, 20, Material.GOLD_INGOT, null),
            new DailyTier(3, "Miner Fortune", 1000, 35, Material.DIAMOND, null),
            new DailyTier(4, "Arcane Cache", 1500, 50, Material.EMERALD, null),
            new DailyTier(5, "Netherite Token", 2500, 75, Material.NETHERITE_SCRAP, null),
            new DailyTier(6, "Mythic Shard", 4000, 100, Material.AMETHYST_SHARD, null),
            new DailyTier(7, "Champion Jackpot", 10000, 250, Material.NETHER_STAR, "&e+ Weekly Crate Key")
    );

    private final String title;
    private List<DailyTier> ladder = DEFAULT_LADDER;
    private BiConsumer<Player, DailyTier> onClaim;

    public PiDailyMenu() {
        this("&8Daily Rewards & Streaks");
    }

    public PiDailyMenu(String title) {
        this.title = title != null ? title : "&8Daily Rewards & Streaks";
        loadFromPrimalVar();
    }

    public static PiDailyMenu create() {
        return new PiDailyMenu();
    }

    /**
     * Loads the 7-day reward ladder dynamically from PiCore's PrimalVar manager if available.
     */
    public PiDailyMenu loadFromPrimalVar() {
        org.illustro.picore.dynvar.DynVarManager mgr = org.illustro.picore.dynvar.DynVarManager.getInstance();
        if (mgr != null) {
            org.illustro.picore.dynvar.Ladder l = mgr.getLadder("daily_rewards");
            if (l != null && l.getRowCount() > 0) {
                List<DailyTier> tiers = new ArrayList<>();
                for (int i = 0; i < l.getRowCount(); i++) {
                    List<Object> row = l.getRow(i);
                    if (row != null && row.size() >= 3) {
                        String title = String.valueOf(row.get(0));
                        int coins = parseInt(row.get(1), 250);
                        int exp = parseInt(row.get(2), 10);
                        String bonus = (row.size() > 4 && !"none".equalsIgnoreCase(String.valueOf(row.get(4))))
                                ? "&e+ " + row.get(4)
                                : null;
                        String matName = String.valueOf(row.get(row.size() - 1));
                        Material mat = parseMaterial(matName, Material.CHEST);
                        tiers.add(new DailyTier(i + 1, title, coins, exp, mat, bonus));
                    }
                }
                if (!tiers.isEmpty()) {
                    this.ladder = tiers;
                }
            }
        }
        return this;
    }

    private static int parseInt(Object obj, int def) {
        if (obj == null) return def;
        try {
            return Integer.parseInt(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            Material m = Material.matchMaterial(name.trim().toUpperCase());
            return m != null ? m : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public PiDailyMenu ladder(List<DailyTier> ladder) {
        if (ladder != null && !ladder.isEmpty()) {
            this.ladder = ladder;
        }
        return this;
    }

    public PiDailyMenu onClaim(BiConsumer<Player, DailyTier> handler) {
        this.onClaim = handler;
        return this;
    }

    /**
     * Opens the daily menu for the player with their current streak and claim state.
     *
     * @param player Target player
     * @param currentStreak 0-based index of next reward (0 to 6)
     * @param canClaimToday True if the player has not claimed today's reward yet
     */
    public void open(Player player, int currentStreak, boolean canClaimToday) {
        PiMenu menu = PiMenu.create(title, 4);
        menu.frame(Material.GRAY_STAINED_GLASS_PANE);

        // Slot 0: Information Manual
        menu.button(0, PiButton.create(Material.BOOK,
                "&e&lDaily Streak System",
                "&7Log in consecutive days to earn",
                "&7increasingly powerful rewards!",
                "",
                "&7Streak resets to Day 1 if you",
                "&7miss more than &c48 hours&7."
        ));

        // Slot 4: Player Profile Summary
        ItemStack head = PiSkull.create(player.getUniqueId(), player.getName());
        ItemMeta headMeta = head.getItemMeta();
        if (headMeta != null) {
            headMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&l" + player.getName() + "'s Progress"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Current Streak: &e" + currentStreak + " &7/ &67 Days"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Status: " + (canClaimToday ? "&aReward Available!" : "&7Claimed for today")));
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        menu.button(4, PiButton.create(head));

        // Slot 8: Close Button
        menu.button(8, PiButton.create(Material.BARRIER, "&c&lClose Menu")
                .onClick((p, e) -> {
                    PiSound.click(p);
                    p.closeInventory();
                }));

        // Slots 10 - 16: The 7-Day Reward Ladder
        for (int i = 0; i < 7 && i < ladder.size(); i++) {
            final int dayIndex = i;
            final DailyTier tier = ladder.get(dayIndex);
            final int slot = 10 + dayIndex;

            if (dayIndex < currentStreak) {
                // State 1: Already Claimed
                menu.button(slot, PiButton.create(
                        Material.LIME_STAINED_GLASS_PANE,
                        "&a✔ Day " + tier.day() + " - " + tier.title(),
                        "&7Reward: &a$" + tier.coins() + " &7| &b" + tier.exp() + " EXP",
                        "",
                        "&a&l✔ ALREADY CLAIMED"
                ).onClick((p, e) -> PiSound.deny(p)));

            } else if (dayIndex == currentStreak && canClaimToday) {
                // State 2: READY TO CLAIM TODAY!
                List<String> claimLore = new ArrayList<>();
                claimLore.add("&7Reward:");
                claimLore.add("  &a● +$" + tier.coins() + " Coins");
                claimLore.add("  &b● +" + tier.exp() + " Adventure EXP");
                if (tier.bonusText() != null) {
                    claimLore.add("  " + tier.bonusText());
                }
                claimLore.add("");
                claimLore.add("&a&l▶ CLICK TO CLAIM TODAY'S REWARD!");

                PiButton claimBtn = PiButton.create(tier.material(), "&e&l✦ Day " + tier.day() + " - " + tier.title(), claimLore.toArray(new String[0]))
                        .onClick((p, e) -> {
                            PiSound.success(p);
                            if (onClaim != null) {
                                onClaim.accept(p, tier);
                            }
                            // Live update menu in place
                            open(p, currentStreak + 1, false);
                        });

                menu.button(slot, claimBtn);

            } else if (dayIndex == currentStreak && !canClaimToday) {
                // State 3: Current day claimed today; next reward tomorrow
                menu.button(slot, PiButton.create(
                        Material.LIME_STAINED_GLASS_PANE,
                        "&a✔ Day " + tier.day() + " - " + tier.title(),
                        "&7Reward: &a$" + tier.coins() + " &7| &b" + tier.exp() + " EXP",
                        "",
                        "&aClaimed today! &7Come back tomorrow."
                ).onClick((p, e) -> PiSound.deny(p)));

            } else {
                // State 4: Locked Future Tier
                int daysUntil = dayIndex - currentStreak;
                List<String> lockLore = new ArrayList<>();
                lockLore.add("&7Reward: &8$" + tier.coins() + " | " + tier.exp() + " EXP");
                if (tier.bonusText() != null) {
                    lockLore.add("&8" + ChatColor.stripColor(tier.bonusText()));
                }
                lockLore.add("");
                lockLore.add("&c🔒 Unlocks in " + daysUntil + " day" + (daysUntil > 1 ? "s" : "") + "!");

                menu.button(slot, PiButton.create(Material.GRAY_DYE, "&8🔒 Day " + tier.day() + " - " + tier.title(), lockLore.toArray(new String[0]))
                        .onClick((p, e) -> PiSound.deny(p)));
            }
        }

        // Slot 22: Next Reset Timer
        menu.button(22, PiButton.create(Material.CLOCK,
                "&e&lDaily Cycle Status",
                "&7Next reset at &f00:00 &7Server Time.",
                "&7Log in every day to keep your streak!"
        ));

        // Slot 31: Bottom Close Button
        menu.button(31, PiButton.create(Material.BARRIER, "&c&lClose")
                .onClick((p, e) -> {
                    PiSound.click(p);
                    p.closeInventory();
                }));

        menu.open(player);
    }
}

