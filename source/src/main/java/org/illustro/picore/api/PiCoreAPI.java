package org.illustro.picore.api;

import org.illustro.picore.PiCore;
import org.illustro.picore.menu.PiMenu;
import org.illustro.picore.menu.PiPaginatedMenu;

import java.util.function.Function;

/**
 * Public API entry point for PiCore.
 * Provides central access to the Menu Engine and Anti-Exploit systems.
 */
public final class PiCoreAPI {

    private PiCoreAPI() { }

    public static PiCore getCore() {
        return PiCore.getInstance();
    }

    public static PiMenu createMenu(String title, int rows) {
        return PiMenu.create(title, rows);
    }

    public static <T> PiPaginatedMenu<T> createPaginatedMenu(String titleTemplate, Function<T, org.illustro.picore.menu.PiButton> converter) {
        return PiPaginatedMenu.create(titleTemplate, converter);
    }
}

