package org.illustro.picore.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.illustro.picore.menu.PiMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent ItemStack builder for clean and rapid menu design in PiCore.
 * Supports MiniMessage, Adventure Components, and legacy color codes.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material != null ? material : Material.STONE);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item != null ? item.clone() : new ItemStack(Material.STONE);
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
            meta.displayName(PiText.component(name));
        }
        return this;
    }

    public ItemBuilder name(Component name) {
        if (meta != null && name != null) {
            meta.displayName(name);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            meta.lore(PiText.components(lines));
        }
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        if (meta != null && lines != null) {
            meta.lore(new ArrayList<>(lines));
        }
        return this;
    }

    public ItemBuilder addLore(String... extraLines) {
        if (meta != null && extraLines != null && extraLines.length > 0) {
            List<Component> current = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore())
                    : new ArrayList<>();
            for (String line : extraLines) {
                current.add(PiText.component(line));
            }
            meta.lore(current);
        }
        return this;
    }

    public ItemBuilder wrapLore(String text, int maxLineLength) {
        if (meta != null && text != null) {
            meta.lore(PiText.components(PiText.wrap(text, maxLineLength)));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder model(int customModelData) {
        if (meta != null) {
            meta.setCustomModelData(customModelData);
        }
        return this;
    }

    public ItemBuilder glow() {
        return glow(true);
    }

    public ItemBuilder glow(boolean active) {
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
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) {
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

    /**
     * Builds the ItemStack and ensures any PiCore GUI anti-theft PDC tags are stripped.
     */
    public ItemStack buildClean() {
        ItemStack built = build();
        PiMenu.unstampPdc(built);
        return built;
    }
}
