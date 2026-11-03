package org.illustro.picore.menu;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 3-Tier Defense-in-Depth anti-theft and exploit guard for PiCore.
 * Protects every menu across all connected plugins (PixelProfiler, Koth, MachineConstruct, etc.):
 *
 * Tier 1: Frontline EventPriority.LOWEST cancellation for PiMenuHolder inventories.
 * Tier 2: PersistentDataContainer (PDC) item stamping and HIGHEST cancellation backstop.
 * Tier 3: Inventory close/join sweeper catching packet lag or hacked-client item leaks.
 */
public class PiMenuGuard implements Listener {

    private static final NamespacedKey KEY_PICORE = new NamespacedKey("picore", "gui");
    private static final NamespacedKey KEY_LEGACY = new NamespacedKey("pixelprofiler", "gui");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof PiMenuHolder holder) {
            e.setCancelled(true);

            Inventory clicked = e.getClickedInventory();
            if (clicked != null && clicked.equals(top)) {
                int slot = e.getSlot();
                PiButton button = holder.getMenu().getButton(slot);
                if (button != null) {
                    button.handleClick(player, e);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof PiMenuHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBackstopPdcCheck(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean hotbarSuspicious = false;

        if (e.getWhoClicked() instanceof Player p) {
            if (e.getHotbarButton() >= 0 && e.getHotbarButton() < 9) {
                ItemStack hotbarItem = p.getInventory().getItem(e.getHotbarButton());
                if (isStamped(hotbarItem)) hotbarSuspicious = true;
            }
            if (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
                ItemStack offhand = p.getInventory().getItemInOffHand();
                if (isStamped(offhand)) hotbarSuspicious = true;
            }
        }

        if (isStamped(current) || isStamped(cursor) || hotbarSuspicious) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                sweepPlayerInventory(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof PiMenuHolder holder) {
            if (e.getPlayer() instanceof Player player) {
                holder.getMenu().handleClose(player);
                sweepPlayerInventory(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        sweepPlayerInventory(e.getPlayer());
    }

    public static boolean isStamped(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(KEY_PICORE, PersistentDataType.BYTE)
                || meta.getPersistentDataContainer().has(KEY_LEGACY, PersistentDataType.BYTE);
    }

    public static void sweepPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        Inventory inv = player.getInventory();
        boolean swept = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isStamped(item)) {
                inv.setItem(i, null);
                swept = true;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (isStamped(cursor)) {
            player.setItemOnCursor(null);
            swept = true;
        }

        if (swept) {
            player.updateInventory();
        }
    }
}

