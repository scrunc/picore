package org.illustro.picore.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, non-blocking Player Head builder for PiCore menus.
 * Caches Paper PlayerProfiles in memory to prevent synchronous Mojang session-server
 * lookups from stalling the main server thread during GUI rendering.
 * Also supports custom Base64 textures and direct texture URLs with deterministic UUIDs.
 */
public final class PiSkull {

    private static final Map<UUID, PlayerProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

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
     * Applies skin textures to a SkullMeta by UUID string.
     */
    public static void apply(SkullMeta meta, String uuidStr, String playerName) {
        UUID uid = null;
        try {
            if (uuidStr != null && !uuidStr.isBlank()) uid = UUID.fromString(uuidStr.trim());
        } catch (Exception ignored) { }
        apply(meta, uid, playerName);
    }

    /**
     * Creates an ItemStack of type PLAYER_HEAD configured for the target UUID.
     */
    public static ItemStack create(UUID uuid, String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (uuid == null) return head;

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

    /**
     * Creates a custom player head from a texture reference:
     * can be a skin URL (e.g. http://textures.minecraft.net/texture/...),
     * a Base64 encoded textures string, or a player name.
     * Uses deterministic UUIDs derived from the URL so identical heads stack cleanly.
     */
    public static ItemStack fromTexture(String textureValue) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (textureValue == null || textureValue.isBlank()) return head;
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        try {
            if (textureValue.startsWith("http")) {
                applyTextureUrl(meta, textureValue);
            } else if (looksLikeBase64(textureValue)) {
                String json = new String(Base64.getDecoder().decode(textureValue));
                Matcher m = URL_IN_JSON.matcher(json);
                if (m.find()) {
                    applyTextureUrl(meta, m.group(1));
                }
            } else {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(textureValue));
            }
        } catch (Exception ignored) { }

        head.setItemMeta(meta);
        return head;
    }

    public static ItemStack fromTexture(String textureValue, String displayName, String... lore) {
        ItemStack head = fromTexture(textureValue);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            if (lore != null && lore.length > 0) {
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

    public static PiButton buttonFromTexture(String textureValue, String displayName, String... lore) {
        return PiButton.create(fromTexture(textureValue, displayName, lore));
    }

    private static void applyTextureUrl(SkullMeta meta, String url) throws Exception {
        UUID deterministicId = UUID.nameUUIDFromBytes(("picore-head:" + url).getBytes(StandardCharsets.UTF_8));
        org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(deterministicId);
        PlayerTextures tex = profile.getTextures();
        tex.setSkin(URI.create(url).toURL());
        profile.setTextures(tex);
        meta.setOwnerProfile(profile);
    }

    private static boolean looksLikeBase64(String s) {
        if (s == null || s.length() < 40) return false;
        return s.matches("[A-Za-z0-9+/=]+");
    }
}
