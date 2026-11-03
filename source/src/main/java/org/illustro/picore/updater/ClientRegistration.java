package org.illustro.picore.updater;

import org.bukkit.plugin.Plugin;

/**
 * Tracks a client plugin registered with the PiCore ecosystem.
 */
public class ClientRegistration {

    private final Plugin plugin;
    private final String clientName;
    private final String installedVersion;
    private final String minCoreVersion;
    private final String githubRepo;

    private String latestReleaseVersion;
    private String latestReleaseNotes;
    private String downloadNotice;
    private boolean mandatory = false;
    private CompatibilityStatus status = CompatibilityStatus.UNKNOWN;
    private long lastCheckedMillis = 0;

    public ClientRegistration(Plugin plugin, String minCoreVersion, String githubRepo) {
        this.plugin = plugin;
        this.clientName = plugin.getName();
        this.installedVersion = plugin.getDescription().getVersion();
        this.minCoreVersion = (minCoreVersion != null && !minCoreVersion.trim().isEmpty()) ? minCoreVersion.trim() : "1.0.0";
        this.githubRepo = githubRepo;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public String getClientName() {
        return clientName;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public String getMinCoreVersion() {
        return minCoreVersion;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public String getLatestReleaseVersion() {
        return latestReleaseVersion;
    }

    public void setLatestReleaseVersion(String latestReleaseVersion) {
        this.latestReleaseVersion = latestReleaseVersion;
    }

    public String getLatestReleaseNotes() {
        return latestReleaseNotes;
    }

    public void setLatestReleaseNotes(String latestReleaseNotes) {
        this.latestReleaseNotes = latestReleaseNotes;
    }

    public String getDownloadNotice() {
        return downloadNotice != null ? downloadNotice : "Download from your customer portal / DevsDiary.";
    }

    public void setDownloadNotice(String downloadNotice) {
        this.downloadNotice = downloadNotice;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public CompatibilityStatus getStatus() {
        return status;
    }

    public void setStatus(CompatibilityStatus status) {
        this.status = status;
    }

    public long getLastCheckedMillis() {
        return lastCheckedMillis;
    }

    public void setLastCheckedMillis(long lastCheckedMillis) {
        this.lastCheckedMillis = lastCheckedMillis;
    }

    /**
     * Recalculates compatibility against the active running PiCore version.
     */
    public void recalculateStatus(String runningCoreVersion) {
        // 1. Is Core Outdated for this plugin?
        if (compareVersions(runningCoreVersion, minCoreVersion) < 0) {
            this.status = CompatibilityStatus.MUST_UPDATE_CORE;
            return;
        }

        // 2. Is there a newer release of this plugin?
        if (latestReleaseVersion != null && !latestReleaseVersion.isEmpty()) {
            if (compareVersions(latestReleaseVersion, installedVersion) > 0) {
                this.status = mandatory ? CompatibilityStatus.MUST_UPDATE_PLUGIN : CompatibilityStatus.UPDATE_AVAILABLE;
                return;
            }
            this.status = CompatibilityStatus.UP_TO_DATE;
            return;
        }

        this.status = CompatibilityStatus.UP_TO_DATE;
    }

    /**
     * SemVer / component-based version comparison.
     */
    public static int compareVersions(String v1, String v2) {
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
