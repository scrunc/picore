package org.illustro.picore.updater.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.illustro.picore.PiCore;
import org.illustro.picore.menu.PiButton;
import org.illustro.picore.menu.PiMenu;
import org.illustro.picore.updater.ClientRegistration;
import org.illustro.picore.updater.CompatibilityStatus;
import org.illustro.picore.updater.PiCoreUpdater;
import org.illustro.picore.updater.UpdateRegistry;
import org.illustro.picore.utils.ItemBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive In-Game Ecosystem & Update Status GUI.
 */
public class PiCoreUpdateGUI {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void open(Player player) {
        PiCore core = PiCore.getInstance();
        if (core == null)
            return;

        PiCoreUpdater updater = core.getUpdater();
        UpdateRegistry registry = core.getUpdateRegistry();

        ItemStack border = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        PiMenu menu = PiMenu.create(ChatColor.DARK_GRAY + "PiCore ➔ " + ChatColor.AQUA + "Ecosystem Health", 6)
                .fillBorder(border);

        // Slot 4: PiCore Framework Beacon
        boolean coreUpdate = updater.isCoreUpdateAvailable();
        Material coreMat = coreUpdate ? Material.NETHER_STAR : Material.BEACON;
        String coreTitle = ChatColor.AQUA + "" + ChatColor.BOLD + "PiCore Framework "
                + (coreUpdate ? ChatColor.GOLD + "(Update Available!)" : ChatColor.GREEN + "(Up to Date)");

        String lastCheck = (updater.getLastCheckedTime() > 0)
                ? DATE_FORMAT.format(Instant.ofEpochMilli(updater.getLastCheckedTime()))
                : "Not checked yet";

        ItemStack coreItem = ItemBuilder.of(coreMat)
                .name(coreTitle)
                .lore(
                        ChatColor.DARK_GRAY + "Core Backbone Architecture",
                        "",
                        ChatColor.GRAY + "• Installed:  " + ChatColor.WHITE + "v" + core.getDescription().getVersion(),
                        ChatColor.GRAY + "• Latest:     " + (coreUpdate ? ChatColor.GOLD : ChatColor.GREEN) + "v"
                                + updater.getLatestCoreVersion(),
                        ChatColor.GRAY + "• Last Check: " + ChatColor.DARK_AQUA + lastCheck,
                        "",
                        ChatColor.YELLOW + "Click to refresh release data.")
                .glow(coreUpdate)
                .build();

        PiButton coreBtn = PiButton.create(coreItem)
                .sound(Sound.UI_BUTTON_CLICK)
                .onClick((p, e) -> {
                    p.sendMessage(ChatColor.AQUA + "Querying GitHub for latest releases...");
                    updater.checkAllAsync(success -> {
                        if (p.isOnline()) {
                            open(p);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        }
                    });
                });
        menu.button(4, coreBtn);

        // Populate registered client plugins
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        int slotIdx = 0;
        for (ClientRegistration client : registry.getAllClients()) {
            if (slotIdx >= slots.length)
                break;

            Material mat;
            CompatibilityStatus st = client.getStatus();
            if (st == CompatibilityStatus.UP_TO_DATE) {
                mat = Material.EMERALD;
            } else if (st == CompatibilityStatus.UPDATE_AVAILABLE) {
                mat = Material.GOLD_INGOT;
            } else if (st == CompatibilityStatus.MUST_UPDATE_CORE || st == CompatibilityStatus.MUST_UPDATE_PLUGIN) {
                mat = Material.REDSTONE_BLOCK;
            } else {
                mat = Material.BOOK;
            }

            String latest = client.getLatestReleaseVersion() != null ? "v" + client.getLatestReleaseVersion()
                    : "Unknown";

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Registered Module");
            lore.add("");
            lore.add(ChatColor.GRAY + "Status:       " + client.getStatus().getBadge());
            lore.add(ChatColor.GRAY + "Installed:    " + ChatColor.WHITE + "v" + client.getInstalledVersion());
            lore.add(ChatColor.GRAY + "Req Core:     " + ChatColor.AQUA + "v" + client.getMinCoreVersion());
            lore.add(ChatColor.GRAY + "Latest Avail: " + ChatColor.YELLOW + latest);

            if (client.getLatestReleaseNotes() != null && !client.getLatestReleaseNotes().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "Release Notes:");
                for (String line : wrapText(client.getLatestReleaseNotes(), 42)) {
                    lore.add(ChatColor.GRAY + "• " + line);
                }
            }

