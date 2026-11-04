package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Declarative button representing an interactive slot in a PiMenu.
 * Supports left, right, and shift clicks, sound feedback, permission checks, steppers, toggles, and cyclers.
 */
public class PiButton {

    private final ItemStack icon;
    private BiConsumer<Player, InventoryClickEvent> onClick;
    private BiConsumer<Player, InventoryClickEvent> onLeftClick;
    private BiConsumer<Player, InventoryClickEvent> onRightClick;
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
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : lore) list.add(ChatColor.translateAlternateColorCodes('&', l));
                meta.setLore(list);
            }
            item.setItemMeta(meta);
        }
        return new PiButton(item);
    }

    public static PiButton playerHead(java.util.UUID uuid, String playerName, String displayName, String... lore) {
        return PiSkull.button(uuid, playerName, displayName, lore);
    }

    public static PiButton playerHead(String uuidStr, String playerName, String displayName, String... lore) {
        return PiSkull.button(uuidStr, playerName, displayName, lore);
    }

    /**
     * Creates an interactive stepper button for integer adjustments (e.g. timers, chances, multipliers).
     * Handles Left-Click (+step), Right-Click (-step), Shift-Left (+shiftStep), Shift-Right (-shiftStep).
     */
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
        lore.add("&8» &aLeft-Click: &7+" + step + u);
        lore.add("&8» &cRight-Click: &7-" + step + u);
        lore.add("&8» &eShift-Click: &7±" + shiftStep + u);

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
                        PiSound.step(p);
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

    /**
     * Creates an interactive toggle button with distinct on/off materials and automated status lore.
     */
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
        lore.add("&8» &eClick &7to toggle");

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

    /**
     * Creates an option cycling button (e.g. sorting filters, modes, game rules).
     * Left-Click cycles forward, Right-Click cycles backward.
     */
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
                    lore.add(" &a● &f" + lbl);
                } else {
                    lore.add(" &8○ &7" + lbl);
                }
            }
        }
        lore.add("");
        lore.add("&8» &eLeft-Click: &7Next option");
        lore.add("&8» &eRight-Click: &7Previous option");

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

    public PiButton onClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onClick = handler;
        return this;
    }

    public PiButton onLeftClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onLeftClick = handler;
        return this;
    }

    public PiButton onRightClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onRightClick = handler;
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

        if (e.isLeftClick() && onLeftClick != null) {
            onLeftClick.accept(p, e);
        } else if (e.isRightClick() && onRightClick != null) {
            onRightClick.accept(p, e);
        }

        if (onClick != null) {
            onClick.accept(p, e);
        }
    }
}
