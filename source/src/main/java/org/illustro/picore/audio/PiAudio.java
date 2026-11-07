package org.illustro.picore.audio;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The shared Simple Voice Chat bridge, published as a Bukkit service by PiCore (module
 * {@code modules.audio}). Covers locational + static (per-player) channels, speech/music encoder tuning
 * and live distance updates; {@link AudioRing}/{@link AudioTrack} are the matching PCM utilities.
 *
 * <p>It lives in PiCore on purpose: Simple Voice Chat hands its server API over exactly once, via an event
 * at server startup. A plugin that hot-reloads gets a fresh classloader and never receives that API again —
 * so a consumer that owned its own SVC connection went silent after a reload. With the connection held
 * here, consumers can be reloaded freely and just re-acquire this service; the audio never drops.
 *
 * <p>Simple Voice Chat is optional. Without it this service is still registered, {@link #available()}
 * returns false and every {@code start*} call returns null, so consumers simply play silent.
 */
public interface PiAudio {

    /** True once SVC has handed over its server API (false if SVC is absent or its server hasn't started). */
    boolean available();

    /** Whether {@code player} has the Simple Voice Chat mod installed (regardless of whether connected). */
    boolean hasVoiceChat(UUID player);

    /** Whether {@code player} is connected to voice chat and not disabled — i.e. can actually hear audio. */
    boolean isVoiceConnected(UUID player);

    /**
     * Start a locational stream at {@code (x,y,z)}, audible to anyone within {@code distance} blocks,
     * fed by {@code supplier} (one 960-sample 20 ms mono frame per call, or null to end). {@code onStopped}
     * (nullable) runs when playback ends. Returns a stop handle, or null if audio can't start (SVC
     * unavailable, bad world, etc.).
     */
    AudioHandle startLocational(World world, double x, double y, double z, float distance, AudioMode mode,
                                 Supplier<short[]> supplier, Runnable onStopped);

    /**
     * Start a static (non-locational) stream heard by exactly {@code listener}, wherever they stand —
     * for per-player playback such as a personal radio channel. {@code onStopped} (nullable) runs when
     * playback ends. Returns null if the player isn't connected to voice chat or audio can't start.
     */
    AudioHandle startStatic(Player listener, AudioMode mode, Supplier<short[]> supplier, Runnable onStopped);
}
