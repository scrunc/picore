package org.illustro.picore.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.illustro.picore.utils.PiText;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fluent builder for creating interactive inventory menus in PiCore.
 * Automatically marks buttons with PDC anti-theft stamps and handles slot
 * mapping. Supports live in-place slot updating, whole-menu refreshes,
 * sub-menu transitions, bottom inventory pickup, and drag hooks.
 */
public class PiMenu {

    public static final NamespacedKey GUI_KEY = new NamespacedKey("picore", "gui");
    public static final NamespacedKey LEGACY_GUI_KEY = new NamespacedKey("pixelprofiler", "gui");

    private final Component title;
    private final int rows;
    private final Map<Integer, PiButton> buttons = new HashMap<>();
    private Consumer<Player> onClose;
    private BiConsumer<Player, InventoryClickEvent> onBottomClick;
    private BiConsumer<Player, InventoryDragEvent> onDrag;
    private boolean allowBottomInventoryPickup = false;
    private final java.util.Set<Integer> depositSlots = new java.util.HashSet<>();
    private java.util.function.BiConsumer<Player, java.util.Map<Integer, ItemStack>> onDepositSave;
    private boolean returnDepositsOnClose = true;

    public PiMenu(String title, int rows) {
        this.title = PiText.component(title != null ? title : "");
        this.rows = Math.max(1, Math.min(6, rows));
    }

    public PiMenu(Component title, int rows) {
        this.title = title != null ? title : Component.empty();
        this.rows = Math.max(1, Math.min(6, rows));
    }

    public static PiMenu create(String title, int rows) {
        return new PiMenu(title, rows);
    }

    public static PiMenu create(Component title, int rows) {
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
        if (onClose != null) {
            onClose.accept(player);
        }
    }

    public PiMenu onBottomClick(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onBottomClick = handler;
        return this;
    }

    public boolean hasBottomClickHandler() {
        return onBottomClick != null;
    }

    public void handleBottomClick(Player player, InventoryClickEvent event) {
        if (onBottomClick != null) {
            onBottomClick.accept(player, event);
        }
    }

    /**
     * When enabled, players can pick up and rearrange their own items in their bottom inventory
     * (e.g., to put an item on their cursor for trade template slots in LivingNPC), while shift-clicking
     * and double-click raking into the top GUI remain blocked.
     */
    public PiMenu allowBottomInventoryPickup(boolean allow) {
        this.allowBottomInventoryPickup = allow;
        return this;
    }

    public boolean isBottomInventoryPickupAllowed() {
        return allowBottomInventoryPickup;
    }

    /**
     * Registers a drag handler for when a player drags an item across top inventory slots.
     */
    public PiMenu onDrag(BiConsumer<Player, InventoryDragEvent> handler) {
        this.onDrag = handler;
        return this;
    }

    public boolean hasDragHandler() {
        return onDrag != null;
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        if (onDrag != null) {
            onDrag.accept(player, event);
        }
    }

    public PiButton getButton(int slot) {
        return buttons.get(slot);
    }

    public int getSize() {
        return rows * 9;
    }

    /**
     * Designates specific slots as deposit / canvas slots where players can place and take real items
     * (e.g. GiftSendGUI, KitEditor, OrdersGUI, BountyGUI, ShopEditor).
     */
    public PiMenu allowDepositSlots(int... slots) {
        if (slots != null) {
            for (int s : slots) {
                if (s >= 0 && s < rows * 9) {
                    depositSlots.add(s);
                }
            }
        }
        return this;
    }

    public PiMenu allowDepositSlots(java.util.Collection<Integer> slots) {
        if (slots != null) {
            for (int s : slots) {
                if (s >= 0 && s < rows * 9) {
                    depositSlots.add(s);
                }
            }
        }
        return this;
    }

    public boolean isDepositSlot(int slot) {
        return depositSlots.contains(slot);
    }

    public java.util.Set<Integer> getDepositSlots() {
        return java.util.Collections.unmodifiableSet(depositSlots);
    }

    public boolean hasDepositSlots() {
        return !depositSlots.isEmpty();
    }

    /**
     * Handler invoked when the menu is closed, passing all non-empty real items currently sitting in deposit slots.
     */
    public PiMenu onDepositSave(java.util.function.BiConsumer<Player, java.util.Map<Integer, ItemStack>> handler) {
        this.onDepositSave = handler;
        return this;
    }

    public boolean hasDepositSaveHandler() {
        return onDepositSave != null;
    }

    public void handleDepositSave(Player player, java.util.Map<Integer, ItemStack> deposits) {
        if (onDepositSave != null) {
            onDepositSave.accept(player, deposits);
        }
    }

    /**
     * Whether items remaining in deposit slots when the menu closes should be returned to the player's inventory
     * (or dropped at feet if inventory is full). Default is true.
     */
    public PiMenu returnDepositsOnClose(boolean returnOnClose) {
        this.returnDepositsOnClose = returnOnClose;
        return this;
    }

    public boolean isReturnDepositsOnClose() {
        return returnDepositsOnClose;
    }

    public Inventory build() {
        PiMenuHolder holder = new PiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        for (Map.Entry<Integer, PiButton> entry : buttons.entrySet()) {
            int slot = entry.getKey();
            ItemStack icon = entry.getValue().getIcon().clone();
            if (!isDepositSlot(slot)) {
                stampPdc(icon);
            }
            inv.setItem(slot, icon);
        }
        return inv;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public void openSubMenu(Player player, PiMenu subMenu) {
        if (player == null || subMenu == null) return;
        PiMenuGuard.suppressNextClose(player);
        subMenu.open(player);
    }

    public void updateSlot(Player player, int slot, PiButton button) {
        if (slot < 0 || slot >= rows * 9) return;
        buttons.put(slot, button);
        if (player != null && player.isOnline() && player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PiMenuHolder h && h.getMenu() == this) {
                h.updateSlot(slot, button);
            }
        }
    }

    public void refresh(Player player) {
        if (player != null && player.isOnline() && player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PiMenuHolder h && h.getMenu() == this) {
                h.refresh();
            }
        }
    }

    public static void stampPdc(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(GUI_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    public static void unstampPdc(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            var pdc = meta.getPersistentDataContainer();
            boolean changed = false;
            if (pdc.has(GUI_KEY, PersistentDataType.BYTE)) {
                pdc.remove(GUI_KEY);
                changed = true;
            }
            if (pdc.has(LEGACY_GUI_KEY, PersistentDataType.BYTE)) {
                pdc.remove(LEGACY_GUI_KEY);
                changed = true;
            }
            if (changed) {
                item.setItemMeta(meta);
            }
        }
    }

    public static ItemStack cloneClean(ItemStack item) {
        if (item == null) return new ItemStack(Material.AIR);
        ItemStack copy = item.clone();
        unstampPdc(copy);
        return copy;
    }

    public static boolean hasPdcStamp(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(GUI_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(PiText.component(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
