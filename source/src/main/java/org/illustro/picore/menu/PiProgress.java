package org.illustro.picore.menu;

import org.bukkit.ChatColor;

/**
 * Utility for rendering standardized visual progress bars and percentage indicators across menus.
 */
public final class PiProgress {

    private PiProgress() {}

    /**
     * Renders a colored ASCII progress bar.
     * E.g. [||||||||||..........] with custom colors.
     *
     * @param current current value
     * @param max maximum value
     * @param totalBars number of character blocks to render
     * @param completedColor color code for completed portion (e.g. "&a")
     * @param remainingColor color code for uncompleted portion (e.g. "&8")
     * @param symbol character symbol to use (e.g. "|", "■", ":")
     * @return colorized string
     */
    public static String bar(double current, double max, int totalBars, String completedColor, String remainingColor, String symbol) {
        if (max <= 0) max = 1;
        double ratio = Math.max(0.0, Math.min(1.0, current / max));
        int completedBars = (int) Math.round(ratio * totalBars);
        int remainingBars = Math.max(0, totalBars - completedBars);

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.translateAlternateColorCodes('&', completedColor));
        for (int i = 0; i < completedBars; i++) sb.append(symbol);
        sb.append(ChatColor.translateAlternateColorCodes('&', remainingColor));
        for (int i = 0; i < remainingBars; i++) sb.append(symbol);
        return sb.toString();
    }

    public static String bar(double current, double max, int totalBars) {
        return bar(current, max, totalBars, "&a", "&8", "|");
    }

    public static String percent(double current, double max) {
        if (max <= 0) return "100.0%";
        double p = Math.max(0.0, Math.min(100.0, (current / max) * 100.0));
        return String.format(java.util.Locale.US, "%.1f%%", p);
    }
}
