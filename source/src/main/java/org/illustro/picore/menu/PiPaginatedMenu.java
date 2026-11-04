package org.illustro.picore.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reusable paginated menu engine for PiCore.
 * Generates paged views with automated sticky navigation bars (Previous, Next, Close, Page Indicator).
 * Supports both full unframed views and 6-row / 5-row framed border layouts via {@link PiLayout}.
 */
public class PiPaginatedMenu<T> {

    private final String titleTemplate;
    private final PiLayout layout;
    private final Function<T, PiButton> itemConverter;
    private final List<T> items = new ArrayList<>();
    private final Map<Integer, PiButton> toolbarButtons = new HashMap<>();

    private ItemStack borderItem = PiMenu.pane(Material.GRAY_STAINED_GLASS_PANE, " ");
    private PiButton headerButton;
    private Consumer<Player> onReturn;
    private BiConsumer<Player, Integer> onPageChange;

    public PiPaginatedMenu(String titleTemplate, Function<T, PiButton> itemConverter) {
        this(titleTemplate, PiLayout.UNFRAMED_5ROW, itemConverter);
    }

    public PiPaginatedMenu(String titleTemplate, PiLayout layout, Function<T, PiButton> itemConverter) {
        this.titleTemplate = titleTemplate;
        this.layout = layout != null ? layout : PiLayout.UNFRAMED_5ROW;
        this.itemConverter = itemConverter;
    }

    public static <T> PiPaginatedMenu<T> create(String titleTemplate, Function<T, PiButton> itemConverter) {
        return new PiPaginatedMenu<>(titleTemplate, itemConverter);
    }

    public static <T> PiPaginatedMenu<T> create(String titleTemplate, PiLayout layout, Function<T, PiButton> itemConverter) {
        return new PiPaginatedMenu<>(titleTemplate, layout, itemConverter);
    }

    public PiPaginatedMenu<T> items(List<T> items) {
        this.items.clear();
        if (items != null) this.items.addAll(items);
        return this;
    }

    public PiPaginatedMenu<T> border(ItemStack borderItem) {
        if (borderItem != null) this.borderItem = borderItem;
        return this;
    }

    public PiPaginatedMenu<T> borderPane(Material glassPane) {
        return border(PiMenu.pane(glassPane != null ? glassPane : Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    public PiPaginatedMenu<T> header(PiButton headerButton) {
        this.headerButton = headerButton;
        return this;
    }

    public PiPaginatedMenu<T> toolbar(int slot, PiButton button) {
        if (slot >= 0 && slot < layout.getSize()) {
            this.toolbarButtons.put(slot, button);
        }
        return this;
    }

    public PiPaginatedMenu<T> onReturn(Consumer<Player> handler) {
        this.onReturn = handler;
        return this;
    }

    public PiPaginatedMenu<T> onPageChange(BiConsumer<Player, Integer> handler) {
        this.onPageChange = handler;
        return this;
    }

    public PiLayout getLayout() {
        return layout;
    }

    public int getMaxPages() {
        if (items.isEmpty()) return 1;
        return (int) Math.ceil((double) items.size() / layout.getPageCapacity());
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    public void openPage(Player player, int page) {
        int maxPages = getMaxPages();
        int currentPage = Math.max(0, Math.min(maxPages - 1, page));

        String title = titleTemplate
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max_pages%", String.valueOf(maxPages));

        PiMenu menu = PiMenu.create(title, layout.getRows());

        // 1. Frame or border filling
        if (layout.isFramed()) {
            PiGrid.frame(menu, borderItem);
        } else {
            // Fill bottom navigation row
            int startSlot = (layout.getRows() - 1) * 9;
            int endSlot = layout.getSize();
            PiButton b = PiButton.create(borderItem);
            for (int i = startSlot; i < endSlot; i++) {
                menu.button(i, b);
            }
        }

        // 2. Populate header if present
        if (headerButton != null && layout.getHeaderSlot() >= 0) {
            menu.button(layout.getHeaderSlot(), headerButton);
        }

        // 3. Populate content slots
        int capacity = layout.getPageCapacity();
        int startIdx = currentPage * capacity;
        int endIdx = Math.min(startIdx + capacity, items.size());
        int[] contentSlots = layout.getContentSlots();

        for (int i = startIdx; i < endIdx; i++) {
            T data = items.get(i);
            PiButton btn = itemConverter.apply(data);
            if (btn != null) {
                int targetSlot = contentSlots[i - startIdx];
                menu.button(targetSlot, btn);
            }
        }

        // 4. Navigation Controls
        if (currentPage > 0) {
            menu.button(layout.getPrevSlot(), PiButton.create(Material.ARROW, "&e« Previous Page")
                    .onClick((p, e) -> {
                        PiSound.page(p);
                        if (onPageChange != null) onPageChange.accept(p, currentPage - 1);
                        openPage(p, currentPage - 1);
                    }));
        }

        if (currentPage < maxPages - 1) {
            menu.button(layout.getNextSlot(), PiButton.create(Material.ARROW, "&eNext Page »")
                    .onClick((p, e) -> {
                        PiSound.page(p);
                        if (onPageChange != null) onPageChange.accept(p, currentPage + 1);
                        openPage(p, currentPage + 1);
                    }));
        }

        // 5. Center Button: Page Indicator or Return
        if (onReturn != null) {
            menu.button(layout.getCenterSlot(), PiButton.create(Material.BARRIER, "&c« Back / Close")
                    .onClick((p, e) -> {
                        PiSound.click(p);
                        onReturn.accept(p);
                    }));
        } else {
            menu.button(layout.getCenterSlot(), PiButton.create(Material.PAPER,
                    "&7Page &f" + (currentPage + 1) + " &7of &f" + maxPages,
                    "&8Total items: " + items.size())
                    .onClick((p, e) -> {
                        PiSound.click(p);
                        p.closeInventory();
                    }));
        }

        // 6. Custom toolbar buttons on the border/chrome
        for (Map.Entry<Integer, PiButton> entry : toolbarButtons.entrySet()) {
            menu.button(entry.getKey(), entry.getValue());
        }

        menu.open(player);
    }

    /**
     * Refreshes the content items on the player's currently open page in-place without closing the inventory.
     */
    public void refreshPage(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        int maxPages = getMaxPages();
        int currentPage = Math.max(0, Math.min(maxPages - 1, page));
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof PiMenuHolder holder) {
            int capacity = layout.getPageCapacity();
            int startIdx = currentPage * capacity;
            int endIdx = Math.min(startIdx + capacity, items.size());
            int[] contentSlots = layout.getContentSlots();

            for (int i = 0; i < capacity; i++) {
                int targetSlot = contentSlots[i];
                int itemIdx = startIdx + i;
                if (itemIdx < endIdx) {
                    T data = items.get(itemIdx);
                    PiButton btn = itemConverter.apply(data);
                    holder.updateSlot(targetSlot, btn);
                } else {
                    holder.updateSlot(targetSlot, PiButton.create(Material.AIR, " "));
                }
            }
        } else {
            openPage(player, page);
        }
    }
}
