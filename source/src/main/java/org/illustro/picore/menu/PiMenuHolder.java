package org.illustro.picore.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Type-safe custom InventoryHolder isolating PiCore menus.
 * Guarantees 100% backward compatibility by ensuring PiMenuGuard only
 * intercepts inventories created through PiCore.
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
}

