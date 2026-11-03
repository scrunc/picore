package org.illustro.picore.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-contained zero-dependency bootstrapper for PiCore.
 * <p>
 * Ensures PiCore is present and running. If PiCore is missing from the server,
 * it safely coordinates across all booting plugins to auto-download PiCore.jar
 * from GitHub, loads it into the server dynamically, and verifies compatibility.
 */
public final class PiCoreBootstrap {

    private static final String DEFAULT_REPO = "scrunc/picore";
    private static final String CORE_JAR_NAME = "PiCore.jar";
    private static final Object LOCK = new Object();
    private static volatile boolean IS_DOWNLOADING = false;

    private PiCoreBootstrap() { }

    /**
     * Ensures PiCore is loaded. If absent, downloads it from GitHub and dynamically enables it.
     *
     * @param caller              The calling plugin (e.g. PixelProfiler)
     * @param minRequiredVersion  Minimum PiCore version required (e.g. "1.0.0")
     * @return true if PiCore is loaded and meets version requirements
     */
    public static boolean ensureCore(Plugin caller, String minRequiredVersion) {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin existing = pm.getPlugin("PiCore");

        if (existing != null) {
            return checkCompatibility(caller, existing.getDescription().getVersion(), minRequiredVersion);
        }

        File pluginsDir = caller.getDataFolder().getParentFile();
        File coreJar = new File(pluginsDir, CORE_JAR_NAME);

        // 1. If PiCore.jar already exists on disk, the server discovered it or is loading it
        if (coreJar.exists() && coreJar.length() > 0) {
            return true;
        }

        // 2. Coordinated Auto-Download (Handles 7+ plugins booting simultaneously)
        File lockFile = new File(pluginsDir, ".picore-download.lock");

        synchronized (LOCK) {
            // If another thread in this JVM or another plugin process is downloading, wait for it
            if (IS_DOWNLOADING || isLockFileActive(lockFile)) {
                caller.getLogger().info("[PiCoreBootstrap] Another plugin is currently downloading PiCore.jar. Awaiting completion...");
                waitForFile(coreJar, 15);
                if (coreJar.exists() && coreJar.length() > 0) {
                    return loadAndEnable(caller, coreJar, minRequiredVersion);
                }
            }

            IS_DOWNLOADING = true;
            try {
                acquireLock(lockFile, caller.getName());
                caller.getLogger().info("[PiCoreBootstrap] PiCore not found! Initiating auto-download from GitHub (" + DEFAULT_REPO + ")...");

                boolean downloaded = downloadCore(caller, coreJar, DEFAULT_REPO);
                if (downloaded && coreJar.exists() && coreJar.length() > 0) {
                    caller.getLogger().info("[PiCoreBootstrap] Successfully downloaded PiCore.jar (" + (coreJar.length() / 1024) + " KB). Dynamically loading...");
                    return loadAndEnable(caller, coreJar, minRequiredVersion);
                } else {
                    caller.getLogger().severe("[PiCoreBootstrap] Failed to download PiCore.jar! Please manually install PiCore.jar into /plugins/.");
                    return false;
                }
            } finally {
                releaseLock(lockFile);
                IS_DOWNLOADING = false;
            }
        }
    }

    private static boolean loadAndEnable(Plugin caller, File coreJar, String minVersion) {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin core = pm.getPlugin("PiCore");

        if (core == null || !core.isEnabled()) {
            try {
                core = pm.loadPlugin(coreJar);
                if (core != null) {
                    core.onLoad();
                    pm.enablePlugin(core);
                    caller.getLogger().info("[PiCoreBootstrap] PiCore v" + core.getDescription().getVersion() + " dynamically activated!");
                }
            } catch (Exception e) {
                caller.getLogger().log(Level.SEVERE, "[PiCoreBootstrap] Could not dynamically load PiCore.jar: " + e.getMessage(), e);
                return false;
            }
        }

        if (core != null && core.isEnabled()) {
            return checkCompatibility(caller, core.getDescription().getVersion(), minVersion);
        }
        return false;
    }

    private static boolean checkCompatibility(Plugin caller, String coreVersion, String minVersion) {
        if (minVersion == null || minVersion.trim().isEmpty()) return true;

        if (compareVersions(coreVersion, minVersion) < 0) {
            caller.getLogger().severe("[PiCoreBootstrap] INCOMPATIBLE CORE: " + caller.getName() + " requires PiCore >= "
                    + minVersion + ", but running version is v" + coreVersion + "!");
            return false;
        }
        return true;
    }

