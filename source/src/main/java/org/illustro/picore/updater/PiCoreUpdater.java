package org.illustro.picore.updater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.illustro.picore.PiCore;
import org.illustro.picore.bootstrap.PiCoreBootstrap;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages release checks and update notices for PiCore and registered modules.
 * Reads the public ecosystem manifest (ecosystem.json) from GitHub.
 */
public class PiCoreUpdater {

    private final PiCore plugin;
    private String latestCoreVersion;
    private String coreReleaseNotes;
    private boolean coreUpdateAvailable = false;
    private long lastCheckedTime = 0;
    private volatile boolean isChecking = false;

    public PiCoreUpdater(PiCore plugin) {
        this.plugin = plugin;
        this.latestCoreVersion = plugin.getDescription().getVersion();
    }

    public String getCoreRepo() {
        return plugin.getConfig().getString("updater.github-repo", "scrunc/picore");
    }

    public String getManifestUrl() {
        return "https://raw.githubusercontent.com/" + getCoreRepo() + "/main/ecosystem.json";
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
     * Checks GitHub for PiCore updates and queries the ecosystem.json manifest for
     * client plugin releases.
     * Remote fetching occurs asynchronously; parsing and registry registration
     * execute safely on the main thread.
     */
    public void checkAllAsync(Consumer<Boolean> callback) {
        if (isChecking) {
            if (callback != null)
                callback.accept(false);
            return;
        }

        isChecking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String coreJson = null;
            String manifestJson = null;

            try {
                // 1. Fetch remote release & manifest HTTP responses asynchronously
                coreJson = fetchUrl(CORE_REPO_URL());
                manifestJson = fetchUrl(getManifestUrl());

                // Fallback to bundled ecosystem.json if remote is unreachable
                if (manifestJson == null || manifestJson.trim().isEmpty()) {
                    java.io.InputStream in = plugin.getResource("ecosystem.json");
                    if (in != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null)
                                sb.append(line);
                            manifestJson = sb.toString();
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[PiCoreUpdater] Error while fetching remote updates: " + e.getMessage(), e);
            }

            final String finalCoreJson = coreJson;
            final String finalManifestJson = manifestJson;

            // 2. Process results on the MAIN thread to ensure 100% Bukkit API & Console
            // thread safety
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Process PiCore release info
                    parseCoreRelease(finalCoreJson);

                    // Process Ecosystem manifest and auto-discover plugins
                    processManifest(finalManifestJson);

                    // Recalculate compatibility statuses
                    UpdateRegistry.getInstance().recalculateAll();
                    lastCheckedTime = System.currentTimeMillis();

                    isChecking = false;
                    logSummary();
                    if (callback != null)
                        callback.accept(true);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[PiCoreUpdater] Error applying updates: " + e.getMessage(),
                            e);
                    isChecking = false;
                    if (callback != null)
                        callback.accept(false);
                }
            });
        });
    }

    private void parseCoreRelease(String json) {
        if (json == null || json.trim().isEmpty())
            return;
        try {
            // newest release built for THIS server's Minecraft version (see PiCoreBootstrap.pickRelease)
            String[] rel = PiCoreBootstrap.pickRelease(json, PiCoreBootstrap.minecraftVersion());
            if (rel == null)
                return;
            this.latestCoreVersion = rel[0].replaceFirst("^[vV]", "");
            this.coreReleaseNotes = rel[1];
            int cmp = ClientRegistration.compareVersions(this.latestCoreVersion,
                    plugin.getDescription().getVersion());
            this.coreUpdateAvailable = (cmp > 0);
        } catch (Exception ignored) {
        }
    }

    private void processManifest(String manifestJson) {
        if (manifestJson == null || manifestJson.trim().isEmpty())
            return;
        try {
            JsonObject root = JsonParser.parseString(manifestJson).getAsJsonObject();

            // Auto-discover active server plugins that match entries in the ecosystem
            // manifest (Thread Safe on Main Thread)
            for (org.bukkit.plugin.Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p == null || p.equals(plugin))
                    continue;
                String matchedKey = findMatchingKey(root, p.getName());
                if (matchedKey != null && UpdateRegistry.getInstance().getClient(p.getName()) == null) {
                    JsonObject data = root.getAsJsonObject(matchedKey);
                    String minCore = data.has("min_core_version") ? data.get("min_core_version").getAsString()
                            : "1.0.0";
                    UpdateRegistry.getInstance().register(p, minCore, null);
                }
            }

            for (ClientRegistration client : UpdateRegistry.getInstance().getAllClients()) {
                String key = findMatchingKey(root, client.getClientName());
                if (key != null) {
                    JsonObject data = root.getAsJsonObject(key);
                    if (data.has("latest")) {
                        client.setLatestReleaseVersion(data.get("latest").getAsString());
                    }
                    if (data.has("notes")) {
                        client.setLatestReleaseNotes(data.get("notes").getAsString());
                    }
                    if (data.has("download_notice")) {
                        client.setDownloadNotice(data.get("download_notice").getAsString());
                    }
                    if (data.has("mandatory")) {
                        client.setMandatory(data.get("mandatory").getAsBoolean());
                    }
                }
                client.setLastCheckedMillis(System.currentTimeMillis());
                client.recalculateStatus(plugin.getDescription().getVersion());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PiCoreUpdater] Could not parse ecosystem manifest: " + e.getMessage());
        }
    }

    private String findMatchingKey(JsonObject root, String pluginName) {
        String normalizedPlugin = pluginName.replaceAll("[_\\-\\s]", "").toLowerCase();
        for (String k : root.keySet()) {
            if (k.equalsIgnoreCase(pluginName)) {
                return k;
            }
            if (k.replaceAll("[_\\-\\s]", "").toLowerCase().equals(normalizedPlugin)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Staging PiCore update into /plugins/update/PiCore.jar
     */
    public void stageCoreUpdateAsync(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) {
                updateFolder.mkdirs();
            }

            File targetFile = new File(updateFolder, "PiCore.jar");
            plugin.getLogger().info("[PiCoreUpdater] Staging PiCore update into " + targetFile.getPath() + "...");

            boolean ok = PiCoreBootstrap.downloadCore(plugin, targetFile, getCoreRepo());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok && targetFile.exists() && targetFile.length() > 0) {
                    plugin.getLogger().info(
                            "[PiCoreUpdater] Successfully staged PiCore.jar to /plugins/update/. Applied automatically on next reboot.");
                    if (callback != null)
                        callback.accept(true);
                } else {
                    plugin.getLogger().warning("[PiCoreUpdater] Failed to stage PiCore update from GitHub.");
                    if (callback != null)
                        callback.accept(false);
                }
            });
        });
    }

    /**
     * Alerts administrators on join if any plugins have available updates or
     * critical mismatches.
     */
    public void alertAdminIfOutdated(Player player) {
        if (!player.hasPermission("picore.admin"))
            return;

        List<String> alerts = new ArrayList<>();
        if (coreUpdateAvailable) {
            alerts.add(ChatColor.AQUA + "PiCore" + ChatColor.GRAY + " (v" + plugin.getDescription().getVersion() + " ➔ "
                    + ChatColor.GOLD + "v" + latestCoreVersion + ")");
        }

        for (ClientRegistration client : UpdateRegistry.getInstance().getAllClients()) {
            if (client.getStatus() == CompatibilityStatus.UPDATE_AVAILABLE || client.getStatus().isCritical()) {
                alerts.add(ChatColor.YELLOW + client.getClientName() + ChatColor.GRAY + " (v"
                        + client.getInstalledVersion()
                        + " ➔ " + ChatColor.GOLD + "v" + client.getLatestReleaseVersion() + ")");
            }
        }

        if (!alerts.isEmpty()) {
            player.sendMessage(ChatColor.DARK_GRAY + "«" + ChatColor.AQUA + ChatColor.BOLD + "PiCore"
                    + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GOLD + "Updates are available for: " + String.join(ChatColor.DARK_GRAY + ", ", alerts));
            player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/picore gui" + ChatColor.GRAY
                    + " for full details and release notes.");
        }
    }

    private void logSummary() {
        plugin.getLogger().info("=== [PiCore Ecosystem Compatibility Ledger] ===");
        plugin.getLogger()
                .info("PiCore: v" + plugin.getDescription().getVersion() + " (Latest: v" + latestCoreVersion + ")"
                        + (coreUpdateAvailable ? " [UPDATE AVAILABLE]" : " [UP TO DATE]"));

        for (ClientRegistration c : UpdateRegistry.getInstance().getAllClients()) {
            String note = (c.getLatestReleaseNotes() != null && !c.getLatestReleaseNotes().isEmpty())
                    ? " | Notes: " + c.getLatestReleaseNotes()
                    : "";
            plugin.getLogger().info("• " + c.getClientName() + ": v" + c.getInstalledVersion()
                    + " (Latest: v" + c.getLatestReleaseVersion() + ") -> " + c.getStatus().getBadge() + note);
        }
        plugin.getLogger().info("================================================");
    }

    private String CORE_REPO_URL() {
        return "https://api.github.com/repos/" + getCoreRepo() + "/releases?per_page=30";
    }

    private String fetchUrl(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PiCore-Updater/1.0");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
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
            if (conn != null)
                conn.disconnect();
        }
    }
}
