package org.illustro.picore.screen;

import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

import java.util.Arrays;

/**
 * An ARGB drawing surface with simple primitives and Minecraft's own bitmap font ({@link MinecraftFont}).
 * Deliberately no Java2D text: headless server containers often have no system fonts, and AWT text then fails.
 *
 * <p>Text takes legacy colour codes inline: {@code "&cRed &fwhite"} (&0-&f; &r = back to the call's colour).
 * Characters the font doesn't have are drawn as {@code ?}.
 */
public final class PiCanvas {

    private static final int[] LEGACY = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private final int width, height;
    final int[] px;
    int version;

    public PiCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.px = new int[width * height];
    }

    public int width() { return width; }

    public int height() { return height; }

    public void clear(int rgb) { Arrays.fill(px, rgb | 0xFF000000); }

    public void set(int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        px[y * width + x] = rgb | 0xFF000000;
    }

    public int get(int x, int y) { return x < 0 || y < 0 || x >= width || y >= height ? 0 : px[y * width + x]; }

    public void fill(int x, int y, int w, int h, int rgb) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(width, x + w), y1 = Math.min(height, y + h);
        int c = rgb | 0xFF000000;
        for (int yy = y0; yy < y1; yy++) Arrays.fill(px, yy * width + x0, Math.max(yy * width + x0, yy * width + x1), c);
    }

    /** Outline, {@code t} pixels thick. */
    public void rect(int x, int y, int w, int h, int t, int rgb) {
        fill(x, y, w, t, rgb);
        fill(x, y + h - t, w, t, rgb);
        fill(x, y, t, h, rgb);
        fill(x + w - t, y, t, h, rgb);
    }

    public void line(int x0, int y0, int x1, int y1, int rgb) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            set(x0, y0, rgb);
            if (x0 == x1 && y0 == y1) return;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** Vertical gradient fill from {@code top} to {@code bottom}. */
    public void gradient(int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            double t = h <= 1 ? 0 : i / (double) (h - 1);
            fill(x, y + i, w, 1, mix(top, bottom, t));
        }
    }

    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ text

    private static MapFont.CharacterSprite glyph(char ch) {
        MapFont.CharacterSprite s = MinecraftFont.Font.getChar(ch);
        if (s != null) return s;
        char alt = switch (ch) {                                   // common symbols the map font lacks
            case '·', '•', '—', '–', '─' -> '-';
            case '✦', '★', '☆', '✧' -> '*';
            case '▶', '►', '»', '→' -> '>';
            case '◀', '◄', '«', '←' -> '<';
            case '●', '◆', '■' -> 'o';
            case '✔', '✓' -> 'v';
            case '✖', '✗', '×' -> 'x';
            case '⚔' -> 'X';
            default -> '?';
        };
        s = MinecraftFont.Font.getChar(alt);
        return s != null ? s : MinecraftFont.Font.getChar('?');
    }

    /** Font height in pixels at {@code scale}. */
    public static int lineHeight(int scale) { return MinecraftFont.Font.getHeight() * Math.max(1, scale); }

    /** Width of {@code s} (colour codes skipped) at {@code scale}. */
    public static int textWidth(String s, int scale) {
        int w = 0, sc = Math.max(1, scale);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < s.length() && code(s.charAt(i + 1)) != -2) { i++; continue; }
            MapFont.CharacterSprite g = glyph(ch);
            if (g != null) w += (g.getWidth() + 1) * sc;
        }
        return Math.max(0, w - sc);
    }

    /** -1 = reset, 0..15 = colour, -2 = not a code. */
    private static int code(char c) {
        if (c == 'r' || c == 'R') return -1;
        int d = Character.digit(c, 16);
        return d >= 0 ? d : (c == 'l' || c == 'L' || c == 'o' || c == 'n' || c == 'm' || c == 'k') ? -3 : -2;
    }

    /** Draws {@code s} with its top-left at x,y; returns the x after the last character. A 1-px shadow when {@code shadow}. */
    public int text(int x, int y, String s, int rgb, int scale, boolean shadow) {
        if (shadow) text(x + Math.max(1, scale / 2), y + Math.max(1, scale / 2), strip(s), 0x1A1A1A, scale, false);
        int sc = Math.max(1, scale), c = rgb, cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < s.length()) {
                int k = code(s.charAt(i + 1));
                if (k != -2) {
                    if (k == -1) c = rgb; else if (k >= 0) c = LEGACY[k];
                    i++;
                    continue;
                }
            }
            MapFont.CharacterSprite g = glyph(ch);
            if (g == null) continue;
            for (int gy = 0; gy < g.getHeight(); gy++)
                for (int gx = 0; gx < g.getWidth(); gx++)
                    if (g.get(gy, gx)) fill(cx + gx * sc, y + gy * sc, sc, sc, c);
            cx += (g.getWidth() + 1) * sc;
        }
        return cx;
    }

    public int text(int x, int y, String s, int rgb, int scale) { return text(x, y, s, rgb, scale, false); }

    /** Centred on {@code cx}. */
    public void textCentered(int cx, int y, String s, int rgb, int scale, boolean shadow) {
        text(cx - textWidth(s, scale) / 2, y, s, rgb, scale, shadow);
    }

    /** {@code s} cut (with "..") to fit {@code maxWidth} pixels at {@code scale}. */
    public static String fit(String s, int maxWidth, int scale) {
        if (textWidth(s, scale) <= maxWidth) return s;
        String t = s;
        while (!t.isEmpty() && textWidth(t + "..", scale) > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    public static String strip(String s) { return s.replaceAll("[&§][0-9a-fA-Fk-oK-OrR]", ""); }
}
