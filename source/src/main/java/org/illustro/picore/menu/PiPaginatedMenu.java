package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reusable paginated menu engine for PiCore.
 * Generates paged views with automated sticky navigation bars (Previous, Next, Close, Page Indicator).
 */
public class PiPaginatedMenu<T> {

    private final String titleTemplate;
    private final Function<T, PiButton> itemConverter;
    private final List<T> items = new ArrayList<>();
    private final int itemsPerPage = 36; // Rows 1-4 for content, Row 5 for navigation
    private Consumer<Player> onReturn;

    public PiPaginatedMenu(String titleTemplate, Function<T, PiButton> itemConverter) {
        this.titleTemplate = titleTemplate;
        this.itemConverter = itemConverter;
    }

    public static <T> PiPaginatedMenu<T> create(String titleTemplate, Function<T, PiButton> itemConverter) {
        return new PiPaginatedMenu<>(titleTemplate, itemConverter);
    }

    public PiPaginatedMenu<T> items(List<T> items) {
        this.items.clear();
        if (items != null) this.items.addAll(items);
        return this;
    }

    public PiPaginatedMenu<T> onReturn(Consumer<Player> handler) {
        this.onReturn = handler;
        return this;
    }

    public int getMaxPages() {
        if (items.isEmpty()) return 1;
        return (int) Math.ceil((double) items.size() / itemsPerPage);
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

        PiMenu menu = PiMenu.create(title, 5);

        // Fill background border on row 5
        ItemStack border = PiMenu.pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 45; i++) {
            menu.button(i, PiButton.create(border));
        }

        // Populate content slots 0 - 35
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, items.size());

        for (int i = startIdx; i < endIdx; i++) {
            T data = items.get(i);
            PiButton btn = itemConverter.apply(data);
            if (btn != null) {
                menu.button(i - startIdx, btn);
            }
        }

        // Navigation Controls
        if (currentPage > 0) {
            menu.button(36, PiButton.create(Material.ARROW, "&e« Previous Page")
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick((p, e) -> openPage(p, currentPage - 1)));
        }

        if (currentPage < maxPages - 1) {
            menu.button(44, PiButton.create(Material.ARROW, "&eNext Page »")
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick((p, e) -> openPage(p, currentPage + 1)));
        }

        // Center Button: Page Indicator or Return
        if (onReturn != null) {
            menu.button(40, PiButton.create(Material.BARRIER, "&c« Back / Close")
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick((p, e) -> onReturn.accept(p)));
        } else {
            menu.button(40, PiButton.create(Material.PAPER,
                    "&7Page &f" + (currentPage + 1) + " &7of &f" + maxPages,
                    "&8Total items: " + items.size())
                    .onClick((p, e) -> p.closeInventory()));
        }

        menu.open(player);
    }
}
