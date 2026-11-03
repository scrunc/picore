package org.illustro.picore.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent ItemStack builder for clean and rapid menu design in PiCore.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item);
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            List<String> colored = new ArrayList<>();
            for (String line : lines) {
                colored.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(colored);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}

