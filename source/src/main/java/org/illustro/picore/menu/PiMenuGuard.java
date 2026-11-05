package org.illustro.picore.menu;

import org.bukkit.Bukkit;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-Tier Defense-in-Depth anti-theft, deposit canvas, and exploit guard for PiCore.
 * Supports:
 * - GlitchSink quarantine hooks (routing intercepted menu items to ItemTracker instead of silent deletion)
 * - Deposit / canvas slots for items placement and retrieval (GiftSend, KitEditor, Orders, Bounty, ShopEditor)
 * - 200ms click debounce to absorb hardware mouse chatter and accidental double clicks
 */
public class PiMenuGuard implements Listener {

    public static final NamespacedKey KEY_PICORE = new NamespacedKey("picore", "gui");
    public static final NamespacedKey KEY_LEGACY = new NamespacedKey("pixelprofiler", "gui");

    /**
     * Sink where confiscated glitched menu items are routed instead of being silently deleted.
     * Implemented by ItemTracker (/it glitched) or security quarantine modules.
     */
    public interface GlitchSink {
        /**
         * Confiscate a stamped item swept from a player.
         * @param player The player holding the glitched item.
         * @param stamped The glitched ItemStack.
         * @param source The origin of the confiscation (e.g. "close", "join", "cursor").
         * @return A harmless replacement item (such as a paper receipt) to leave in the inventory,
         *         or null to clear the slot.
         */
        ItemStack onConfiscate(Player player, ItemStack stamped, String source);
    }

    private static volatile GlitchSink glitchSink = null;

    public static void setGlitchSink(GlitchSink sink) {
        glitchSink = sink;
    }

    public static GlitchSink getGlitchSink() {
        return glitchSink;
    }

    /** Remove the menu-decoration stamp (mutates + returns copy) — used before quarantining so reclaimed items won't re-sweep. */
    public static ItemStack untag(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        PiMenu.unstampPdc(copy);
        return copy;
    }

    /** True if this item carries the menu-decoration stamp. */
    public static boolean tagged(ItemStack item) {
        return isStamped(item);
    }

