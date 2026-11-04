package org.illustro.picore.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.illustro.picore.PiCore;
import org.illustro.picore.utils.PiText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Declarative button representing an interactive slot in a PiMenu.
 * Supports left, right, shift-left, shift-right, middle, and drop clicks,
 * inline 2-step confirmation, steppers, toggles, cyclers, and MiniMessage.
 */
public class PiButton {

    private final ItemStack icon;
    private BiConsumer<Player, InventoryClickEvent> onClick;
    private BiConsumer<Player, InventoryClickEvent> onLeftClick;
    private BiConsumer<Player, InventoryClickEvent> onRightClick;
    private BiConsumer<Player, InventoryClickEvent> onShiftLeftClick;
    private BiConsumer<Player, InventoryClickEvent> onShiftRightClick;
    private BiConsumer<Player, InventoryClickEvent> onMiddleClick;
    private BiConsumer<Player, InventoryClickEvent> onDropClick;
    private String permission;
    private Sound sound;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    public PiButton(ItemStack icon) {
        this.icon = icon != null ? icon.clone() : new ItemStack(Material.AIR);
    }

    public static PiButton create(ItemStack icon) {
        return new PiButton(icon);
    }

    public static PiButton create(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(PiText.component(name));
            if (lore != null && lore.length > 0) {
                meta.lore(PiText.components(Arrays.asList(lore)));
            }
            item.setItemMeta(meta);
        }
        return new PiButton(item);
    }

    /**
     * Clones a real player/shop ItemStack as a menu button while preserving its original
     * Adventure Component name, lore, and metadata, and appends extra UI lore lines below.
     */
    public static PiButton fromTemplate(ItemStack template, String... extraLore) {
        ItemStack copy = template != null ? template.clone() : new ItemStack(Material.STONE);
        PiButton btn = new PiButton(copy);
        if (extraLore != null && extraLore.length > 0) {
            btn.addLore(extraLore);
        }
        return btn;
    }

    /**
     * Creates an inline two-step confirmation button (e.g. "Delete Shop").
     * First click arms the button in-place with the armed icon/text for timeoutSeconds;
     * clicking again before expiry executes onConfirmed.
     */
    public static PiButton confirm(
            Material normalMat,
            String normalTitle,
            List<String> normalLore,
            Material armedMat,
            String armedTitle,
            List<String> armedLore,
            int timeoutSeconds,
            BiConsumer<Player, InventoryClickEvent> onConfirmed
    ) {
        Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
        int windowSec = Math.max(1, timeoutSeconds);

        ItemStack baseIcon = create(
                normalMat,
                normalTitle,
                normalLore != null ? normalLore.toArray(new String[0]) : new String[0]
        ).getIcon();

        ItemStack armedIcon = create(
                armedMat != null ? armedMat : Material.BARRIER,
                armedTitle != null ? armedTitle : "&c&lCLICK AGAIN TO CONFIRM",
                armedLore != null ? armedLore.toArray(new String[0]) : new String[]{"&7Expires in " + windowSec + "s."}
        ).glow(true).getIcon();

        PiButton btn = new PiButton(baseIcon);
        btn.onClick((p, e) -> {
            long now = System.currentTimeMillis();
            Long expiry = armedUntil.get(p.getUniqueId());

            if (expiry != null && now <= expiry) {
                armedUntil.remove(p.getUniqueId());
                PiSound.success(p);
                if (e.getView().getTopInventory().getHolder() instanceof PiMenuHolder holder) {
                    holder.updateSlot(e.getSlot(), btn);
                }
                if (onConfirmed != null) {
                    onConfirmed.accept(p, e);
                }
                return;
            }

            long newExpiry = now + (windowSec * 1000L);
            armedUntil.put(p.getUniqueId(), newExpiry);
            PiSound.deny(p);

            int slot = e.getSlot();
            if (e.getView().getTopInventory().getHolder() instanceof PiMenuHolder holder) {
                PiButton armedBtn = new PiButton(armedIcon).onClick(btn.onClick);
                holder.updateSlot(slot, armedBtn);

                PiCore core = PiCore.getInstance();
                if (core != null && core.isEnabled()) {
                    Bukkit.getScheduler().runTaskLater(core, () -> {
                        Long currentExp = armedUntil.get(p.getUniqueId());
                        if (currentExp != null && currentExp == newExpiry) {
                            armedUntil.remove(p.getUniqueId());
                            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == holder) {
                                holder.updateSlot(slot, btn);
                            }
                        }
                    }, windowSec * 20L);
                }
            }
        });
        return btn;
    }

    public static PiButton confirm(
            Material normalMat,
            String normalTitle,
            Material armedMat,
            String armedTitle,
            int timeoutSeconds,
            BiConsumer<Player, InventoryClickEvent> onConfirmed
    ) {
        return confirm(normalMat, normalTitle, null, armedMat, armedTitle, null, timeoutSeconds, onConfirmed);
    }

    public static PiButton playerHead(java.util.UUID uuid, String playerName, String displayName, String... lore) {
        return PiSkull.button(uuid, playerName, displayName, lore);
    }

    public static PiButton playerHead(String uuidStr, String playerName, String displayName, String... lore) {
        return PiSkull.button(uuidStr, playerName, displayName, lore);
    }

    public static PiButton headFromTexture(String textureValue, String displayName, String... lore) {
        return PiSkull.buttonFromTexture(textureValue, displayName, lore);
    }

    public static PiButton stepper(
            Material mat,
            String title,
            int current,
            int min,
            int max,
            int step,
            int shiftStep,
            String unit,
            Consumer<Integer> onChange
    ) {
        String u = unit != null ? unit : "";
        List<String> lore = new ArrayList<>();
        lore.add("&7Current: &e" + current + u);
        lore.add("&8• &aLeft-Click: &7+" + step + u);
        lore.add("&8• &cRight-Click: &7-" + step + u);
        lore.add("&8• &eShift-Click: &7±" + shiftStep + u);

        return create(mat, title, lore.toArray(new String[0]))
                .onClick((p, e) -> {
                    int delta;
                    if (e.isShiftClick()) {
                        delta = e.isLeftClick() ? shiftStep : -shiftStep;
                    } else {
                        delta = e.isLeftClick() ? step : -step;
                    }
                    int newVal = Math.max(min, Math.min(max, current + delta));
                    if (newVal == current) {
                        PiSound.deny(p);
                    } else {
                        PiSound.step(p, delta > 0);
                        if (onChange != null) {
                            onChange.accept(newVal);
                        }
                    }
                });
    }

    public static PiButton stepper(
            Material mat,
            String title,
            int current,
            int min,
            int max,
            int step,
            int shiftStep,
            Consumer<Integer> onChange
    ) {
        return stepper(mat, title, current, min, max, step, shiftStep, "", onChange);
    }

    public static PiButton toggle(
            Material onMat,
            Material offMat,
            String title,
            boolean state,
            Consumer<Boolean> onToggle
    ) {
        Material mat = state ? onMat : offMat;
        String status = state ? "&aEnabled" : "&cDisabled";
        List<String> lore = new ArrayList<>();
        lore.add("&7Status: " + status);
        lore.add("&8• &eClick &7to toggle");

        return create(mat, title, lore.toArray(new String[0]))
                .onClick((p, e) -> {
                    PiSound.flip(p);
                    if (onToggle != null) {
                        onToggle.accept(!state);
                    }
                });
    }

    public static PiButton toggle(
            Material mat,
            String title,
            boolean state,
            Consumer<Boolean> onToggle
    ) {
        return toggle(mat, mat, title, state, onToggle);
    }

    public static <E> PiButton cycle(
            Material mat,
            String title,
            List<E> options,
            E current,
            Function<E, String> labelFunction,
            Consumer<E> onCycle
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Options:");
        if (options != null) {
            for (E opt : options) {
                String lbl = labelFunction != null ? labelFunction.apply(opt) : String.valueOf(opt);
                if (Objects.equals(opt, current)) {
                    lore.add(" &a▸ &f" + lbl);
                } else {
                    lore.add(" &8▪ &7" + lbl);
                }
            }
        }
        lore.add("");
        lore.add("&8• &eLeft-Click: &7Next option");
        lore.add("&8• &eRight-Click: &7Previous option");

        return create(mat, title, lore.toArray(new String[0]))
                .onClick((p, e) -> {
                    if (options == null || options.isEmpty()) return;
                    int idx = options.indexOf(current);
                    if (idx < 0) idx = 0;
                    int nextIdx;
                    if (e.isRightClick()) {
                        nextIdx = (idx - 1 + options.size()) % options.size();
                    } else {
                        nextIdx = (idx + 1) % options.size();
                    }
                    PiSound.flip(p);
                    if (onCycle != null) {
                        onCycle.accept(options.get(nextIdx));
                    }
                });
    }

    public static <E> PiButton cycle(
            Material mat,
            String title,
            E[] options,
            E current,
            Function<E, String> labelFunction,
            Consumer<E> onCycle
    ) {
        return cycle(mat, title, options != null ? Arrays.asList(options) : List.of(), current, labelFunction, onCycle);
    }

    public ItemStack getIcon() {
        return icon;
    }

    public PiButton name(String displayName) {
        if (displayName != null) {
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(PiText.component(displayName));
                icon.setItemMeta(meta);
            }
        }
        return this;
    }

    public PiButton name(Component displayName) {
        if (displayName != null) {
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(displayName);
                icon.setItemMeta(meta);
            }
        }
        return this;
    }

    public PiButton lore(String... lines) {
        return lore(lines != null ? Arrays.asList(lines) : List.of());
    }

    public PiButton lore(List<String> lines) {
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && lines != null) {
            meta.lore(PiText.components(lines));
            icon.setItemMeta(meta);
        }
        return this;
    }

    public PiButton addLore(String... extraLines) {
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && extraLines != null && extraLines.length > 0) {
            List<Component> list = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore())
                    : new ArrayList<>();
            for (String l : extraLines) {
                list.add(PiText.component(l));
            }
            meta.lore(list);
            icon.setItemMeta(meta);
        }
        return this;
    }

    public PiButton amount(int count) {
        icon.setAmount(Math.max(1, count));
        return this;
    }

    public PiButton model(int customModelData) {
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            icon.setItemMeta(meta);
        }
        return this;
    }

    public PiButton glow() {
        return glow(true);
    }

    public PiButton glow(boolean active) {
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (active) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                for (Enchantment enc : new ArrayList<>(meta.getEnchants().keySet())) {
                    meta.removeEnchant(enc);
                }
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            icon.setItemMeta(meta);
        }
        return this;
    }

    public PiButton onClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onClick = handler;
        return this;
    }

    public PiButton onClick(Consumer<Player> handler) {
        this.onClick = (p, e) -> {
            if (handler != null) handler.accept(p);
        };
        return this;
    }

    public PiButton onLeftClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onLeftClick = handler;
        return this;
    }

    public PiButton onLeftClick(Consumer<Player> handler) {
        this.onLeftClick = (p, e) -> {
            if (handler != null) handler.accept(p);
        };
        return this;
    }

    public PiButton onRightClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onRightClick = handler;
        return this;
    }

    public PiButton onRightClick(Consumer<Player> handler) {
        this.onRightClick = (p, e) -> {
            if (handler != null) handler.accept(p);
        };
        return this;
    }

    public PiButton onShiftLeftClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onShiftLeftClick = handler;
        return this;
    }

    public PiButton onShiftRightClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onShiftRightClick = handler;
        return this;
    }

    public PiButton onMiddleClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onMiddleClick = handler;
        return this;
    }

    public PiButton onDropClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onDropClick = handler;
        return this;
    }

    public PiButton permission(String permission) {
        this.permission = permission;
        return this;
    }

    public String getPermission() {
        return permission;
    }

    public PiButton sound(Sound sound) {
        this.sound = sound;
        return this;
    }

    public PiButton sound(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.soundVolume = volume;
        this.soundPitch = pitch;
        return this;
    }

    public void handleClick(Player p, InventoryClickEvent e) {
        if (permission != null && !p.hasPermission(permission)) {
            p.sendMessage(ChatColor.RED + "You lack permission to use this button.");
            return;
        }
        if (sound != null) {
            p.playSound(p.getLocation(), sound, soundVolume, soundPitch);
        }

        boolean specificHandled = false;
        ClickType click = e.getClick();

        if (click == ClickType.SHIFT_LEFT && onShiftLeftClick != null) {
            onShiftLeftClick.accept(p, e);
            specificHandled = true;
        } else if (click == ClickType.SHIFT_RIGHT && onShiftRightClick != null) {
            onShiftRightClick.accept(p, e);
            specificHandled = true;
        } else if ((click == ClickType.MIDDLE || click == ClickType.CREATIVE) && onMiddleClick != null) {
            onMiddleClick.accept(p, e);
            specificHandled = true;
        } else if ((click == ClickType.DROP || click == ClickType.CONTROL_DROP) && onDropClick != null) {
            onDropClick.accept(p, e);
            specificHandled = true;
        } else if (e.isLeftClick() && onLeftClick != null) {
            onLeftClick.accept(p, e);
            specificHandled = true;
        } else if (e.isRightClick() && onRightClick != null) {
            onRightClick.accept(p, e);
            specificHandled = true;
        }

        if (!specificHandled && onClick != null) {
            onClick.accept(p, e);
        }
    }
}
