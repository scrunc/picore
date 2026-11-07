package org.illustro.picore.audio;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every Simple Voice Chat call PiAudio makes. Only constructed (and so only class-loaded) when the voicechat
 * plugin is present; {@link PiAudioService} delegates here and never touches an SVC type itself.
 */
final class VoiceChatBridge {

    private final JavaPlugin plugin;
    private volatile VoicechatServerApi api;

    VoiceChatBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) throw new IllegalStateException("BukkitVoicechatService not registered");
        service.registerPlugin(new Hook());
        plugin.getLogger().info("[PiAudio] Registered with Simple Voice Chat.");
    }

    boolean available() {
        return api != null;
    }

    boolean hasVoiceChat(UUID player) {
        VoicechatServerApi a = api;
        if (a == null || player == null) return false;
        try {
            VoicechatConnection c = a.getConnectionOf(player);
            return c != null && c.isInstalled();
        } catch (Throwable t) {
            return false;
        }
    }

    boolean isVoiceConnected(UUID player) {
        VoicechatServerApi a = api;
        if (a == null || player == null) return false;
        try {
            VoicechatConnection c = a.getConnectionOf(player);
            return c != null && c.isConnected() && !c.isDisabled();
        } catch (Throwable t) {
            return false;
        }
    }

    AudioHandle startLocational(World world, double x, double y, double z, float distance, AudioMode mode,
                                Supplier<short[]> supplier, Runnable onStopped) {
        VoicechatServerApi a = api;
        if (a == null || world == null) return null;
        try {
            ServerLevel level = a.fromServerLevel(world);
            LocationalAudioChannel channel = a.createLocationalAudioChannel(UUID.randomUUID(), level, a.createPosition(x, y, z));
            if (channel == null) return null;
            channel.setDistance(distance);
            AudioPlayer player = a.createAudioPlayer(channel, encoder(a, mode), supplier);
            if (onStopped != null) player.setOnStopped(onStopped);
            player.startPlaying();
            return new Handle(player, channel);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not start locational audio: " + t);
            return null;
        }
    }

    AudioHandle startStatic(Player listener, AudioMode mode, Supplier<short[]> supplier, Runnable onStopped) {
        VoicechatServerApi a = api;
        if (a == null || listener == null) return null;
        try {
            VoicechatConnection conn = a.getConnectionOf(listener.getUniqueId());
            if (conn == null) return null;   // player hasn't joined the voice session
            ServerLevel level = a.fromServerLevel(listener.getWorld());
            StaticAudioChannel channel = a.createStaticAudioChannel(UUID.randomUUID(), level, conn);
            if (channel == null) return null;
            AudioPlayer player = a.createAudioPlayer(channel, encoder(a, mode), supplier);
            if (onStopped != null) player.setOnStopped(onStopped);
            player.startPlaying();
            return new Handle(player, null);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not start static audio: " + t);
            return null;
        }
    }

    private static de.maxhenkel.voicechat.api.opus.OpusEncoder encoder(VoicechatServerApi a, AudioMode mode) {
        // AUDIO mode preserves the full frequency range (music); the deprecated no-arg createEncoder() is
        // VOIP/speech-tuned and rolls off bass. See AudioMode's javadoc.
        return mode == AudioMode.MUSIC ? a.createEncoder(OpusEncoderMode.AUDIO) : a.createEncoder();
    }

    /** Wraps SVC's AudioPlayer/LocationalAudioChannel so callers stay free of SVC types. */
    private static final class Handle implements AudioHandle {
        private final AudioPlayer player;
        private final LocationalAudioChannel channel;   // null for static (per-player) channels

        private Handle(AudioPlayer player, LocationalAudioChannel channel) {
            this.player = player;
            this.channel = channel;
        }

        @Override
        public void stop() {
            try {
                if (player.isPlaying()) player.stopPlaying();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public boolean playing() {
            try {
                return player.isPlaying();
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void setDistance(float distance) {
            try {
                if (channel != null) channel.setDistance(distance);
            } catch (Throwable ignored) {
            }
        }
    }

    private final class Hook implements VoicechatPlugin {
        @Override
        public String getPluginId() {
            return "picore";
        }

        @Override
        public void initialize(VoicechatApi a) {
            // the server API arrives via the started event below
        }

        @Override
        public void registerEvents(EventRegistration registration) {
            registration.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
        }
    }
}
