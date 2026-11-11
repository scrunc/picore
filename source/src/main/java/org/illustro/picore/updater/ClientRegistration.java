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
        this.minCoreVersion = (minCoreVersion != null && !minCoreVersion.trim().isEmpty()) ? minCoreVersion.trim()
                : "1.0.0";
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
        return downloadNotice != null ? downloadNotice : "Check the official release page for updates.";
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
     * SemVer / component-based version comparison compliant with SemVer 2.0.0.
     * Compares core numerical segments (major.minor.patch) and pre-release
     * identifiers.
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null)
            return (v2 == null) ? 0 : -1;
        if (v2 == null)
            return 1;

        // Clean leading 'v' or 'V' and strip build metadata after '+'
        String s1 = v1.trim().replaceFirst("^[vV]", "").split("\\+")[0];
        String s2 = v2.trim().replaceFirst("^[vV]", "").split("\\+")[0];

        String[] split1 = s1.split("-", 2);
        String[] split2 = s2.split("-", 2);

        String core1 = split1[0];
        String core2 = split2[0];
        String pre1 = split1.length > 1 ? split1[1] : "";
        String pre2 = split2.length > 1 ? split2[1] : "";

        String[] parts1 = core1.split("\\.");
        String[] parts2 = core2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = (i < parts1.length) ? parseSegment(parts1[i]) : 0;
            int num2 = (i < parts2.length) ? parseSegment(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }

        // Core versions are equal. SemVer rule:
        // A version without a pre-release tag has higher precedence than with a
        // pre-release tag.
        // e.g. "0.9.1" > "0.9.1-alpha.1"
        if (pre1.isEmpty() && !pre2.isEmpty())
            return 1;
        if (!pre1.isEmpty() && pre2.isEmpty())
            return -1;
        if (pre1.isEmpty() && pre2.isEmpty())
            return 0;

        // Both have pre-release tags: compare identifiers (e.g. "alpha.2" vs "alpha.1")
        return comparePreRelease(pre1, pre2);
    }

    private static int comparePreRelease(String pre1, String pre2) {
        String[] p1 = pre1.split("\\.");
        String[] p2 = pre2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            if (i >= p1.length)
                return -1;
            if (i >= p2.length)
                return 1;
            String seg1 = p1[i];
            String seg2 = p2[i];
            boolean num1 = seg1.matches("\\d+");
            boolean num2 = seg2.matches("\\d+");
            if (num1 && num2) {
                int cmp = Integer.compare(Integer.parseInt(seg1), Integer.parseInt(seg2));
                if (cmp != 0)
                    return cmp;
            } else if (num1) {
                return -1;
            } else if (num2) {
                return 1;
            } else {
                int cmp = seg1.compareToIgnoreCase(seg2);
                if (cmp != 0)
                    return cmp;
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
