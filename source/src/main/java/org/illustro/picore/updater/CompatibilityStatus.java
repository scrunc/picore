package org.illustro.picore.updater;

import org.bukkit.ChatColor;

/**
 * Compatibility status of a client plugin or the PiCore framework.
 */
public enum CompatibilityStatus {

    UP_TO_DATE(ChatColor.GREEN + "✔ Up to Date", ChatColor.GREEN, "Running the latest verified version."),
    UPDATE_AVAILABLE(ChatColor.GOLD + "▲ Update Available", ChatColor.GOLD, "A newer release is published on GitHub."),
    MUST_UPDATE_CORE(ChatColor.RED + "✖ CRITICAL: CORE OUTDATED", ChatColor.RED, "Plugin requires a newer PiCore version to function."),
    MUST_UPDATE_PLUGIN(ChatColor.DARK_RED + "✖ CRITICAL: PLUGIN OUTDATED", ChatColor.DARK_RED, "PiCore requires this plugin to be updated."),
    CHECKING(ChatColor.GRAY + "⏳ Checking...", ChatColor.GRAY, "Querying GitHub release endpoints..."),
    UNKNOWN(ChatColor.DARK_GRAY + "? Unchecked", ChatColor.DARK_GRAY, "No update channel configured or check failed.");

    private final String badge;
    private final ChatColor color;
    private final String description;

    CompatibilityStatus(String badge, ChatColor color, String description) {
        this.badge = badge;
        this.color = color;
        this.description = description;
    }

    public String getBadge() {
        return badge;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCritical() {
        return this == MUST_UPDATE_CORE || this == MUST_UPDATE_PLUGIN;
    }
}

