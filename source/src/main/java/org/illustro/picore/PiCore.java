package org.illustro.picore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.illustro.picore.menu.PiMenuGuard;
import org.illustro.picore.updater.ClientRegistration;
import org.illustro.picore.updater.PiCoreUpdater;
import org.illustro.picore.updater.UpdateRegistry;
import org.illustro.picore.updater.gui.PiCoreUpdateGUI;

/**
 * PiCore - Central Core & Unified Framework.
 * Powers menus, security guards, update coordination, and shared services across BMS plugins.
 */
public class PiCore extends JavaPlugin implements CommandExecutor {

    private static PiCore instance;

    private UpdateRegistry updateRegistry;
    private PiCoreUpdater updater;

    public static PiCore getInstance() {
        return instance;
    }

    public UpdateRegistry getUpdateRegistry() {
        return updateRegistry;
    }

    public PiCoreUpdater getUpdater() {
        return updater;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // 1. Initialize Updater and Registry
        this.updateRegistry = UpdateRegistry.getInstance();
        this.updater = new PiCoreUpdater(this);

        // 2. Register Server-wide 3-Tier Menu Security Guard
        getServer().getPluginManager().registerEvents(new PiMenuGuard(), this);

        // 3. Register Commands
        if (getCommand("picore") != null) {
            getCommand("picore").setExecutor(this);
        }

        // 4. Initial async release & compatibility scan after server warms up (10s)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updater.checkAllAsync(null);
        }, 200L);

        // 5. Alert administrators on join if updates are available
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                Bukkit.getScheduler().runTaskLater(PiCore.this, () -> {
                    if (e.getPlayer().isOnline()) {
                        updater.alertAdminIfOutdated(e.getPlayer());
                    }
                }, 40L);
            }
        }, this);

        getLogger().info("=========================================");
        getLogger().info("       PiCore v" + getDescription().getVersion() + " (Unified Spine)     ");
        getLogger().info("   Menu Guard, Auto-Updater & Health OK  ");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("PiCore disabled.");
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendCompatibilityStatus(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "gui":
            case "menu":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "GUI can only be opened by players. Use /picore status from console.");
                    return true;
                }
                PiCoreUpdateGUI.open((Player) sender);
                return true;

            case "check":
                sender.sendMessage(ChatColor.AQUA + "Initiating async GitHub check for PiCore and registered modules...");
                updater.checkAllAsync(ok -> {
                    sender.sendMessage(ChatColor.GREEN + "Scan complete! Showing updated status:");
                    sendCompatibilityStatus(sender);
                });
                return true;

            case "update":
                if (!updater.isCoreUpdateAvailable()) {
                    sender.sendMessage(ChatColor.GREEN + "PiCore is already on the latest version (v" + getDescription().getVersion() + ").");
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + "Staging PiCore update to /plugins/update/...");
                updater.stageCoreUpdateAsync(ok -> {
                    if (ok) {
                        sender.sendMessage(ChatColor.GREEN + "✔ PiCore v" + updater.getLatestCoreVersion() + " staged! It will apply on next restart.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "✖ Failed to stage PiCore update. Check server logs.");
                    }
                });
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown argument. Usage: /picore [status | gui | check | update]");
                return true;
        }
    }

    private void sendCompatibilityStatus(CommandSender sender) {
        String coreVer = getDescription().getVersion();
        boolean coreUpdate = updater.isCoreUpdateAvailable();

        sender.sendMessage(ChatColor.DARK_GRAY + "«" + ChatColor.AQUA + ChatColor.BOLD + "PiCore Ecosystem Health" + ChatColor.DARK_GRAY + "»");
        sender.sendMessage(ChatColor.GRAY + "• Core: " + ChatColor.AQUA + "PiCore " + ChatColor.WHITE + "v" + coreVer
                + " " + (coreUpdate ? ChatColor.GOLD + "[UPDATE AVAILABLE: v" + updater.getLatestCoreVersion() + "]" : ChatColor.GREEN + "[UP TO DATE]"));

        int clientCount = updateRegistry.getClientCount();
        sender.sendMessage(ChatColor.GRAY + "• Connected Modules (" + ChatColor.YELLOW + clientCount + ChatColor.GRAY + "):");

        if (clientCount == 0) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  (No downstream plugins registered yet)");
        } else {
            for (ClientRegistration c : updateRegistry.getAllClients()) {
                String latest = (c.getLatestReleaseVersion() != null) ? ChatColor.GRAY + " (Latest: " + ChatColor.GOLD + "v" + c.getLatestReleaseVersion() + ChatColor.GRAY + ")" : "";
                String req = ChatColor.GRAY + "(Req Core: " + ChatColor.AQUA + "v" + c.getMinCoreVersion() + ChatColor.GRAY + ")";
                sender.sendMessage(ChatColor.DARK_GRAY + "  • " + ChatColor.YELLOW + c.getClientName()
                        + ChatColor.WHITE + " v" + c.getInstalledVersion() + latest + " " + req + " ➔ " + c.getStatus().getBadge());
            }
        }

        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/picore gui" + ChatColor.GRAY + " for the interactive visual dashboard.");
        }
    }
}
