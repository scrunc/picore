package org.illustro.picore.cinematic;

import org.bukkit.entity.Player;

import java.util.Collection;

/** A running {@link Cinematic}. Every viewer is always handed back (camera, game mode, position) when it ends. */
public interface CinematicPlayback {

    /** Current tick on the shot's timeline. */
    int tick();

    boolean running();

    /** Viewers still watching. */
    Collection<Player> viewers();

    /** End now; viewers are restored and the end callback runs. */
    void stop();

    /** Add a viewer to the running shot (e.g. someone started spectating mid-intro). No body double. */
    void join(Player viewer);

    /** Hand one viewer back early (e.g. they left the game). */
    void release(Player viewer);

    // --- used by CinematicEffect factories ---

    void shake(int ticks, double strength);

    /** Body doubles vanish in a puff (before control goes back to the players). */
    void vanishDoubles();

    void vignette(Player viewer, int ticks);

    void frost(Player viewer, int ticks);
}
