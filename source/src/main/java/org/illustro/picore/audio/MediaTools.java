package org.illustro.picore.audio;

import java.io.File;
import java.io.IOException;

/**
 * The shared <b>ffmpeg / yt-dlp</b> pair, published as a Bukkit service alongside {@link PiAudio}.
 *
 * <p>ffmpeg is a ~220 MB static binary. Every plugin that ingests audio used to look for its own copy in
 * its own data folder — MachineConstruct in {@code plugins/MachineConstruct/bin/}, VideoWall in its own —
 * so a server that ran both paid for it twice, and a mirror copied without one of those folders went
 * silent with nothing but a WARN in the log to say why. One copy, in {@code plugins/PiCore/bin/},
 * serves every consumer.
 *
 * <p>Nothing here does any NETWORKING with ffmpeg: fetching is yt-dlp's job (it handles HLS and the
 * bot-wall), and ffmpeg only ever reads a local file or a pipe. Consumers keep their own download logic —
 * what lives here is the part that is identical everywhere: where the binaries are, and turning a
 * compressed file into the 48 kHz mono WAV {@link AudioTrack} wants.
 */
public interface MediaTools {

    /** The ffmpeg binary, whether or not it exists — for error messages. Honours {@code media.ffmpeg-path}. */
    File ffmpeg();

    /** The yt-dlp binary, whether or not it exists. Honours {@code media.ytdlp-path}. */
    File ytdlp();

    /** True if ffmpeg is actually present (decoding needs it). */
    boolean hasFfmpeg();

    /** True if yt-dlp is actually present (downloading needs it). */
    boolean hasYtdlp();

    /** Where the binaries are looked for: {@code plugins/PiCore/bin} unless {@code media.bin-dir} says otherwise. */
    File binDir();

    /**
     * Decode any audio (or a video's audio track) to a temp 48 kHz mono WAV that {@link AudioTrack#load}
     * can read. Blocking — call off the main thread. The caller deletes the file when done.
     *
     * @param src   the file to decode
     * @param cache directory for the temp file, or null for the system temp directory
     * @throws IOException if ffmpeg is missing or the decode fails (the message carries ffmpeg's stderr)
     */
    File decodeToWav(File src, File cache) throws IOException, InterruptedException;

    /** Make a binary executable (a fresh copy off a zip usually is not, on Linux). Safe to call repeatedly. */
    void ensureExecutable(File bin);
}
