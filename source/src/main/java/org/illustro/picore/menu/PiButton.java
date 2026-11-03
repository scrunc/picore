package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Declarative button representing an interactive slot in a PiMenu.
 * Supports left, right, and shift clicks, sound feedback, permission checks, and custom lore builders.
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