    public static boolean isStamped(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_PICORE, PersistentDataType.BYTE) || pdc.has(KEY_LEGACY, PersistentDataType.BYTE);
    }

    private static final Set<InventoryClickEvent> PROCESSED_CLICKS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<InventoryCloseEvent> PROCESSED_CLOSES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final long CLICK_DEBOUNCE_MS = 200L;
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

            // Top inventory clicks
            if (clicked != null && clicked.equals(top)) {
                int slot = e.getSlot();

                // Deposit slot handling
                if (menu.isDepositSlot(slot)) {
                    // Block placing stamped decorative items into deposit slots
                    ItemStack cursor = e.getCursor();
                    if (isStamped(cursor)) {
                        e.setCancelled(true);
                        sweepPlayerInventory(player, "deposit_cursor_stamped");
                        return;
                    }
                    e.setCancelled(false);
                    return;
                }

                // Decorative / action button slot: always cancel so items can never be taken
                e.setCancelled(true);

                long now = System.currentTimeMillis();
                Long last = LAST_CLICK_MS.get(player.getUniqueId());
                if (last != null && now - last < CLICK_DEBOUNCE_MS) {
                    return;
                }
                LAST_CLICK_MS.put(player.getUniqueId(), now);

                PiButton button = menu.getButton(slot);
                if (button != null) {
                    button.handleClick(player, e);
                }
                return;
            }

            // Bottom inventory clicks
            if (clicked != null && clicked.equals(e.getView().getBottomInventory())) {
                boolean allowBottom = menu.isBottomInventoryPickupAllowed() || menu.hasDepositSlots();
                if (allowBottom) {
                    if (e.isShiftClick() || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        if (!menu.hasDepositSlots()) {
                            e.setCancelled(true);
                        } else {
                            if (isStamped(e.getCurrentItem())) {
                                e.setCancelled(true);
                                sweepPlayerInventory(player, "bottom_shift_stamped");
                            }
                        }
                    }
                } else {
                    e.setCancelled(true);
                }

                if (menu.hasBottomClickHandler()) {
                    long now = System.currentTimeMillis();
                    Long last = LAST_CLICK_MS.get(player.getUniqueId());
                    if (last != null && now - last < CLICK_DEBOUNCE_MS) {
                        return;
                    }
                    LAST_CLICK_MS.put(player.getUniqueId(), now);
                    menu.handleBottomClick(player, e);
                }
                return;
            }

            // Clicking outside the window while holding a cursor item
            if (!menu.isBottomInventoryPickupAllowed() && !menu.hasDepositSlots()) {
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

            boolean touchesNonDepositTop = false;
            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot < topSize) {
                    if (!menu.isDepositSlot(rawSlot)) {
                        touchesNonDepositTop = true;
                        break;
                    }
                }
            }

            boolean allowBottom = menu.isBottomInventoryPickupAllowed() || menu.hasDepositSlots();
            if (touchesNonDepositTop || (!allowBottom && e.getRawSlots().stream().anyMatch(rawSlot -> rawSlot >= topSize))) {
                e.setCancelled(true);
            }

            // Block dragging stamped items into deposit slots
            if (isStamped(e.getOldCursor())) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player player) {
                    sweepPlayerInventory(player, "drag_stamped");
                }
            }

            if (menu.hasDragHandler() && e.getWhoClicked() instanceof Player player) {
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
                sweepPlayerInventory(p, "backstop_pdc");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent e) {
        if (!PROCESSED_CLOSES.add(e)) return;
        if (e.getInventory().getHolder() instanceof PiMenuHolder holder) {
            if (e.getPlayer() instanceof Player player) {
                PiMenu menu = holder.getMenu();
                boolean suppressed = SUPPRESSED_CLOSES.remove(player.getUniqueId());
                if (!suppressed) {
                    menu.handleClose(player);
                }

                // Handle deposit slots save and return on close
                if (menu.hasDepositSlots()) {
                    Inventory inv = e.getInventory();
                    Map<Integer, ItemStack> deposits = new HashMap<>();
                    for (int slot : menu.getDepositSlots()) {
                        ItemStack item = inv.getItem(slot);
                        if (item != null && item.getType() != Material.AIR && !isStamped(item)) {
                            deposits.put(slot, item);
                        }
                    }

                    if (menu.hasDepositSaveHandler()) {
                        menu.handleDepositSave(player, deposits);
                    }

                    if (menu.isReturnDepositsOnClose()) {
                        for (Map.Entry<Integer, ItemStack> entry : deposits.entrySet()) {
                            inv.setItem(entry.getKey(), new ItemStack(Material.AIR));
                            var leftovers = player.getInventory().addItem(entry.getValue());
                            for (ItemStack leftover : leftovers.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                            }
                        }
                    }
                }

                sweepPlayerInventory(player, "menu_close");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        sweepPlayerInventory(e.getPlayer(), "player_join");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        LAST_CLICK_MS.remove(id);
        SUPPRESSED_CLOSES.remove(id);
    }

    public static void sweepPlayerInventory(Player player) {
        sweepPlayerInventory(player, "sweep");
    }

    public static void sweepPlayerInventory(Player player, String source) {
        if (player == null) return;
        boolean cleaned = false;
        GlitchSink sink = glitchSink;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isStamped(item)) {
                ItemStack replacement = (sink != null) ? safeConfiscate(sink, player, item, source) : null;
                player.getInventory().setItem(i, replacement != null ? replacement : new ItemStack(Material.AIR));
                cleaned = true;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isStamped(cursor)) {
            if (sink != null) {
                safeConfiscate(sink, player, cursor, (source != null ? source : "sweep") + ":cursor");
            }
            player.setItemOnCursor(new ItemStack(Material.AIR));
            cleaned = true;
        }
        if (cleaned) {
            player.updateInventory();
        }
    }

    private static ItemStack safeConfiscate(GlitchSink sink, Player player, ItemStack stamped, String source) {
        try {
            return sink.onConfiscate(player, stamped, source);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[PiCore] GlitchSink error: " + t.getMessage());
            return null;
        }
    }
}