            if (client.getStatus() == CompatibilityStatus.UPDATE_AVAILABLE || client.getStatus().isCritical()) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Download Notice:");
                for (String line : wrapText(client.getDownloadNotice(), 42)) {
                    lore.add(ChatColor.WHITE + line);
                }
            }

            lore.add("");
            lore.add(ChatColor.ITALIC + "" + ChatColor.DARK_GRAY + client.getStatus().getDescription());

            ItemStack clientItem = ItemBuilder.of(mat)
                    .name(ChatColor.GOLD + "" + ChatColor.BOLD + client.getClientName())
                    .lore(lore)
                    .glow(client.getStatus().isCritical() || client.getStatus() == CompatibilityStatus.UPDATE_AVAILABLE)
                    .build();

            PiButton clientBtn = PiButton.create(clientItem)
                    .sound(Sound.UI_BUTTON_CLICK);

            menu.button(slots[slotIdx++], clientBtn);
        }

        // Slot 47: Check Updates Button
        ItemStack checkItem = ItemBuilder.of(Material.CLOCK)
                .name(ChatColor.YELLOW + "" + ChatColor.BOLD + "Check Updates")
                .lore(
                        ChatColor.GRAY + "Query GitHub releases for PiCore",
                        ChatColor.GRAY + "and all registered downstream plugins.",
                        "",
                        ChatColor.AQUA + "Click to execute scan.")
                .build();

        PiButton checkBtn = PiButton.create(checkItem)
                .sound(Sound.ITEM_ARMOR_EQUIP_GENERIC)
                .onClick((p, e) -> {
                    p.sendMessage(ChatColor.AQUA + "Checking for updates across GitHub...");
                    updater.checkAllAsync(ok -> {
                        if (p.isOnline()) {
                            open(p);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
                        }
                    });
                });
        menu.button(47, checkBtn);

        // Slot 51: Stage Update Button (if update is available)
        if (coreUpdate) {
            ItemStack stageItem = ItemBuilder.of(Material.CHEST_MINECART)
                    .name(ChatColor.GREEN + "" + ChatColor.BOLD + "Stage PiCore Update")
                    .lore(
                            ChatColor.GRAY + "Downloads " + ChatColor.YELLOW + "v" + updater.getLatestCoreVersion()
                                    + ChatColor.GRAY + " into",
                            ChatColor.DARK_AQUA + "/plugins/update/PiCore.jar" + ChatColor.GRAY + ".",
                            "",
                            ChatColor.GRAY + "Paper will automatically apply it",
                            ChatColor.GRAY + "upon the next server restart cleanly.",
                            "",
                            ChatColor.GOLD + "Click to download & stage.")
                    .glow(true)
                    .build();

            PiButton stageBtn = PiButton.create(stageItem)
                    .sound(Sound.BLOCK_ANVIL_USE)
                    .onClick((p, e) -> {
                        p.sendMessage(ChatColor.AQUA + "Staging PiCore update to /plugins/update/...");
                        updater.stageCoreUpdateAsync(ok -> {
                            if (p.isOnline()) {
                                if (ok) {
                                    p.sendMessage(ChatColor.GREEN
                                            + "✔ PiCore update staged! It will take effect on next reboot.");
                                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                                } else {
                                    p.sendMessage(
                                            ChatColor.RED + "✖ Failed to stage PiCore update. Check server logs.");
                                }
                            }
                        });
                    });
            menu.button(51, stageBtn);
        }

        // Slot 49: Close
        ItemStack closeItem = ItemBuilder.of(Material.BARRIER)
                .name(ChatColor.RED + "Close")
                .build();

        PiButton closeBtn = PiButton.create(closeItem)
                .onClick((p, e) -> p.closeInventory());
        menu.button(49, closeBtn);

        menu.open(player);
    }

    private static List<String> wrapText(String text, int maxLineLen) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty())
            return result;
        String[] lines = text.split("\\R");
        for (String raw : lines) {
            String[] words = raw.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (sb.length() + w.length() + 1 > maxLineLen) {
                    if (sb.length() > 0)
                        result.add(sb.toString().trim());
                    sb = new StringBuilder();
                }
                sb.append(w).append(" ");
            }
            if (sb.length() > 0)
                result.add(sb.toString().trim());
        }
        return result;
    }
}
