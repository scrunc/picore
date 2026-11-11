package org.illustro.picore.cinematic;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * Cinematic cameras, published as a Bukkit service by PiCore (module {@code modules.cinematic}).
 *
 * <p>Viewers are put in spectator mode and locked onto an invisible camera entity that glides along the shot's
 * keyframes (client-side interpolated, so it's smooth); they can't move, turn away or shift out of it. Anyone
 * who wasn't already a spectator leaves a skinned body double (a Mannequin wearing their gear) where they
 * stood, so the camera can fly past them — they literally see themselves. When the shot ends (or is stopped,
 * or they quit) each viewer gets their game mode, position and camera back exactly as before.
 */
public interface PiCinematic {

    /**
     * Play {@code shot} for {@code viewers}. {@code onEnd} (nullable) runs on the main thread once every viewer
     * is restored — whether the shot finished, was stopped, or everyone left.
     */
    CinematicPlayback play(Plugin owner, Cinematic shot, Collection<? extends Player> viewers, Runnable onEnd);

    /** Whether {@code p} is currently inside a cinematic (their camera, game mode and position are borrowed). */
    boolean inCinematic(Player p);

    /** Hand {@code p} back early if they're in a cinematic. */
    void release(Player p);
}
