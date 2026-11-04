package org.illustro.picore.menu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Standardized audio feedback suite for PiCore menus.
 * Ensures consistent auditory cues (clicks, toggles, success, errors) across all BMS plugins.
 */
public final class PiSound {

    private PiSound() { }

    /** Standard button click navigation sound. */
    public static void click(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
    }

    /** Success / affirmative chime (purchase completed, item restored, setting saved). */
    public static void success(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /** Deny / error buzzer (lacking permission, insufficient funds, illegal action). */
    public static void deny(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
    }

    /** Toggle flip click (switching a boolean filter ON/OFF). */
    public static void flip(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.2f);
    }

    /** Stepper adjustment tick (incrementing / decrementing a number). */
    public static void step(Player player, boolean increment) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, increment ? 1.2f : 0.8f);
    }

    /** Page turn flutter (navigating previous / next in paginated menus). */
    public static void page(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
    }

    /** Menu opening sound. */
    public static void open(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    /** Menu closing sound. */
    public static void close(Player player) {
        if (player == null) return;
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.2f);
    }
}
