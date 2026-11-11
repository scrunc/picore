package org.illustro.picore.cinematic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

/**
 * Something that happens to one viewer at a tick of a {@link Cinematic}. The factories cover the screen
 * effects vanilla can draw without a resource pack; anything else is a lambda away.
 */
@FunctionalInterface
public interface CinematicEffect {

    void play(Player viewer, CinematicPlayback playback);

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy == null ? "" : legacy);
    }

    /** Title + subtitle ({@code &} colour codes). Times in ticks. */
    static CinematicEffect title(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(text(title), text(subtitle), Title.Times.times(
                Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L)));
        return (p, pb) -> p.showTitle(t);
    }

    static CinematicEffect actionBar(String text) {
        Component c = text(text);
        return (p, pb) -> p.sendActionBar(c);
    }

    static CinematicEffect sound(Sound sound, float volume, float pitch) {
        return (p, pb) -> p.playSound(p, sound, volume, pitch);
    }

    /** Camera jitter for {@code ticks}, up to {@code strength} blocks, fading out. Applies to the whole shot. */
    static CinematicEffect shake(int ticks, double strength) {
        return (p, pb) -> pb.shake(ticks, strength);
    }

    /** Red danger vignette round the screen edges (a per-viewer world border warning). */
    static CinematicEffect vignette(int ticks) {
        return (p, pb) -> pb.vignette(p, ticks);
    }

    /** Powder-snow frost overlay. */
    static CinematicEffect frost(int ticks) {
        return (p, pb) -> pb.frost(p, ticks);
    }

    /** Nausea wobble — the whole view swims. */
    static CinematicEffect wobble(int ticks) {
        return (p, pb) -> p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 0, false, false, false));
    }

    /** Marker: a world effect (fireworks, lightning, particles) — played ONCE per tick, not once per viewer. */
    interface World extends CinematicEffect { }

    private static CinematicEffect world(java.util.function.Consumer<CinematicPlayback> run) {
        return (World) (p, pb) -> run.accept(pb);
    }

    /** Body doubles vanish in a puff of particles. */
    static CinematicEffect vanishDoubles() { return world(CinematicPlayback::vanishDoubles); }

    /** The full-screen totem-pop animation for each viewer (plus its sound). */
    static CinematicEffect totem() {
        return (p, pb) -> p.playEffect(org.bukkit.EntityEffect.PROTECTED_FROM_DEATH);
    }

    /** An instant firework burst at {@code at}. */
    static CinematicEffect firework(org.bukkit.Location at, org.bukkit.FireworkEffect.Type type, org.bukkit.Color... colors) {
        org.bukkit.Location l = at.clone();
        return world(pb -> l.getWorld().spawn(l, org.bukkit.entity.Firework.class, fw -> {
            var meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder().with(type).withColor(colors).withFade(org.bukkit.Color.WHITE)
                    .flicker(true).trail(true).build());
            fw.setFireworkMeta(meta);
            fw.setSilent(false);
        }).detonate());
    }

    /** A harmless lightning strike (flash + thunder) at {@code at}. */
    static CinematicEffect lightning(org.bukkit.Location at) {
        org.bukkit.Location l = at.clone();
        return world(pb -> l.getWorld().strikeLightningEffect(l));
    }

    static CinematicEffect particles(org.bukkit.Particle particle, org.bukkit.Location at, int count, double spread, double speed) {
        org.bukkit.Location l = at.clone();
        return world(pb -> l.getWorld().spawnParticle(particle, l, count, spread, spread, spread, speed));
    }

    /** A rising ring of particles round {@code at} (feet height), radius {@code r}. */
    static CinematicEffect ring(org.bukkit.Particle particle, org.bukkit.Location at, double r) {
        org.bukkit.Location c = at.clone();
        return world(pb -> {
            for (int i = 0; i < 36; i++) {
                double a = Math.PI * 2 * i / 36;
                for (int h = 0; h < 3; h++) {
                    c.getWorld().spawnParticle(particle, c.clone().add(Math.cos(a) * r, 0.2 + h * 0.7, Math.sin(a) * r), 1, 0, 0.05, 0, 0.01);
                }
            }
        });
    }

    /** Several effects at once. */
    static CinematicEffect all(CinematicEffect... effects) {
        return (p, pb) -> { for (CinematicEffect e : effects) e.play(p, pb); };
    }
}
