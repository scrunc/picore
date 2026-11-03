package org.illustro.picore.menu.components;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.illustro.picore.menu.PiButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Universal component for displaying chronological chain-of-custody in PiCore.
 * Renders player heads with detailed provenance timestamps, handoff context, and transition actions.
 */
public class TouchChainComponent {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("MMM d, HH:mm:ss");

    public record TouchRecord(
            long id,
            String itemId,
            String playerUuid,
            String playerName,
            String action,
            String containerType,
            String location,
            long timestamp,
            String details
    ) {
        public TouchRecord(long id, String itemId, String playerUuid, String playerName, String action, String containerType, String location, long timestamp) {
            this(id, itemId, playerUuid, playerName, action, containerType, location, timestamp, null);
        }

        public String formattedTime() {
            return FMT.format(new Date(timestamp));
        }
    }

    public static PiButton createTouchHead(TouchRecord record, int stepIndex, int totalSteps, boolean isCurrent) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String roleTag = stepIndex == 0 ? "&a[Origin Creator]" : (isCurrent ? "&6[Current Holder]" : "&e[Chain Step " + (stepIndex + 1) + "]");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', roleTag + " &f" + record.playerName()));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Action: " + actionBadge(record.action()));
            if (record.details() != null && !record.details().isBlank()) {
                lore.add(ChatColor.YELLOW + "Context: &f" + record.details());
            }
            lore.add(ChatColor.GRAY + "Container: &f" + record.containerType());
            lore.add(ChatColor.GRAY + "Location: &7" + (record.location() != null ? record.location() : "Unknown"));
            lore.add(ChatColor.GRAY + "Time: &f" + record.formattedTime());
            lore.add(" ");
            if (stepIndex == 0) {
                lore.add(ChatColor.DARK_GRAY + "First recorded acquisition point.");
            } else if (isCurrent) {
                lore.add(ChatColor.DARK_GRAY + "Latest verified possession.");
            } else {
                lore.add(ChatColor.DARK_GRAY + "Transferred in sequence.");
            }

            meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList());
            try {
                if (record.playerUuid() != null && !record.playerUuid().isBlank()) {
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(record.playerUuid()));
                    meta.setOwningPlayer(op);
                }
            } catch (Exception ignored) { }

            head.setItemMeta(meta);
        }

        return PiButton.create(head);
    }

    public static String actionBadge(String action) {
        if (action == null) return "&7UNKNOWN";
        return switch (action.toUpperCase()) {
            case "CREATED" -> "&a✔ CREATED";
            case "CHEST_DEPOSIT", "CONTAINER_DEPOSIT", "CONTAINER_IN" -> "&e📥 DEPOSITED";
            case "CHEST_WITHDRAW", "CONTAINER_WITHDRAW", "CONTAINER_OUT" -> "&b📤 WITHDRAWN";
            case "INVSEE_TAKE" -> "&c🔍 INVSEE TAKEN";
            case "INVSEE_GIVE" -> "&d📦 INVSEE GIVEN";
            case "INVSEE_RECEIVE" -> "&a🎁 INVSEE RECEIVED";
            case "DROP" -> "&c▼ DROPPED";
            case "PICKUP", "GROUND_PICKUP" -> "&2▲ PICKED UP";
            case "TRADE" -> "&d🔄 TRADED";
            case "SWEEP", "HELD", "IN_INVENTORY" -> "&3🛡 HELD IN INVENTORY";
            case "BROKEN" -> "&4💥 BROKEN";
            case "DUPE_BRANCH" -> "&4⚠ DUPE BRANCHED";
            default -> "&f" + action;
        };
    }
}
