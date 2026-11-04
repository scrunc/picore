package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Standardized 3-row (27-slot) confirmation modal dialog.
 * Presents a clear Confirm (Green) vs Cancel (Red) choice around a central target/subject item.
 * Automatically handles click sound feedback, window closing, and PDC anti-theft protection.
 */
public class PiConfirmMenu {

    private final String title;
    private ItemStack targetItem;
    private String confirmText = "&a&lCONFIRM";
    private String cancelText = "&c&lCANCEL";
    private List<String> confirmLore = List.of("&7Click to proceed with this action.");
    private List<String> cancelLore = List.of("&7Click to abort and return.");
    private Material confirmMat = Material.LIME_CONCRETE;
    private Material cancelMat = Material.RED_CONCRETE;
    private Material borderMat = Material.GRAY_STAINED_GLASS_PANE;

    private Consumer<Player> onConfirm;
    private Consumer<Player> onCancel;

    public PiConfirmMenu(String title) {
        this.title = title != null ? title : "&4&lConfirm Action";
    }

    public static PiConfirmMenu create(String title) {
        return new PiConfirmMenu(title);
    }

    public PiConfirmMenu target(ItemStack item) {
        this.targetItem = item;
        return this;
    }

    public PiConfirmMenu target(Material mat, String name, String... lore) {
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
        this.targetItem = item;
        return this;
    }

    public PiConfirmMenu confirmText(String text, String... lore) {
        this.confirmText = text;
        if (lore != null && lore.length > 0) {
            List<String> list = new ArrayList<>();
            for (String l : lore) list.add(ChatColor.translateAlternateColorCodes('&', l));
            this.confirmLore = list;
        }
        return this;
    }

    public PiConfirmMenu cancelText(String text, String... lore) {
        this.cancelText = text;
        if (lore != null && lore.length > 0) {
            List<String> list = new ArrayList<>();
            for (String l : lore) list.add(ChatColor.translateAlternateColorCodes('&', l));
            this.cancelLore = list;
        }
        return this;
    }

    public PiConfirmMenu onConfirm(Consumer<Player> handler) {
        this.onConfirm = handler;
        return this;
    }

    public PiConfirmMenu onCancel(Consumer<Player> handler) {
        this.onCancel = handler;
        return this;
    }

    public PiConfirmMenu materials(Material confirm, Material cancel, Material border) {
        if (confirm != null) this.confirmMat = confirm;
        if (cancel != null) this.cancelMat = cancel;
        if (border != null) this.borderMat = border;
        return this;
    }

    public void open(Player player) {
        PiMenu menu = PiMenu.create(title, 3);
        menu.frame(borderMat);

        // Center target item (slot 13)
        if (targetItem != null) {
            menu.button(13, PiButton.create(targetItem));
        }

        // Confirm button (slot 11)
        menu.button(11, PiButton.create(confirmMat, confirmText, confirmLore.toArray(new String[0]))
                .onClick((p, e) -> {
                    PiSound.success(p);
                    p.closeInventory();
                    if (onConfirm != null) onConfirm.accept(p);
                }));

        // Cancel button (slot 15)
        menu.button(15, PiButton.create(cancelMat, cancelText, cancelLore.toArray(new String[0]))
                .onClick((p, e) -> {
                    PiSound.deny(p);
                    p.closeInventory();
                    if (onCancel != null) onCancel.accept(p);
                }));

        menu.open(player);
    }
}

