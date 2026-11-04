package org.illustro.picore.menu;

/**
 * Standard inventory geometry and navigation layout presets across the PiCore ecosystem.
 * Defines content boundaries, perimeter borders, and semantic toolbar/navigation slots.
 */
public enum PiLayout {

    /**
     * Classic 6-row (54-slot) framed chest.
     * Content: 28 inner slots (rows 1-4, cols 1-7).
     * Border: 26 outer glass perimeter slots.
     * Navigation on bottom row: Prev (45), Back/Indicator (49), Next (53).
     * Header toolbar: Center slot 4.
     */
    FRAMED_6ROW(
            6,
            new int[]{
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            },
            45, 53, 49, 4,
            true
    ),

    /**
     * 5-row (45-slot) framed chest.
     * Content: 21 inner slots (rows 1-3, cols 1-7).
     * Border: 24 outer glass perimeter slots.
     * Navigation on bottom row: Prev (36), Back/Indicator (40), Next (44).
     * Header toolbar: Center slot 4.
     */
    FRAMED_5ROW(
            5,
            new int[]{
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34
            },
            36, 44, 40, 4,
            true
    ),

    /**
     * 4-row (36-slot) framed chest.
     * Content: 14 inner slots (rows 1-2, cols 1-7).
     * Border: 22 outer glass perimeter slots.
     * Navigation on bottom row: Prev (27), Back/Indicator (31), Next (35).
     * Header toolbar: Center slot 4.
     */
    FRAMED_4ROW(
            4,
            new int[]{
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25
            },
            27, 35, 31, 4,
            true
    ),

    /**
     * 5-row (45-slot) unframed view.
     * Content: 36 slots (rows 0-3, slots 0..35).
     * Border: Bottom navigation bar only (row 4, slots 36..44).
     * Navigation: Prev (36), Back/Indicator (40), Next (44).
     */
    UNFRAMED_5ROW(
            5,
            new int[]{
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35
            },
            36, 44, 40, -1,
            false
    ),

    /**
     * 6-row (54-slot) unframed view.
     * Content: 45 slots (rows 0-4, slots 0..44).
     * Border: Bottom navigation bar only (row 5, slots 45..53).
     * Navigation: Prev (45), Back/Indicator (49), Next (53).
     */
    UNFRAMED_6ROW(
            6,
            new int[]{
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44
            },
            45, 53, 49, -1,
            false
    ),

    /**
     * 6-row (54-slot) vertical side-tabbed layout (used for cosmetics, hub tabs, multi-category galleries).
     * Tabs: 4 vertical tabs in column 0 (slots 9, 18, 27, 36).
     * Divider: 4 pane divider slots in column 1 (slots 10, 19, 28, 37).
     * Content: 24 slots across columns 2-7 (slots 11-16, 20-25, 29-34, 38-43).
     * Navigation: Prev (45), Back/Indicator (49), Next (53).
     * Header: Center slot 4.
     */
    TABBED_6ROW(
            6,
            new int[]{
                    11, 12, 13, 14, 15, 16,
                    20, 21, 22, 23, 24, 25,
                    29, 30, 31, 32, 33, 34,
                    38, 39, 40, 41, 42, 43
            },
            45, 53, 49, 4,
            false
    );

    public static final int[] TAB_SLOTS_6ROW = {9, 18, 27, 36};
    public static final int[] DIVIDER_SLOTS_6ROW = {10, 19, 28, 37};

    private final int rows;
    private final int[] contentSlots;
    private final int prevSlot;
    private final int nextSlot;
    private final int centerSlot;
    private final int headerSlot;
    private final boolean framed;

    PiLayout(int rows, int[] contentSlots, int prevSlot, int nextSlot, int centerSlot, int headerSlot, boolean framed) {
        this.rows = rows;
        this.contentSlots = contentSlots;
        this.prevSlot = prevSlot;
        this.nextSlot = nextSlot;
        this.centerSlot = centerSlot;
        this.headerSlot = headerSlot;
        this.framed = framed;
    }

    public int getRows() {
        return rows;
    }

    public int getSize() {
        return rows * 9;
    }

    public int[] getContentSlots() {
        return contentSlots;
    }

    public int getPageCapacity() {
        return contentSlots.length;
    }

    public int getPrevSlot() {
        return prevSlot;
    }

    public int getNextSlot() {
        return nextSlot;
    }

    public int getCenterSlot() {
        return centerSlot;
    }

    public int getHeaderSlot() {
        return headerSlot;
    }

    public boolean isFramed() {
        return framed;
    }
}
