package org.illustro.picore.menu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.illustro.picore.utils.PiText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Universal interactive prompt system for capturing text and numeric input from players.
 * Listens to Paper's AsyncChatEvent (with legacy fallback) at LOWEST priority,
 * enforces prompt timeouts, and delivers all responses on the Bukkit primary thread.
 */
public final class PiPrompt {

    private static final Map<UUID, PendingPrompt> PENDING = new ConcurrentHashMap<>();
    private static volatile boolean listenerRegistered = false;

    private PiPrompt() {}

    private static class PendingPrompt {
        final Plugin plugin;
        final String title;
        final Consumer<String> onConfirm;
        final Runnable onCancel;
        final long timeoutMillis;

        PendingPrompt(Plugin plugin, String title, Consumer<String> onConfirm, Runnable onCancel, long timeoutSeconds) {
            this.plugin = plugin;
            this.title = title;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
            this.timeoutMillis = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > timeoutMillis;
        }
    }

    public static synchronized void register(Plugin plugin) {
        if (!listenerRegistered && plugin != null) {
            Bukkit.getPluginManager().registerEvents(new PromptListener(), plugin);
            listenerRegistered = true;
        }
    }

    public static void text(
            Plugin plugin,
            Player player,
            String promptTitle,
            String currentValue,
            Consumer<String> onConfirm,
            Runnable onCancel
    ) {
        if (plugin == null || player == null || !player.isOnline()) return;
        register(plugin);

        if (Bukkit.isPrimaryThread()) {
            PiMenuGuard.suppressNextClose(player);
            player.closeInventory();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                PiMenuGuard.suppressNextClose(player);
                player.closeInventory();
            });
        }

        PENDING.put(player.getUniqueId(), new PendingPrompt(plugin, promptTitle, onConfirm, onCancel, 60));

        PiSound.click(player);
        player.sendMessage(ChatColor.DARK_GRAY + "▪" + ChatColor.STRIKETHROUGH + "----------------------------------------" + ChatColor.DARK_GRAY + "▪");
        player.sendMessage(PiText.component("&e▸ &6&l" + promptTitle));
        if (currentValue != null && !currentValue.isBlank()) {
            player.sendMessage(PiText.component("&7Current value: &f" + currentValue));
        }
        player.sendMessage(ChatColor.GRAY + "Type your new value in chat, or type " + ChatColor.RED + "cancel" + ChatColor.GRAY + " to abort.");
        player.sendMessage(ChatColor.DARK_GRAY + "▪" + ChatColor.STRIKETHROUGH + "----------------------------------------" + ChatColor.DARK_GRAY + "▪");
    }

    public static void number(
            Plugin plugin,
            Player player,
            String promptTitle,
            int currentValue,
            int min,
            int max,
            Consumer<Integer> onConfirm,
            Runnable onCancel
    ) {
        text(plugin, player, promptTitle, currentValue + " [" + min + " - " + max + "]", input -> {
            try {
                int val = Integer.parseInt(input.trim());
                if (val < min || val > max) {
                    player.sendMessage(ChatColor.RED + "Value must be between " + min + " and " + max + ".");
                    PiSound.deny(player);
                    number(plugin, player, promptTitle, currentValue, min, max, onConfirm, onCancel);
                    return;
                }
                PiSound.success(player);
                if (onConfirm != null) onConfirm.accept(val);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number format. Please enter an integer.");
                PiSound.deny(player);
                number(plugin, player, promptTitle, currentValue, min, max, onConfirm, onCancel);
            }
        }, onCancel);
    }

    public static boolean isAwaiting(Player player) {
        if (player == null) return false;
        PendingPrompt p = PENDING.get(player.getUniqueId());
        if (p != null && p.isExpired()) {
            PENDING.remove(player.getUniqueId());
            return false;
        }
        return p != null;
    }

    public static void cancel(Player player) {
        if (player == null) return;
        PendingPrompt p = PENDING.remove(player.getUniqueId());
        if (p != null && p.onCancel != null) {
            if (Bukkit.isPrimaryThread()) {
                p.onCancel.run();
            } else {
                Bukkit.getScheduler().runTask(p.plugin, p.onCancel);
            }
        }
    }

    public static class PromptListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
        public void onPaperChat(AsyncChatEvent e) {
            Player player = e.getPlayer();
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            if (consumePromptInput(player, raw)) {
                e.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onLegacyChat(AsyncPlayerChatEvent e) {
            if (consumePromptInput(e.getPlayer(), e.getMessage())) {
                e.setCancelled(true);
            }
        }

        private boolean consumePromptInput(Player player, String rawMessage) {
            PendingPrompt prompt = PENDING.remove(player.getUniqueId());
            if (prompt == null) return false;

            if (prompt.isExpired()) {
                if (prompt.onCancel != null) {
                    Bukkit.getScheduler().runTask(prompt.plugin, prompt.onCancel);
                }
                return false;
            }

            String msg = rawMessage != null ? rawMessage.trim() : "";
            Bukkit.getScheduler().runTask(prompt.plugin, () -> {
                if (msg.equalsIgnoreCase("cancel")) {
                    PiSound.deny(player);
                    player.sendMessage(ChatColor.YELLOW + "Input prompt cancelled.");
                    if (prompt.onCancel != null) {
                        prompt.onCancel.run();
                    }
                    return;
                }
                if (prompt.onConfirm != null) {
                    prompt.onConfirm.accept(msg);
                }
            });
            return true;
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            PENDING.remove(e.getPlayer().getUniqueId());
        }
    }
}
