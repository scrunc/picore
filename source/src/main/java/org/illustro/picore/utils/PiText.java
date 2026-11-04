package org.illustro.picore.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal text, color, and formatting utility for PiCore menus.
 * Supports legacy '&' color codes, modern '&#RRGGBB' / '<#RRGGBB>' hex codes,
 * smart lore word-wrapping, and color stripping.
 */
public final class PiText {

    private static final Pattern HEX_PATTERN_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PATTERN_BRACKET = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private PiText() {}

    /**
     * Colorizes a string supporting both legacy '&' codes and modern hex codes (&#RRGGBB or <#RRGGBB>).
     */
    public static String color(String message) {
        if (message == null || message.isEmpty()) return "";

        // 1. Replace &#RRGGBB
        Matcher matcherAmp = HEX_PATTERN_AMP.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcherAmp.find()) {
            matcherAmp.appendReplacement(buffer, ChatColor.of("#" + matcherAmp.group(1)).toString());
        }
        matcherAmp.appendTail(buffer);
        String s = buffer.toString();

        // 2. Replace <#RRGGBB>
        Matcher matcherBracket = HEX_PATTERN_BRACKET.matcher(s);
        buffer = new StringBuffer();
        while (matcherBracket.find()) {
            matcherBracket.appendReplacement(buffer, ChatColor.of("#" + matcherBracket.group(1)).toString());
        }
        matcherBracket.appendTail(buffer);

        // 3. Translate legacy '&' codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Colorizes a list of lines.
     */
    public static List<String> color(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    /**
     * Strips all Minecraft and hex color formatting from a string.
     */
    public static String strip(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(color(message));
    }

    /**
     * Word-wraps a long description into formatted lore lines without breaking words.
     * Automatically carries active color codes over to subsequent lines.
     *
     * @param text raw description
     * @param maxLineLength maximum characters per line (e.g. 35)
     * @return list of colorized lore lines
     */
    public static List<String> wrap(String text, int maxLineLength) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        String colored = color(text);
        List<String> lines = new ArrayList<>();

        String[] words = colored.split(" ");
        StringBuilder currentLine = new StringBuilder();
        String lastColors = "";

        for (String word : words) {
            String strippedLine = ChatColor.stripColor(currentLine.toString());
            String strippedWord = ChatColor.stripColor(word);

            if (strippedLine.length() + strippedWord.length() + 1 > maxLineLength && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                lastColors = org.bukkit.ChatColor.getLastColors(currentLine.toString());
                currentLine = new StringBuilder();
                if (!lastColors.isEmpty()) {
                    currentLine.append(lastColors);
                }
            }

            if (currentLine.length() > 0 && !currentLine.toString().endsWith(lastColors)) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
