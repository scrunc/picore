package org.illustro.picore.audio;

/** Opaque handle to a running stream started via {@link PiAudio} — callers never touch SVC types. */
public interface AudioHandle {

    /** Stop playback. Safe to call more than once. */
    void stop();

    /** Whether the stream is still playing (false once stopped, finished, or on error). */
    boolean playing();

    /** Update a locational stream's audible radius live, no restart. No-op on static (per-player) streams. */
    void setDistance(float distance);
}
