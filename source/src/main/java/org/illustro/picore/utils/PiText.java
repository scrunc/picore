package org.illustro.picore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal text, Adventure Component, MiniMessage, and legacy color utility for PiCore menus.
 * Supports legacy '&' color codes, modern '&#RRGGBB' / '<#RRGGBB>' hex codes, full MiniMessage,
 * smart lore word-wrapping, and color stripping with safe fallback on malformed tags.
 */
public final class PiText {

    private static final Pattern HEX_PATTERN_AMP = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PATTERN_BRACKET = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile(
            "§x§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])"
    );
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private PiText() {}

    /**
     * Parses a string into an Adventure Component supporting both MiniMessage tags
     * and legacy '&' / '§' / '&#RRGGBB' formatting, with default item italics disabled.
     * Falls back safely to LegacyComponentSerializer if MiniMessage encounters malformed input.
     */
    public static Component component(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        try {
            String converted = legacyToMiniMessage(message);
            return MM.deserialize(converted)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        } catch (Exception ignored) {
            return LEGACY_SERIALIZER.deserialize(color(message))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
    }

    /**
     * Parses a list of strings into Adventure Components with default item italics disabled.
     */
    public static List<Component> components(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(component(line));
        }
        return out;
    }

    /**
     * Converts legacy '&' / '§' codes and '&#RRGGBB' hex codes into MiniMessage tags.
     */
    public static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return "";

        Matcher bungeeHex = BUNGEE_HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (bungeeHex.find()) {
            String hex = bungeeHex.group(1) + bungeeHex.group(2) + bungeeHex.group(3)
                    + bungeeHex.group(4) + bungeeHex.group(5) + bungeeHex.group(6);
            bungeeHex.appendReplacement(sb, "<#" + hex + ">");
        }
        bungeeHex.appendTail(sb);

        Matcher ampHex = HEX_PATTERN_AMP.matcher(sb.toString());
        sb = new StringBuffer();
        while (ampHex.find()) {
            ampHex.appendReplacement(sb, "<#" + ampHex.group(1) + ">");
        }
        ampHex.appendTail(sb);

        Matcher legacy = LEGACY_CODE_PATTERN.matcher(sb.toString());
        sb = new StringBuffer();
        while (legacy.find()) {
            char code = Character.toLowerCase(legacy.group(1).charAt(0));
            String replacement = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> legacy.group(0);
            };
            legacy.appendReplacement(sb, replacement);
        }
        legacy.appendTail(sb);
        return sb.toString().replace('§', '&');
    }

    /**
     * Colorizes a string into legacy section-coded text (backward-compatible).
     */
    public static String color(String message) {
        if (message == null || message.isEmpty()) return "";

        Matcher matcherAmp = HEX_PATTERN_AMP.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcherAmp.find()) {
            matcherAmp.appendReplacement(buffer, ChatColor.of("#" + matcherAmp.group(1)).toString());
        }
        matcherAmp.appendTail(buffer);
        String s = buffer.toString();

        Matcher matcherBracket = HEX_PATTERN_BRACKET.matcher(s);
        buffer = new StringBuffer();
        while (matcherBracket.find()) {
            matcherBracket.appendReplacement(buffer, ChatColor.of("#" + matcherBracket.group(1)).toString());
        }
        matcherBracket.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> color(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<Component> components = components(lines);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    public static String strip(String message) {
        if (message == null || message.isEmpty()) return "";
        return PlainTextComponentSerializer.plainText().serialize(component(message));
    }

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
