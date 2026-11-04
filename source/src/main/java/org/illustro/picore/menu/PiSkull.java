package org.illustro.picore.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, non-blocking Player Head builder for PiCore menus.
 * Caches Paper PlayerProfiles in memory to prevent synchronous Mojang session-server
 * lookups from stalling the main server thread during GUI rendering.
 */
public final class PiSkull {

    private static final Map<UUID, PlayerProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

    private PiSkull() { }

    /**
     * Resolves or caches a Paper PlayerProfile without blocking network I/O.
     */
    public static PlayerProfile getOrCreateProfile(UUID uuid, String playerName) {
        if (uuid == null) return null;
        return PROFILE_CACHE.computeIfAbsent(uuid, id -> {
            try {
                return Bukkit.createProfile(id, playerName);
            } catch (Throwable t) {
                return null;
            }
        });
    }

    /**
     * Applies skin textures to a SkullMeta safely.
     * Prefers Paper's non-blocking PlayerProfile API, with graceful fallback to OfflinePlayer.
     */
    public static void apply(SkullMeta meta, UUID uuid, String playerName) {
        if (meta == null || uuid == null) return;
        try {
            PlayerProfile profile = getOrCreateProfile(uuid, playerName);
            if (profile != null) {
                meta.setPlayerProfile(profile);
                return;
            }
        } catch (Throwable ignored) { }

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
        } catch (Exception ignored) { }
    }

    /**
     * Applies skin textures by UUID string.
     */
    public static void apply(SkullMeta meta, String uuidStr, String playerName) {
        if (uuidStr == null || uuidStr.isBlank()) return;
        try {
            apply(meta, UUID.fromString(uuidStr.trim()), playerName);
        } catch (Exception ignored) { }
    }

    /**
     * Creates a raw player head with the skin applied.
     */
    public static ItemStack create(UUID uuid, String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            apply(meta, uuid, playerName);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Creates a raw player head by UUID string.
     */
    public static ItemStack create(String uuidStr, String playerName) {
        if (uuidStr == null || uuidStr.isBlank()) return new ItemStack(Material.PLAYER_HEAD);
        try {
            return create(UUID.fromString(uuidStr.trim()), playerName);
        } catch (Exception e) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /**
     * Creates a styled player head with display name and lore.
     */
    public static ItemStack create(UUID uuid, String playerName, String displayName, List<String> lore) {
        ItemStack head = create(uuid, playerName);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> list = new ArrayList<>();
                for (String line : lore) {
                    list.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(list);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Creates a styled player head with display name and vararg lore.
     */
    public static ItemStack create(UUID uuid, String playerName, String displayName, String... lore) {
        return create(uuid, playerName, displayName, lore != null ? List.of(lore) : List.of());
    }

    /**
     * Creates an interactive PiButton with the player head icon.
     */
    public static PiButton button(UUID uuid, String playerName, String displayName, String... lore) {
        return PiButton.create(create(uuid, playerName, displayName, lore));
    }

    /**
     * Creates an interactive PiButton by UUID string.
     */
    public static PiButton button(String uuidStr, String playerName, String displayName, String... lore) {
        UUID uid = null;
        try {
            if (uuidStr != null && !uuidStr.isBlank()) uid = UUID.fromString(uuidStr.trim());
        } catch (Exception ignored) { }
        return button(uid, playerName, displayName, lore);
    }
}
