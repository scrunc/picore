package org.illustro.picore.screen;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;

/** One wall screen. See {@link PiScreens}. */
public interface PiScreen {

    String id();

    int cols();

    int rows();

    /** Pixels: {@code cols × 128} by {@code rows × 128}. */
    int width();

    int height();

    /** The drawing surface (ARGB). Draw, then {@link #push()}. */
    PiCanvas canvas();

    /** Converts the canvas to map colours; maps whose pixels changed are re-sent to viewers. Main thread. */
    void push();

    /** Centre of the screen surface (for holograms, sounds, distance checks). */
    Location center();

    BlockFace face();

    /** Whether {@code e} is one of this screen's frames (clicks on the screen). */
    boolean isFrame(Entity e);

    /**
     * Which canvas pixel a frame click hit - {@code {x, y}} - from the clicking player's eye ray, or null.
     * Lets a screen act as a touch surface ("click a match to watch it").
     */
    int[] pixelAt(org.bukkit.entity.Player p, Entity frame);
}
