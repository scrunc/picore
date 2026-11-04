package org.illustro.picore.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal inventory slot layouts and grid math utilities for PiCore menus.
 * Replaces repetitive hardcoded slot arrays and border loops across all BMS plugins.
 */
public final class PiGrid {

    private PiGrid() { }

    /** Inner 28 content slots for 6-row framed menus (rows 1-4, cols 1-7). */
    public static final int[] CHEST_28 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Inner 21 content slots for 5-row framed menus (rows 1-3, cols 1-7). */
    public static final int[] CHEST_21 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /** Inner 14 content slots for 4-row framed menus (rows 1-2, cols 1-7). */
    public static final int[] CHEST_14 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    /** The 26 perimeter border slots of a 54-slot chest. */
    public static final int[] BORDER_54 = calculateBorderSlots(6);

    /** The 24 perimeter border slots of a 45-slot chest. */
    public static final int[] BORDER_45 = calculateBorderSlots(5);

    /** The 22 perimeter border slots of a 36-slot chest. */
    public static final int[] BORDER_36 = calculateBorderSlots(4);

    /**
     * Checks if a slot falls on the outer perimeter border of a chest with the given row count.
     */
    public static boolean isBorder(int slot, int rows) {
        if (slot < 0 || slot >= rows * 9) return false;
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    /**
     * Checks if a slot falls inside the framed content area (columns 1..7 and rows 1..rows-2).
     */
    public static boolean isInside(int slot, int rows) {
        if (slot < 0 || slot >= rows * 9) return false;
        int row = slot / 9;
        int col = slot % 9;
        return row >= 1 && row < rows - 1 && col >= 1 && col <= 7;
    }

    /**
     * Generates slot coordinates centered horizontally in an interior row (columns 1–7).
     * <p>
     * Up to 4 items get clean gaps between them:
     * <ul>
     *   <li>1 item  → column 4</li>
     *   <li>2 items → columns 3, 5</li>
     *   <li>3 items → columns 2, 4, 6</li>
     *   <li>4 items → columns 1, 3, 5, 7</li>
     * </ul>
     * 5 or more items sit side-by-side centered.
     *
     * @param row   0-indexed row (0 to rows-1)
     * @param count number of items to center (1 to 7)
     * @return array of slot numbers
     */
    public static int[] centered(int row, int count) {
        int n = Math.max(0, Math.min(7, count));
        int[] out = new int[n];
        if (n <= 4) {
            for (int i = 0; i < n; i++) {
                out[i] = row * 9 + (4 - (n - 1) + 2 * i);
            }
        } else {
            int startCol = 1 + (7 - n) / 2;
            for (int i = 0; i < n; i++) {
                out[i] = row * 9 + startCol + i;
            }
        }
        return out;
    }

    /**
     * Fills the perimeter border of a PiMenu with a glass pane.
     */
    public static void frame(PiMenu menu, Material glassPane) {
        frame(menu, PiMenu.pane(glassPane, " "));
    }

    /**
     * Fills the perimeter border of a PiMenu with an arbitrary item.
     */
    public static void frame(PiMenu menu, ItemStack borderItem) {
        PiButton btn = PiButton.create(borderItem);
        int rows = menu.getSize() / 9;
        for (int slot = 0; slot < menu.getSize(); slot++) {
            if (isBorder(slot, rows) && menu.getButton(slot) == null) {
                menu.button(slot, btn);
            }
        }
    }

    /**
     * Fills specific slots with a PiButton.
     */
    public static void fill(PiMenu menu, int[] slots, PiButton button) {
        if (slots == null || button == null) return;
        for (int slot : slots) {
            if (menu.getButton(slot) == null) {
                menu.button(slot, button);
            }
        }
    }

    /**
     * Fills specific slots with an ItemStack.
     */
    public static void fill(PiMenu menu, int[] slots, ItemStack item) {
        fill(menu, slots, PiButton.create(item));
    }

    private static int[] calculateBorderSlots(int rows) {
        List<Integer> list = new ArrayList<>();
        for (int s = 0; s < rows * 9; s++) {
            if (isBorder(s, rows)) {
                list.add(s);
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
