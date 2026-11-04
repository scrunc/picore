package org.illustro.picore.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Universal interactive prompt system for capturing text and numeric input from players.
 * Safely cancels chat events at LOWEST priority to avoid leaking input to public chat,
 * and delivers all responses on the Bukkit primary thread.
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
    }

    /**
     * Initializes the chat listener. Can be called explicitly during onEnable,
     * or is automatically registered on first prompt call.
     */
    public static synchronized void register(Plugin plugin) {
        if (!listenerRegistered && plugin != null) {
            Bukkit.getPluginManager().registerEvents(new PromptListener(), plugin);
            listenerRegistered = true;
        }
    }

    /**
     * Prompts the player for string input.
     *
     * @param plugin hosting plugin
     * @param player player to prompt
     * @param promptTitle displayed title / label
     * @param currentValue optional current value to show (null to omit)
     * @param onConfirm callback receiving the entered string on the Bukkit main thread
     * @param onCancel callback executed if cancelled or timed out
     */
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

        // Close any currently open GUI
        if (Bukkit.isPrimaryThread()) {
            player.closeInventory();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }

        PENDING.put(player.getUniqueId(), new PendingPrompt(plugin, promptTitle, onConfirm, onCancel, 60));

        PiSound.click(player);
        player.sendMessage(ChatColor.DARK_GRAY + "«" + ChatColor.STRIKETHROUGH + "----------------------------------------" + ChatColor.DARK_GRAY + "»");
        player.sendMessage(ChatColor.YELLOW + "✎ " + ChatColor.GOLD + ChatColor.BOLD + ChatColor.translateAlternateColorCodes('&', promptTitle));
        if (currentValue != null && !currentValue.isBlank()) {
            player.sendMessage(ChatColor.GRAY + "Current value: " + ChatColor.WHITE + currentValue);
        }
        player.sendMessage(ChatColor.GRAY + "Type your new value in chat, or type " + ChatColor.RED + "cancel" + ChatColor.GRAY + " to abort.");
        player.sendMessage(ChatColor.DARK_GRAY + "«" + ChatColor.STRIKETHROUGH + "----------------------------------------" + ChatColor.DARK_GRAY + "»");
    }

    /**
     * Prompts the player for an integer within a bounds range.
     * Reprompts automatically if the input is not a valid integer within [min, max].
     */
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
        text(plugin, player, promptTitle, String.valueOf(currentValue) + " [" + min + " - " + max + "]", input -> {
            try {
                int val = Integer.parseInt(input.trim());
                if (val < min || val > max) {
                    player.sendMessage(ChatColor.RED + "Value must be between " + min + " and " + max + ".");
                    PiSound.deny(player);
                    // Re-prompt
                    number(plugin, player, promptTitle, currentValue, min, max, onConfirm, onCancel);
                    return;
                }
                PiSound.success(player);
                if (onConfirm != null) onConfirm.accept(val);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number format. Please enter an integer.");
                PiSound.deny(player);
                // Re-prompt
                number(plugin, player, promptTitle, currentValue, min, max, onConfirm, onCancel);
            }
        }, onCancel);
    }

    public static boolean isAwaiting(Player player) {
        return player != null && PENDING.containsKey(player.getUniqueId());
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

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent e) {
            Player player = e.getPlayer();
            PendingPrompt prompt = PENDING.get(player.getUniqueId());
            if (prompt == null) return;

            e.setCancelled(true);
            PENDING.remove(player.getUniqueId());

            String msg = e.getMessage().trim();

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
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            PENDING.remove(e.getPlayer().getUniqueId());
        }
    }
}
