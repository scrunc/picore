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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-Tier Defense-in-Depth anti-theft and exploit guard for PiCore.
 * Protects every menu across all connected plugins (PixelProfiler, Koth, MachineConstruct, etc.):
 *
 * Tier 1: Frontline EventPriority.LOWEST cancellation for PiMenuHolder inventories with event deduplication.
 * Tier 2: PersistentDataContainer (PDC) item stamping and HIGHEST cancellation backstop.
 * Tier 3: Inventory close/join sweeper catching packet lag or hacked-client item leaks.
 */
public class PiMenuGuard implements Listener {

    private static final NamespacedKey KEY_PICORE = new NamespacedKey("picore", "gui");
    private static final NamespacedKey KEY_LEGACY = new NamespacedKey("pixelprofiler", "gui");

    // Static event deduplication across multiple plugin registrations / listeners
    private static final Set<InventoryClickEvent> PROCESSED_CLICKS =
            Collections.newSetFromMap(new WeakHashMap<>());

    // Micro-debounce per player (50ms / 1 tick) to protect against mouse switch bounce / chatter
    private static final Map<UUID, Long> LAST_CLICK_MS = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof PiMenuHolder holder) {
            e.setCancelled(true);

            // Deduplicate: ensure an event instance is only handled once across any number of guard instances
            if (!PROCESSED_CLICKS.add(e)) {
                return;
            }

            // Debounce rapid packet spam or physical mouse switch chatter (<50ms / 1 tick)
            long now = System.currentTimeMillis();
            Long last = LAST_CLICK_MS.get(player.getUniqueId());
            if (last != null && now - last < 50L) {
                return;
            }
            LAST_CLICK_MS.put(player.getUniqueId(), now);

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        LAST_CLICK_MS.remove(e.getPlayer().getUniqueId());
    }

    private boolean isStamped(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_PICORE, PersistentDataType.BYTE) || pdc.has(KEY_LEGACY, PersistentDataType.BYTE);
    }

    public static void sweepPlayerInventory(Player player) {
        boolean cleaned = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta()) {
                var pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(KEY_PICORE, PersistentDataType.BYTE) || pdc.has(KEY_LEGACY, PersistentDataType.BYTE)) {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                    cleaned = true;
                }
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.hasItemMeta()) {
            var pdc = cursor.getItemMeta().getPersistentDataContainer();
            if (pdc.has(KEY_PICORE, PersistentDataType.BYTE) || pdc.has(KEY_LEGACY, PersistentDataType.BYTE)) {
                player.setItemOnCursor(new ItemStack(Material.AIR));
                cleaned = true;
            }
        }

        if (cleaned) {
            player.updateInventory();
        }
    }
}