    /**
     * Downloads PiCore.jar from GitHub release assets into targetFile.
     */
    public static boolean downloadCore(Plugin caller, File targetFile, String repo) {
        File tempFile = new File(targetFile.getParentFile(), CORE_JAR_NAME + ".tmp");
        String token = resolveGitHubToken(caller.getDataFolder().getParentFile());

        // 1. Direct release download (fastest, works when repo is public)
        String directUrl = "https://github.com/" + repo + "/releases/latest/download/" + CORE_JAR_NAME;
        if (downloadFile(directUrl, tempFile, token)) {
            return finalizeDownload(tempFile, targetFile);
        }

        // 2. GitHub API fallback (works for private repos with token or when direct URL redirects)
        try {
            String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
            String json = fetchString(apiUrl, token);
            if (json != null) {
                // Parse asset download url or asset ID
                String assetUrl = extractAssetUrl(json, CORE_JAR_NAME);
                if (assetUrl != null && downloadFile(assetUrl, tempFile, token)) {
                    return finalizeDownload(tempFile, targetFile);
                }

                // If private repo asset, API requires asset endpoint with Accept header
                String assetApiUrl = extractAssetApiUrl(json, CORE_JAR_NAME);
                if (assetApiUrl != null && downloadApiAsset(assetApiUrl, tempFile, token)) {
                    return finalizeDownload(tempFile, targetFile);
                }
            }
        } catch (Exception e) {
            caller.getLogger().warning("[PiCoreBootstrap] GitHub API query failed: " + e.getMessage());
        }

        return false;
    }

    private static boolean finalizeDownload(File tempFile, File targetFile) {
        if (!tempFile.exists() || tempFile.length() < 1024) {
            tempFile.delete();
            return false;
        }
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile.exists() && targetFile.length() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean downloadFile(String urlString, File dest, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "PiCore-Bootstrap/1.0");

            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            int code = conn.getResponseCode();
            // Handle HTTP redirect manually if not automatically followed across protocols
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    return downloadFile(newUrl, dest, null); // Don't forward auth token to S3/CDN
                }
            }

            if (code != HttpURLConnection.HTTP_OK) {
                return false;
            }

            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception ignored) {
            dest.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean downloadApiAsset(String assetApiUrl, File dest, String token) {
        if (token == null || token.isEmpty()) return false;
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(assetApiUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "PiCore-Bootstrap/1.0");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/octet-stream");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    return downloadFile(newUrl, dest, null);
                }
            }

            if (code != HttpURLConnection.HTTP_OK) {
                return false;
            }

            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception ignored) {
            dest.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String fetchString(String urlString, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PiCore-Bootstrap/1.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractAssetUrl(String json, String assetName) {
        Pattern pattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+" + Pattern.quote(assetName) + ")\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractAssetApiUrl(String json, String assetName) {
        Pattern pattern = Pattern.compile("\"url\"\\s*:\\s*\"(https://api\\.github\\.com/repos/[^/]+/[^/]+/releases/assets/[0-9]+)\"[^}]*\"name\"\\s*:\\s*\"" + Pattern.quote(assetName) + "\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return matcher.group(1);

        // Reverse search
        Pattern pattern2 = Pattern.compile("\"name\"\\s*:\\s*\"" + Pattern.quote(assetName) + "\"[^}]*\"url\"\\s*:\\s*\"(https://api\\.github\\.com/repos/[^/]+/[^/]+/releases/assets/[0-9]+)\"");
        Matcher matcher2 = pattern2.matcher(json);
        return matcher2.find() ? matcher2.group(1) : null;
    }

    private static String resolveGitHubToken(File pluginsDir) {
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.trim().isEmpty()) return token.trim();

        token = System.getProperty("picore.token");
        if (token != null && !token.trim().isEmpty()) return token.trim();

        // Check plugins/picore.token
        File tokenFile = new File(pluginsDir, "picore.token");
        if (tokenFile.exists()) {
            try {
                return new String(Files.readAllBytes(tokenFile.toPath()), StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) { }
        }

        // Check plugins/PiCore/token.txt
        File coreToken = new File(new File(pluginsDir, "PiCore"), "token.txt");
        if (coreToken.exists()) {
            try {
                return new String(Files.readAllBytes(coreToken.toPath()), StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) { }
        }

        return null;
    }

    private static boolean isLockFileActive(File lockFile) {
        if (!lockFile.exists()) return false;
        long age = System.currentTimeMillis() - lockFile.lastModified();
        if (age > 45000) { // Stale lock > 45s
            lockFile.delete();
            return false;
        }
        return true;
    }

    private static void acquireLock(File lockFile, String pluginName) {
        try (FileWriter fw = new FileWriter(lockFile, false)) {
            fw.write(pluginName + ":" + System.currentTimeMillis());
        } catch (Exception ignored) { }
    }

    private static void releaseLock(File lockFile) {
        try {
            if (lockFile.exists()) lockFile.delete();
        } catch (Exception ignored) { }
    }

    private static void waitForFile(File file, int maxSeconds) {
        long end = System.currentTimeMillis() + (maxSeconds * 1000L);
        while (System.currentTimeMillis() < end) {
            if (file.exists() && file.length() > 1024) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) { }
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static int compareVersions(String v1, String v2) {
        if (v1 == null) return (v2 == null) ? 0 : -1;
        if (v2 == null) return 1;

        String clean1 = v1.trim().replaceFirst("^[vV]", "").split("[-_]")[0];
        String clean2 = v2.trim().replaceFirst("^[vV]", "").split("[-_]")[0];

        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = (i < parts1.length) ? parseSegment(parts1[i]) : 0;
            int num2 = (i < parts2.length) ? parseSegment(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private static int parseSegment(String part) {
        try {
            String digits = part.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) {
            return 0;
        }
    }
}

