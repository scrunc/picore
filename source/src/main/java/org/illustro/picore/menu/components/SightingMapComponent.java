package org.illustro.picore.menu.components;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.illustro.picore.menu.PiButton;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Universal component for displaying spatial container sightings in PiCore.
 * Maps unique locations and container types (Chests, Shulkers, Barrels, Ender Chests).
 */
public class SightingMapComponent {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());

    public record SightingRecord(
            long id,
            String itemId,
            String locationKey,
            String containerType,
            String locationDisplay,
            long firstSeen,
            long lastSeen,
            int totalVisits
    ) { }

    public static PiButton createSightingButton(SightingRecord record) {
        Material mat = switch (record.containerType().toUpperCase()) {
            case "CHEST" -> Material.CHEST;
            case "BARREL" -> Material.BARREL;
            case "SHULKER_BOX" -> Material.SHULKER_BOX;
            case "ENDER_CHEST" -> Material.ENDER_CHEST;
            case "GROUND" -> Material.GRASS_BLOCK;
            default -> Material.DROPPER;
        };

        String containerName = record.containerType().replace('_', ' ');
        return PiButton.create(mat,
            "&b" + containerName + " &8· &f" + record.locationDisplay(),
            "&7Container Type: &f" + containerName,
            "&7Coordinates: &f" + record.locationDisplay(),
            "&7Visits Logged: &e" + record.totalVisits(),
            "&7First Sighted: &f" + FMT.format(Instant.ofEpochMilli(record.firstSeen())),
            "&7Last Sighted: &f" + FMT.format(Instant.ofEpochMilli(record.lastSeen())),
            " ",
            "&8Spatial sighting ledger");
    }
}

