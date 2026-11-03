package org.illustro.picore;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.illustro.picore.menu.PiMenuGuard;

/**
 * PiCore - Central Core & Unified Framework.
 * Powers menus, security guards, and shared services across all BMS custom plugins:
 * - PixelProfiler
 * - Koth
 * - MachineConstruct
 * - MorphicGames
 * - MorphicTrials
 * - PixelUtilities
 */
public class PiCore extends JavaPlugin implements CommandExecutor {

    private static PiCore instance;

    public static PiCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Register Server-wide 3-Tier Menu Security Guard
        getServer().getPluginManager().registerEvents(new PiMenuGuard(), this);

        if (getCommand("picore") != null) {
            getCommand("picore").setExecutor(this);
        }

        getLogger().info("=========================================");
        getLogger().info("       PiCore v1.0.0 (Unified Spine)     ");
        getLogger().info("   Universal Menu Guard & Spine Active   ");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("PiCore disabled.");
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.DARK_GRAY + "«" + ChatColor.AQUA + ChatColor.BOLD + "PiCore" + ChatColor.DARK_GRAY + "» "
                + ChatColor.GREEN + "v1.0.0 is active and running.");
        sender.sendMessage(ChatColor.GRAY + "Serving unified menu architecture and anti-theft guards.");
        return true;
    }
}
