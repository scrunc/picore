package org.illustro.picore.dynvar;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.illustro.picore.PiCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI expansion for PiCore Dynamic Variables and Ladders.
 *
 * Supported placeholders:
 * - %picore_s_<name>%               -> Static Global variable
 * - %picore_p_<name>%               -> Static Player variable
 * - %picore_l_<ladder>_<row>_<col>% -> Ladder column value (supports {bracket} placeholders for row/col)
 * - %picore_lf_<ladder>_<score>_<col>% -> Floor / Threshold numeric ladder lookup
 * - %picore_math:<expression>%      -> Mathematical arithmetic evaluation (supports {bracket} and %percent% placeholders)
 */
public class DynVarExpansion extends PlaceholderExpansion {

    private final PiCore plugin;
    private final DynVarManager manager;

    public DynVarExpansion(PiCore plugin, DynVarManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "picore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "illustro";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (params.isBlank()) return "";

        // Math Expression: %picore_math:<expr>%
        if (params.regionMatches(true, 0, "math:", 0, 5)) {
            String expr = params.substring(5).trim();
            expr = PlaceholderAPI.setBracketPlaceholders(offlinePlayer, expr);
            expr = PlaceholderAPI.setPlaceholders(offlinePlayer, expr);
            return MathEvaluator.eval(expr);
        }

        // Template / Lore Block: %picore_t_<template>%
        if (params.regionMatches(true, 0, "t_", 0, 2)) {
            String name = params.substring(2).trim();
            return manager.getTemplateString(name);
        }

        // Static Global: %picore_s_<name>%
        if (params.regionMatches(true, 0, "s_", 0, 2)) {
            String name = params.substring(2).trim().toLowerCase(Locale.ROOT);
            String val = manager.getStaticValue(name);
            return val != null ? val : "";
        }

        // Static Player: %picore_p_<name>%
        if (params.regionMatches(true, 0, "p_", 0, 2)) {
            if (offlinePlayer == null) return "";
            String name = params.substring(2).trim().toLowerCase(Locale.ROOT);
            return manager.getPlayerValue(offlinePlayer.getUniqueId(), name);
        }

        // Floor / Threshold Ladder Lookup: %picore_lf_<ladder>_<score>_<col>%
        if (params.regionMatches(true, 0, "lf_", 0, 3)) {
            String rest = params.substring(3).trim();
            if (rest.indexOf('{') >= 0) {
                rest = PlaceholderAPI.setBracketPlaceholders(offlinePlayer, rest);
            }
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore <= 0) return "";
            int secondLastUnderscore = rest.lastIndexOf('_', lastUnderscore - 1);
            if (secondLastUnderscore <= 0) return "";

            String ladderName = rest.substring(0, secondLastUnderscore).trim().toLowerCase(Locale.ROOT);
            String scoreStr = rest.substring(secondLastUnderscore + 1, lastUnderscore).trim();
            String colStr = rest.substring(lastUnderscore + 1).trim();

            try {
                double score = Double.parseDouble(scoreStr);
                int col = Integer.parseInt(colStr);
                Ladder ladder = manager.getLadder(ladderName);
                if (ladder == null) return "";
                Object val = ladder.getFloor(score, col);
                return val != null ? String.valueOf(val) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }

        // Ladder: %picore_l_<ladder>_<row>_<col>%
        if (params.regionMatches(true, 0, "l_", 0, 2)) {
            String rest = params.substring(2).trim();
            if (rest.indexOf('{') >= 0) {
                rest = PlaceholderAPI.setBracketPlaceholders(offlinePlayer, rest);
            }
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore <= 0) return "";
            int secondLastUnderscore = rest.lastIndexOf('_', lastUnderscore - 1);
            if (secondLastUnderscore <= 0) return "";

            String ladderName = rest.substring(0, secondLastUnderscore).trim().toLowerCase(Locale.ROOT);
            String rowStr = rest.substring(secondLastUnderscore + 1, lastUnderscore).trim();
            String colStr = rest.substring(lastUnderscore + 1).trim();

            try {
                int row = Integer.parseInt(rowStr);
                int col = Integer.parseInt(colStr);
                return manager.getLadderValue(ladderName, row, col);
            } catch (NumberFormatException e) {
                return "";
            }
        }

        return null;
    }
}
