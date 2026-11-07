package org.illustro.picore.morph;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.List;

/**
 * Registers the {@link PiMorph} service on behalf of PiCore and owns the style registry
 * ({@code plugins/PiCore/morphs/}). Lives as long as PiCore does — a fresh classloader would invalidate
 * every consumer's cached service reference, so pick up a jar update with a restart, not a hot-swap.
 */
public final class PiMorphService implements PiMorph {

    private final Plugin plugin;
    private final MorphRegistry registry;

    public PiMorphService(Plugin plugin) {
        this.plugin = plugin;
        this.registry = new MorphRegistry(plugin);   // scans/copies plugins/PiCore/morphs/ on first run
        plugin.getServer().getServicesManager().register(PiMorph.class, this, plugin, ServicePriority.Normal);
        plugin.getLogger().info("[PiMorph] Loaded " + registry.count() + " morph styles.");
    }

    public void shutdown() {
        plugin.getServer().getServicesManager().unregister(PiMorph.class, this);
    }

    @Override public List<String> styleIds() { return registry.styleIds(); }
    @Override public String displayName(String id) { return registry.displayName(id); }
    @Override public Material icon(String id) { return registry.icon(id); }
    @Override public List<String> lore(String id) { return registry.lore(id); }
    @Override public int styleCount() { return registry.count(); }
    @Override public void reload() { registry.reload(); }

    @Override
    public ArenaMorph morph(Plugin owner, MorphGrid target, MorphGrid compare, int seconds,
                             MorphSpec spec, MorphSettings settings, String ownerKeyId) {
        MorphEffect eff = registry.effect(spec.styleId);
        return new ArenaMorph(owner, target, compare, seconds, spec, settings, eff, ownerKeyId);
    }
}
