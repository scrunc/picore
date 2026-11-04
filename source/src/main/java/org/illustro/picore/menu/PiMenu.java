package org.illustro.picore.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for creating interactive inventory menus in PiCore.
 * Automatically marks buttons with PDC anti-theft stamps and handles slot
 * mapping.
 */
public class PiMenu {

    public static final NamespacedKey GUI_KEY = new NamespacedKey("picore", "gui");

    private final String title;
    private final int rows;
    private final Map<Integer, PiButton> buttons = new HashMap<>();
    private Consumer<Player> onClose;

    public PiMenu(String title, int rows) {
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.rows = Math.max(1, Math.min(6, rows));
    }

    public static PiMenu create(String title, int rows) {
        return new PiMenu(title, rows);
    }

    public PiMenu button(int slot, PiButton button) {
        if (slot >= 0 && slot < rows * 9) {
            buttons.put(slot, button);
        }
        return this;
    }

    public PiMenu fillBorder(ItemStack borderItem) {
        PiButton b = PiButton.create(borderItem);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                    int slot = r * 9 + c;
                    if (!buttons.containsKey(slot)) {
                        buttons.put(slot, b);
                    }
                }
            }
        }
        return this;
    }

    public PiMenu frame(Material glassPane) {
        return fillBorder(pane(glassPane != null ? glassPane : Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    public PiMenu fill(int[] slots, PiButton button) {
        if (slots != null && button != null) {
            for (int s : slots) {
                if (s >= 0 && s < rows * 9 && !buttons.containsKey(s)) {
                    buttons.put(s, button);
                }
            }
        }
        return this;
    }

    public PiMenu fill(int[] slots, ItemStack item) {
        return fill(slots, PiButton.create(item));
    }

    public PiMenu fill(ItemStack fillItem) {
        PiButton b = PiButton.create(fillItem);
        for (int i = 0; i < rows * 9; i++) {
            if (!buttons.containsKey(i)) {
                buttons.put(i, b);
            }
        }
        return this;
    }

    public PiMenu onClose(Consumer<Player> handler) {
        this.onClose = handler;
        return this;
    }

    public void handleClose(Player player) {
        if (onClose != null)
            onClose.accept(player);
    }

    public PiButton getButton(int slot) {
        return buttons.get(slot);
    }

    public int getSize() {
        return rows * 9;
    }

    public Inventory build() {
        PiMenuHolder holder = new PiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        for (Map.Entry<Integer, PiButton> entry : buttons.entrySet()) {
            ItemStack icon = entry.getValue().getIcon().clone();
            stampPdc(icon);
            inv.setItem(entry.getKey(), icon);
        }

        return inv;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public static void stampPdc(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(GUI_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    public static boolean hasPdcStamp(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(GUI_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
