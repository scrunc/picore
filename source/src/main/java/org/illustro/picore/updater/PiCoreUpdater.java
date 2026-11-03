package org.illustro.picore.updater;

import org.bukkit.Bukkit;
import org.illustro.picore.PiCore;
import org.illustro.picore.bootstrap.PiCoreBootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages GitHub release checks and auto-updates for PiCore and registered client plugins.
 */
public class PiCoreUpdater {

    private final PiCore plugin;
    private final String coreRepo = "scrunc/picore";
    private String latestCoreVersion;
    private String coreReleaseNotes;
    private boolean coreUpdateAvailable = false;
    private long lastCheckedTime = 0;
    private volatile boolean isChecking = false;

    public PiCoreUpdater(PiCore plugin) {
        this.plugin = plugin;
        this.latestCoreVersion = plugin.getDescription().getVersion();
    }

    public String getLatestCoreVersion() {
        return latestCoreVersion;
    }

    public String getCoreReleaseNotes() {
        return coreReleaseNotes;
    }

    public boolean isCoreUpdateAvailable() {
        return coreUpdateAvailable;
    }

    public long getLastCheckedTime() {
        return lastCheckedTime;
    }

    public boolean isChecking() {
        return isChecking;
    }

    /**
     * Checks GitHub for updates to PiCore and all registered client plugins.
     */
    public void checkAllAsync(Consumer<Boolean> callback) {
        if (isChecking) {
            if (callback != null) callback.accept(false);
            return;
        }

        isChecking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 1. Check PiCore latest release
                checkCoreRelease();

                // 2. Check each registered client plugin
                for (ClientRegistration client : UpdateRegistry.getInstance().getAllClients()) {
                    if (client.getGithubRepo() != null && !client.getGithubRepo().trim().isEmpty()) {
                        checkClientRelease(client);
                    }
                }

                // 3. Recalculate all compatibility statuses
                UpdateRegistry.getInstance().recalculateAll();
                lastCheckedTime = System.currentTimeMillis();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    isChecking = false;
                    logSummary();
                    if (callback != null) callback.accept(true);
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[PiCoreUpdater] Error while checking updates: " + e.getMessage(), e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    isChecking = false;
                    if (callback != null) callback.accept(false);
                });
            }
        });
    }

    private void checkCoreRelease() {
        try {
            String json = fetchGitHubJson(coreRepo);
            if (json != null) {
                String tag = extractTag(json);
                if (tag != null) {
                    this.latestCoreVersion = tag.replaceFirst("^[vV]", "");
                    this.coreReleaseNotes = extractBody(json);
                    int cmp = ClientRegistration.compareVersions(this.latestCoreVersion, plugin.getDescription().getVersion());
                    this.coreUpdateAvailable = (cmp > 0);
                }
            }
        } catch (Exception ignored) { }
    }

    private void checkClientRelease(ClientRegistration client) {
        try {
            client.setStatus(CompatibilityStatus.CHECKING);
            String json = fetchGitHubJson(client.getGithubRepo());
            if (json != null) {
                String tag = extractTag(json);
                if (tag != null) {
                    client.setLatestReleaseVersion(tag.replaceFirst("^[vV]", ""));
                    client.setLatestReleaseNotes(extractBody(json));
                }
            }
            client.setLastCheckedMillis(System.currentTimeMillis());
            client.recalculateStatus(plugin.getDescription().getVersion());
        } catch (Exception ignored) {
            client.recalculateStatus(plugin.getDescription().getVersion());
        }
    }

    /**
     * Downloads the latest PiCore.jar and stages it into the standard Bukkit /plugins/update/ folder.
     * The server will automatically apply it cleanly upon next reboot.
     */
    public void stageCoreUpdateAsync(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File targetFile = new File(updateFolder, "PiCore.jar");
            plugin.getLogger().info("[PiCoreUpdater] Staging PiCore update into " + targetFile.getPath() + "...");

            boolean ok = PiCoreBootstrap.downloadCore(plugin, targetFile, coreRepo);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok && targetFile.exists() && targetFile.length() > 0) {
                    plugin.getLogger().info("[PiCoreUpdater] Successfully staged PiCore.jar to /plugins/update/. It will be installed automatically on the next server reboot.");
                    if (callback != null) callback.accept(true);
                } else {
                    plugin.getLogger().warning("[PiCoreUpdater] Failed to stage PiCore update from GitHub.");
                    if (callback != null) callback.accept(false);
                }
            });
        });
    }

    private void logSummary() {
        plugin.getLogger().info("=== [PiCore Ecosystem Compatibility] ===");
        plugin.getLogger().info("PiCore: v" + plugin.getDescription().getVersion() + " (Latest: v" + latestCoreVersion + ")"
                + (coreUpdateAvailable ? " [UPDATE AVAILABLE]" : " [UP TO DATE]"));

        for (ClientRegistration c : UpdateRegistry.getInstance().getAllClients()) {
            plugin.getLogger().info("• " + c.getClientName() + ": v" + c.getInstalledVersion()
                    + " (Req Core: " + c.getMinCoreVersion() + ") -> " + c.getStatus().getBadge());
        }
        plugin.getLogger().info("=========================================");
    }

    private String fetchGitHubJson(String repo) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create("https://api.github.com/repos/" + repo + "/releases/latest").toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PiCore-Updater/1.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.trim().isEmpty()) {
                token = System.getProperty("picore.token");
            }
            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
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

    private String extractTag(String json) {
        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractBody(String json) {
        Pattern pattern = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\r\\n", "\n").replace("\\n", "\n");
        }
        return "";
    }
}

