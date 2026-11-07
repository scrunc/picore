package org.illustro.picore.audio;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** {@link MediaTools} over binaries in {@code plugins/PiCore/bin} (or wherever {@code media.*} points). */
final class MediaToolsImpl implements MediaTools {

    private final JavaPlugin plugin;

    MediaToolsImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public File binDir() {
        String cfg = plugin.getConfig().getString("media.bin-dir");
        return (cfg != null && !cfg.isBlank()) ? new File(cfg) : new File(plugin.getDataFolder(), "bin");
    }

    @Override
    public File ffmpeg() {
        return resolve("media.ffmpeg-path", "ffmpeg");
    }

    @Override
    public File ytdlp() {
        return resolve("media.ytdlp-path", "yt-dlp");
    }

    @Override
    public boolean hasFfmpeg() {
        return ffmpeg().isFile();
    }

    @Override
    public boolean hasYtdlp() {
        return ytdlp().isFile();
    }

    private File resolve(String configKey, String defaultName) {
        String cfg = plugin.getConfig().getString(configKey);
        if (cfg != null && !cfg.isBlank()) return new File(cfg);
        File f = new File(binDir(), defaultName);
        if (!f.isFile()) {
            File exe = new File(binDir(), defaultName + ".exe");   // Windows dev boxes
            if (exe.isFile()) return exe;
        }
        return f;
    }

    @Override
    public File decodeToWav(File src, File cache) throws IOException, InterruptedException {
        File ff = ffmpeg();
        if (!ff.isFile()) throw new IOException("ffmpeg binary missing at " + ff.getPath());
        ensureExecutable(ff);
        if (cache != null) //noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
        File out = File.createTempFile("pa-decode-", ".wav", cache);
        List<String> args = new ArrayList<>(List.of(
                ff.getAbsolutePath(), "-hide_banner", "-loglevel", "error", "-y",
                "-i", src.getAbsolutePath(),
                "-ar", String.valueOf(AudioTrack.SAMPLE_RATE), "-ac", "1", "-f", "wav",
                out.getAbsolutePath()));
        ProcessBuilder pb = new ProcessBuilder(args);
        if (cache != null) {   // keep ffmpeg's scratch files off the system temp dir
            String dir = cache.getAbsolutePath();
            pb.environment().put("TMPDIR", dir);
            pb.environment().put("TMP", dir);
            pb.environment().put("TEMP", dir);
        }
        Process p = pb.start();
        String err = drain(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new IOException("ffmpeg decode failed (" + code + "): " + err);
        }
        return out;
    }

    /** Read a child's stderr fully (so the pipe cannot block) and keep the tail for the error message. */
    private static String drain(InputStream stream) {
        StringBuilder sb = new StringBuilder();
        try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 4000) sb.delete(0, sb.length() - 4000);
            }
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }

    @Override
    public void ensureExecutable(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true, false);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(f.toPath(), perms);
        } catch (Exception ignored) {
        }
    }
}
