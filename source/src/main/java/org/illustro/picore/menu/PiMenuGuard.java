package org.illustro.picore.menu;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
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
 */
public class PiMenuGuard implements Listener {

    private static final NamespacedKey KEY_PICORE = new NamespacedKey("picore", "gui");
    private static final NamespacedKey KEY_LEGACY = new NamespacedKey("pixelprofiler", "gui");

    private static final Set<InventoryClickEvent> PROCESSED_CLICKS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<InventoryCloseEvent> PROCESSED_CLOSES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<UUID, Long> LAST_CLICK_MS = new ConcurrentHashMap<>();
    private static final Set<UUID> SUPPRESSED_CLOSES = ConcurrentHashMap.newKeySet();

    public static void suppressNextClose(Player player) {
        if (player != null) {
            SUPPRESSED_CLOSES.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof PiMenuHolder holder) {
            PiMenu menu = holder.getMenu();
            Inventory clicked = e.getClickedInventory();

            if (!PROCESSED_CLICKS.add(e)) {
                return;
            }

            // Always block double-click raking (COLLECT_TO_CURSOR) which pulls matching items from the top GUI
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }

            // Top inventory clicks are always cancelled so GUI items can never be taken or overwritten directly
            if (clicked != null && clicked.equals(top)) {
                e.setCancelled(true);

                long now = System.currentTimeMillis();
                Long last = LAST_CLICK_MS.get(player.getUniqueId());
                if (last != null && now - last < 50L) {
                    return;
                }
                LAST_CLICK_MS.put(player.getUniqueId(), now);

                int slot = e.getSlot();
                PiButton button = menu.getButton(slot);
                if (button != null) {
                    button.handleClick(player, e);
                }
                return;
            }

            // Bottom inventory clicks
            if (clicked != null && clicked.equals(e.getView().getBottomInventory())) {
                if (menu.isBottomInventoryPickupAllowed()) {
                    // Block shift-clicking items from bottom inventory into the top GUI
                    if (e.isShiftClick() || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        e.setCancelled(true);
                    }
                } else {
                    e.setCancelled(true);
                }

                if (menu.hasBottomClickHandler()) {
                    long now = System.currentTimeMillis();
                    Long last = LAST_CLICK_MS.get(player.getUniqueId());
                    if (last != null && now - last < 50L) {
                        return;
                    }
                    LAST_CLICK_MS.put(player.getUniqueId(), now);
                    menu.handleBottomClick(player, e);
                }
                return;
            }

            // Clicking outside the window while holding a cursor item
            if (!menu.isBottomInventoryPickupAllowed()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMenuDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof PiMenuHolder holder) {
            PiMenu menu = holder.getMenu();
            int topSize = top.getSize();
            boolean touchesTop = e.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < topSize);

            if (touchesTop || !menu.isBottomInventoryPickupAllowed()) {
                e.setCancelled(true);
            }

            if (touchesTop && menu.hasDragHandler() && e.getWhoClicked() instanceof Player player) {
                menu.handleDrag(player, e);
            }
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
        if (!PROCESSED_CLOSES.add(e)) return;
        if (e.getInventory().getHolder() instanceof PiMenuHolder holder) {
            if (e.getPlayer() instanceof Player player) {
                boolean suppressed = SUPPRESSED_CLOSES.remove(player.getUniqueId());
                if (!suppressed) {
                    holder.getMenu().handleClose(player);
                }
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
        UUID id = e.getPlayer().getUniqueId();
        LAST_CLICK_MS.remove(id);
        SUPPRESSED_CLOSES.remove(id);
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
