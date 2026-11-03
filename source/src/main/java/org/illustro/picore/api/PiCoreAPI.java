package org.illustro.picore.api;

import org.bukkit.plugin.Plugin;
import org.illustro.picore.PiCore;
import org.illustro.picore.menu.PiMenu;
import org.illustro.picore.menu.PiPaginatedMenu;
import org.illustro.picore.updater.ClientRegistration;
import org.illustro.picore.updater.PiCoreUpdater;
import org.illustro.picore.updater.UpdateRegistry;

import java.util.function.Function;

/**
 * Public API entry point for PiCore.
 * Provides central access to the Menu Engine, Anti-Exploit systems, and Ecosystem Health Registry.
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

    /**
     * Registers a downstream plugin with PiCore's ecosystem health and update registry.
     *
     * @param plugin          The client plugin instance
     * @param minCoreVersion  Minimum PiCore version required (e.g. "1.0.0")
     * @param githubRepo      Optional GitHub repo (e.g. "scrunc/Pixel_Profiler") for update checks
     * @return ClientRegistration or null if PiCore is not loaded
     */
    public static ClientRegistration registerClient(Plugin plugin, String minCoreVersion, String githubRepo) {
        PiCore core = PiCore.getInstance();
        if (core != null && core.getUpdateRegistry() != null) {
            return core.getUpdateRegistry().register(plugin, minCoreVersion, githubRepo);
        }
        return null;
    }

    public static UpdateRegistry getUpdateRegistry() {
        PiCore core = PiCore.getInstance();
        return core != null ? core.getUpdateRegistry() : UpdateRegistry.getInstance();
    }

    public static PiCoreUpdater getUpdater() {
        PiCore core = PiCore.getInstance();
        return core != null ? core.getUpdater() : null;
    }
}
