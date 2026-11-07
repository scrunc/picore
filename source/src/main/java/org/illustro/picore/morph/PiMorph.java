package org.illustro.picore.morph;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The shared arena-morph engine, published as a Bukkit service by PiCore (module {@code modules.morph}).
 * Consumers softdepend PiCore, then {@code getServicesManager().load(PiMorph.class)}; guard for null the
 * same way any other optional service is consumed — PiCore absent (or the module disabled) just means the
 * morph feature is unavailable, nothing else about the consumer breaks.
 *
 * <p>PiMorph owns exactly one thing: animating a region from one {@link MorphGrid} into another with a
 * chosen choreography ({@link MorphSpec.Style}), plus the {@code morphs/*.yml} style registry that names
 * those choreographies. It has no opinion on what "capture a region" or "a named snapshot library" means
 * — that's entirely the consumer's own persistence format, built into a {@link MorphGrid} once per call.
 */
public interface PiMorph {

    /** All registered style ids, in load order — for building a style-picker menu. */
    List<String> styleIds();

    String displayName(String id);

    Material icon(String id);

    List<String> lore(String id);

    int styleCount();

    /** Re-scan {@code plugins/PiCore/morphs/} — call after an operator edits or drops in a pack. */
    void reload();

    /**
     * Build an {@link ArenaMorph} ready to {@link ArenaMorph#start(Runnable)}, resolving {@code
     * spec.styleId} against this registry. The morph does not start itself — the caller decides when, and
     * keeps owning cancellation / "one running morph at a time" bookkeeping, exactly as it would if it
     * had constructed {@link ArenaMorph} directly.
     *
     * @param owner      the CALLING plugin (used for its scheduler/logger/{@code NamespacedKey} owner —
     *                   not necessarily PiMorph's own plugin instance)
     * @param target     the grid to morph the world into
     * @param compare    cells equal in both are left untouched; {@code null} pastes every cell (full reset)
     * @param seconds    sweep duration; &le;0 pastes as fast as {@code settings} allows
     * @param spec       the resolved style + FX settings for this morph
     * @param settings   engine tunables — the caller's own config, converted once
     * @param ownerKeyId string namespacing the PDC tag stamped on spawned displays, so a later morph can
     *                   find and retire the ones this call placed
     */
    ArenaMorph morph(Plugin owner, MorphGrid target, MorphGrid compare, int seconds,
                      MorphSpec spec, MorphSettings settings, String ownerKeyId);
}
