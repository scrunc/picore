package org.illustro.picore.audio;

/**
 * Which Opus encoder tuning a stream should use.
 *
 * <p>Simple Voice Chat's deprecated no-arg {@code createEncoder()} is VOIP/speech-tuned and rolls off
 * bass — fine for a talking NPC, bad for music (this is what MachineConstruct's jukebox bass-quality fix
 * was about). {@link #MUSIC} asks for {@code OpusEncoderMode.AUDIO} instead, preserving the full
 * frequency range; {@link #SPEECH} keeps the original VOIP tuning.
 */
public enum AudioMode {
    /** VOIP-tuned encoding — talking NPCs, barks, sound effects. */
    SPEECH,
    /** Full-frequency-range encoding — music, jukeboxes, video/stream audio. */
    MUSIC
}
