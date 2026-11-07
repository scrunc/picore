package org.illustro.picore.audio;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Publishes {@link PiAudio} and {@link MediaTools} as Bukkit services on behalf of PiCore. Holds no Simple
 * Voice Chat types itself: every SVC call goes through {@link VoiceChatBridge}, which is only constructed
 * when the voicechat plugin is installed — so PiCore loads cleanly on servers without it.
 */
public final class PiAudioService implements PiAudio {

    private final JavaPlugin plugin;
    private final MediaTools media;
    private VoiceChatBridge bridge;   // null when Simple Voice Chat is absent

    public PiAudioService(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("[PiAudio] Simple Voice Chat not installed — audio consumers will be silent.");
        } else {
            try {
                bridge = new VoiceChatBridge(plugin);
            } catch (Throwable t) {
                plugin.getLogger().warning("[PiAudio] Could not hook Simple Voice Chat: " + t);
                bridge = null;
            }
        }
        plugin.getServer().getServicesManager().register(PiAudio.class, this, plugin, ServicePriority.Normal);

        // The shared ffmpeg/yt-dlp pair, so one binary serves every plugin that ingests audio.
        this.media = new MediaToolsImpl(plugin);
        plugin.getServer().getServicesManager().register(MediaTools.class, media, plugin, ServicePriority.Normal);
        if (media.hasFfmpeg()) plugin.getLogger().info("[PiAudio] Media tools: ffmpeg " + (media.hasYtdlp() ? "+ yt-dlp " : "") + "at " + media.binDir().getPath() + ".");
    }

    public MediaTools media() {
        return media;
    }

    public void shutdown() {
        plugin.getServer().getServicesManager().unregister(PiAudio.class, this);
        plugin.getServer().getServicesManager().unregister(MediaTools.class, media);
    }

    @Override
    public boolean available() {
        return bridge != null && bridge.available();
    }

    @Override
    public boolean hasVoiceChat(UUID player) {
        return bridge != null && bridge.hasVoiceChat(player);
    }

    @Override
    public boolean isVoiceConnected(UUID player) {
        return bridge != null && bridge.isVoiceConnected(player);
    }

    @Override
    public AudioHandle startLocational(World world, double x, double y, double z, float distance, AudioMode mode,
                                        Supplier<short[]> supplier, Runnable onStopped) {
        return bridge == null ? null : bridge.startLocational(world, x, y, z, distance, mode, supplier, onStopped);
    }

    @Override
    public AudioHandle startStatic(Player listener, AudioMode mode, Supplier<short[]> supplier, Runnable onStopped) {
        return bridge == null ? null : bridge.startStatic(listener, mode, supplier, onStopped);
    }
}
