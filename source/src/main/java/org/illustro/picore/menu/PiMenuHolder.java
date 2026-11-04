package org.illustro.picore.menu;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Type-safe custom InventoryHolder isolating PiCore menus.
 * Guarantees 100% backward compatibility by ensuring PiMenuGuard only
 * intercepts inventories created through PiCore.
 * Supports in-place slot updating and full menu refresh without closing the window.
 */
public class PiMenuHolder implements InventoryHolder {

    private final PiMenu menu;
    private Inventory inventory;

    public PiMenuHolder(PiMenu menu) {
        this.menu = menu;
    }

    public PiMenu getMenu() {
        return menu;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Updates an individual slot in-place on this open inventory.
     */
    public void updateSlot(int slot, PiButton button) {
        if (inventory != null && menu != null) {
            menu.button(slot, button);
            ItemStack icon = button != null && button.getIcon() != null
                    ? button.getIcon().clone()
                    : new ItemStack(Material.AIR);
            PiMenu.stampPdc(icon);
            inventory.setItem(slot, icon);
        }
    }

    /**
     * Re-renders all buttons in-place across this entire open inventory.
     */
    public void refresh() {
        if (inventory != null && menu != null) {
            for (int i = 0; i < menu.getSize(); i++) {
                PiButton b = menu.getButton(i);
                ItemStack icon = b != null && b.getIcon() != null
                        ? b.getIcon().clone()
                        : new ItemStack(Material.AIR);
                PiMenu.stampPdc(icon);
                inventory.setItem(i, icon);
            }
        }
    }
}
