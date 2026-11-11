package org.illustro.picore.cinematic;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One camera shot: a start pose, keyframes the camera glides through (each with its own travel time and
 * easing), and effects pinned to ticks on the timeline. Immutable once built; play it with {@link PiCinematic}.
 *
 * <pre>{@code
 * Cinematic shot = Cinematic.builder(sky)
 *         .now(CinematicEffect.title("&6&lDUEL", "", 5, 30, 10))
 *         .to(Cinematic.look(nearA, faceA), 30)          // swoop to player A
 *         .hold(20)
 *         .to(wide, 30, Cinematic.Ease.OUT)
 *         .now(CinematicEffect.shake(10, 0.15))
 *         .build();
 * }</pre>
 */
public final class Cinematic {

    /** How a keyframe segment accelerates. */
    public enum Ease {
        LINEAR, SMOOTH, IN, OUT;

        double apply(double t) {
            return switch (this) {
                case LINEAR -> t;
                case SMOOTH -> t * t * (3 - 2 * t);
                case IN -> t * t * t;
                case OUT -> 1 - Math.pow(1 - t, 3);
            };
        }
    }

    private record Key(Location pose, int ticks, Ease ease) { }

    private final Location start;
    private final List<Key> keys;
    private final Map<Integer, List<CinematicEffect>> effects;
    private final int length;
    private final boolean bodyDouble;

    private Cinematic(Builder b) {
        this.start = b.start.clone();
        this.keys = List.copyOf(b.keys);
        TreeMap<Integer, List<CinematicEffect>> fx = new TreeMap<>();
        b.effects.forEach((k, v) -> fx.put(k, List.copyOf(v)));
        this.effects = Collections.unmodifiableMap(fx);
        int len = b.cursor;
        for (int t : fx.keySet()) len = Math.max(len, t);
        this.length = len;
        this.bodyDouble = b.bodyDouble;
    }

    public static Builder builder(Location start) { return new Builder(start); }

    /** A camera pose at {@code eye} looking at {@code target}. */
    public static Location look(Location eye, Location target) {
        Location l = eye.clone();
        Vector dir = target.toVector().subtract(eye.toVector());
        if (dir.lengthSquared() > 1e-6) l.setDirection(dir);
        return l;
    }

    /** Total ticks, including effects scheduled past the last keyframe. */
    public int length() { return length; }

    public Location start() { return start.clone(); }

    /** Viewers who are not already spectators leave a skinned stand-in where they stood. */
    public boolean bodyDouble() { return bodyDouble; }

    List<CinematicEffect> effectsAt(int tick) { return effects.getOrDefault(tick, List.of()); }

    /** The camera pose at {@code tick} (clamped to the last keyframe). */
    public Location poseAt(int tick) {
        Location from = start;
        int t = tick;
        for (Key k : keys) {
            if (t <= k.ticks) {
                double f = k.ticks <= 0 ? 1 : k.ease.apply(Math.max(0, Math.min(1, t / (double) k.ticks)));
                return lerp(from, k.pose, f);
            }
            t -= k.ticks;
            from = k.pose;
        }
        return from.clone();
    }

    private static Location lerp(Location a, Location b, double f) {
        Location l = a.clone();
        l.setX(a.getX() + (b.getX() - a.getX()) * f);
        l.setY(a.getY() + (b.getY() - a.getY()) * f);
        l.setZ(a.getZ() + (b.getZ() - a.getZ()) * f);
        float dy = ((b.getYaw() - a.getYaw()) % 360 + 540) % 360 - 180;      // shortest way round
        l.setYaw((float) (a.getYaw() + dy * f));
        l.setPitch((float) (a.getPitch() + (b.getPitch() - a.getPitch()) * f));
        return l;
    }

    public static final class Builder {
        private final Location start;
        private final List<Key> keys = new ArrayList<>();
        private final Map<Integer, List<CinematicEffect>> effects = new TreeMap<>();
        private int cursor;
        private boolean bodyDouble = true;

        private Builder(Location start) { this.start = start.clone(); }

        /** Glide to {@code pose} over {@code ticks}, eased. */
        public Builder to(Location pose, int ticks, Ease ease) {
            keys.add(new Key(pose.clone(), Math.max(0, ticks), ease));
            cursor += Math.max(0, ticks);
            return this;
        }

        public Builder to(Location pose, int ticks) { return to(pose, ticks, Ease.SMOOTH); }

        /** Cut straight to {@code pose}. */
        public Builder cut(Location pose) { return to(pose, 0, Ease.LINEAR); }

        /** Stay on the current pose for {@code ticks}. */
        public Builder hold(int ticks) {
            Location last = keys.isEmpty() ? start : keys.get(keys.size() - 1).pose;
            return to(last, ticks, Ease.LINEAR);
        }

        /** Fire {@code effect} at an absolute tick. */
        public Builder at(int tick, CinematicEffect effect) {
            effects.computeIfAbsent(Math.max(0, tick), k -> new ArrayList<>()).add(effect);
            return this;
        }

        /** Fire {@code effect} at the current end of the camera path (where the next keyframe starts). */
        public Builder now(CinematicEffect effect) { return at(cursor, effect); }

        /** Ticks of camera path so far — handy for {@link #at}. */
        public int cursor() { return cursor; }

        public Builder bodyDouble(boolean on) { this.bodyDouble = on; return this; }

        public Cinematic build() { return new Cinematic(this); }
    }
}
