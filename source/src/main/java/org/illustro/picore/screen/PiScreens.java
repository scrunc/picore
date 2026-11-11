package org.illustro.picore.screen;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/**
 * In-world map screens, published as a Bukkit service by PiCore (module {@code modules.screen}).
 *
 * <p>A screen is a {@code cols × rows} wall of invisible, fixed, invulnerable item frames, each holding a map
 * whose only renderer is PiCore's. Draw on its {@link PiCanvas} (128 px per map), then {@link PiScreen#push()}:
 * changed maps are re-sent to whoever can see them. Built for slow-changing boards (brackets, scoreboards,
 * leaderboards) - for video rates use raw map packets instead (ArcadeCab).
 *
 * <p>Screens describe themselves: each frame carries {@code picore:screen = owner:id/index/mapId} in its PDC, so
 * {@link #open} after a restart finds the same frames and maps again (no config file, no leaked map ids).
 */
public interface PiScreens {

    /**
     * Open (or re-open) screen {@code id} of {@code owner}. {@code topLeft} is the wall block behind the top-left
     * map; the screen faces {@code face} (N/S/E/W) and extends viewer-right and down. Frames already there with
     * the same tag and size are reused; anything else tagged with this id is removed and rebuilt. Main thread;
     * the chunks must be loadable.
     */
    PiScreen open(Plugin owner, String id, Block topLeft, BlockFace face, int cols, int rows);

    /** A screen opened earlier (this session), or null. */
    PiScreen get(Plugin owner, String id);

    /**
     * The open screen {@code e} belongs to, or null. PiCore cancels every break/rotate/damage on screen frames at
     * LOWEST priority; owners that want clicks listen with {@code ignoreCancelled = false} and use this +
     * {@link PiScreen#pixelAt}.
     */
    PiScreen screenAt(org.bukkit.entity.Entity e);

    /** Removes the screen's frames from the world. */
    void remove(PiScreen screen);
}
