package org.illustro.picore.cinematic;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PiCore's {@link PiCinematic}: spectator-locked camera entity + body doubles + timeline effects. */
public final class PiCinematicService implements PiCinematic, Listener {

    private final Plugin plugin;
    private final NamespacedKey doubleTag;
    private final Map<UUID, Playback> active = new HashMap<>();
    private final List<Playback> running = new ArrayList<>();

    public PiCinematicService(Plugin plugin) {
        this.plugin = plugin;
        this.doubleTag = new NamespacedKey(plugin, "cinematic_double");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getServicesManager().register(PiCinematic.class, this, plugin, ServicePriority.Normal);
        int swept = 0;
        for (World w : Bukkit.getWorlds()) for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) if (isDouble(m)) { m.remove(); swept++; }
        plugin.getLogger().info("[PiCinematic] Camera engine ready." + (swept > 0 ? " Swept " + swept + " stray body double(s)." : ""));
    }

    public void shutdown() {
        for (Playback pb : new ArrayList<>(running)) pb.stop();
        HandlerList.unregisterAll(this);
        plugin.getServer().getServicesManager().unregister(PiCinematic.class, this);
    }

    private boolean isDouble(Entity e) {
        return e instanceof Mannequin && e.getPersistentDataContainer().has(doubleTag, PersistentDataType.BYTE);
    }

    @Override
    public CinematicPlayback play(Plugin owner, Cinematic shot, Collection<? extends Player> viewers, Runnable onEnd) {
        for (Player p : viewers) release(p);
        Playback pb = new Playback(shot, onEnd);
        running.add(pb);
        for (Player p : viewers) if (p.isOnline() && p.getWorld().equals(shot.start().getWorld())) pb.add(p);
        pb.begin();
        return pb;
    }

    @Override public boolean inCinematic(Player p) { return active.containsKey(p.getUniqueId()); }

    @Override
    public void release(Player p) {
        Playback pb = active.get(p.getUniqueId());
        if (pb != null) pb.release(p);
    }

    // ---- the viewer can't shift out of the camera, and quitting hands them back before their data is saved

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null && pb.running && pb.camera != null && pb.camera.equals(e.getSpectatorTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) { release(e.getPlayer()); }

    /** A client too old for Mannequins joining while a double stands somewhere: hide it from them (protocol error). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        for (Playback pb : running) {
            if (pb.doubles.isEmpty() || pb.knowsMannequins(e.getPlayer())) continue;
            for (Mannequin m : pb.doubles) e.getPlayer().hideEntity(plugin, m);
        }
    }

    /** True only while PiCinematic itself spawns a body double. */
    private boolean spawningDouble;

    /** WorldGuard's {@code mob-spawning: deny} (with block-plugin-spawning) kills plugin-spawned creatures,
     *  Mannequins included — our own body double always gets through. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoubleSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (spawningDouble && e.getEntity() instanceof Mannequin) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoubleSpawnGeneric(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (spawningDouble && e.getEntity() instanceof Mannequin) e.setCancelled(false);
    }

    /** Until a viewer is locked onto the camera they're a free spectator — keep them where the camera is. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb == null || pb.attached.contains(e.getPlayer().getUniqueId())) return;
        var to = e.getTo();
        if (to.getX() != e.getFrom().getX() || to.getY() != e.getFrom().getY() || to.getZ() != e.getFrom().getZ()) {
            e.setTo(e.getFrom().clone().setDirection(to.getDirection()));
        }
    }

    // ---- the client must have the camera entity loaded before it'll accept "spectate this"; the server sends the
    //      spawn right after this event, so the viewer is locked on two ticks later.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(io.papermc.paper.event.player.PlayerTrackEntityEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null && pb.camera != null && pb.camera.equals(e.getEntity())) pb.tracked(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUntrack(io.papermc.paper.event.player.PlayerUntrackEntityEvent e) {
        Playback pb = active.get(e.getPlayer().getUniqueId());
        if (pb != null && pb.camera != null && pb.camera.equals(e.getEntity())) pb.untracked(e.getPlayer());
    }

    // =====================================================================================================

    private final class Playback implements CinematicPlayback {

        private record Saved(GameMode mode, Location at, boolean allowFlight, boolean flying) { }

        private final Cinematic shot;
        private final Runnable onEnd;
        private final Map<UUID, Saved> saved = new LinkedHashMap<>();
        private final Map<UUID, Integer> vignetteUntil = new HashMap<>(), frostUntil = new HashMap<>();
        private final List<Mannequin> doubles = new ArrayList<>();
        private final List<Location> doubleSpots = new ArrayList<>();
        private BlockDisplay camera;
        private BukkitTask task;
        private int tick;
        private int age;                                       // ticks since begin (the timeline waits for attach)
        private boolean started;
        private final Map<UUID, Integer> attachAt = new HashMap<>();   // viewer -> age to lock them on
        private final java.util.Set<UUID> attached = new java.util.HashSet<>();
        private final Map<UUID, Integer> lastLock = new HashMap<>();
        private final java.util.Set<UUID> lastViewers = new java.util.LinkedHashSet<>();   // everyone who ever watched
        private boolean running;
        private int shakeLeft, shakeTotal;
        private double shakeStrength;

        Playback(Cinematic shot, Runnable onEnd) {
            this.shot = shot;
            this.onEnd = onEnd;
        }

        void add(Player p) {
            if (shot.bodyDouble() && p.getGameMode() != GameMode.SPECTATOR) spawnDouble(p);
            saved.put(p.getUniqueId(), new Saved(p.getGameMode(), p.getLocation(), p.getAllowFlight(), p.isFlying()));
            active.put(p.getUniqueId(), this);
            lastViewers.add(p.getUniqueId());
        }

        void begin() {
            Location start = shot.start();
            camera = start.getWorld().spawn(start, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setTeleportDuration(4);                   // client glides each per-tick move over 4 ticks: soaks up ping jitter
                d.setViewRange(0.1f);
            });
            running = true;
            for (Player p : viewers()) {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(start);
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 1L, 1L);
        }

        void tracked(Player p) { attachAt.put(p.getUniqueId(), age + 2); }

        void untracked(Player p) { attached.remove(p.getUniqueId()); attachAt.remove(p.getUniqueId()); }

        /** Lock viewers on once their client has the camera; re-lock (at most once a second) if something knocked
         *  them off. Never spams it — every lock is a camera packet + position sync on the client. */
        private void attach(Player p) {
            UUID id = p.getUniqueId();
            Entity target = p.getSpectatorTarget();
            if (target != null && target.equals(camera)) { attached.add(id); return; }
            attached.remove(id);
            Integer at = attachAt.get(id);
            if (at == null && age >= 20) at = 20;                       // no track event seen: try anyway
            if (at == null || age < at) return;
            Integer last = lastLock.get(id);
            if (last != null && age - last < 20) return;
            lastLock.put(id, age);
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            p.setSpectatorTarget(camera);
        }

        private void step() {
            if (!running) return;
            if (saved.isEmpty() || !camera.isValid()) { stop(); return; }
            age++;
            List<Player> vs = new ArrayList<>(viewers());
            for (Player p : vs) attach(p);
            if (!started) {
                // hold the first pose until every viewer is really watching through the camera (3 s at most)
                if (attached.size() < vs.size() && age < 60) return;
                started = true;
            }
            Location pose = shot.poseAt(tick);
            if (shakeLeft > 0) {
                // smooth rumble: layered sines (no per-tick random jitter), fading out
                double f = shakeLeft / (double) shakeTotal, s = shakeStrength * f * f, t = age;
                pose.add(s * (Math.sin(t * 1.9) * 0.6 + Math.sin(t * 3.7) * 0.4),
                        s * (Math.sin(t * 2.3 + 1.1) * 0.6 + Math.sin(t * 4.1) * 0.4),
                        s * (Math.sin(t * 1.6 + 2.0) * 0.6 + Math.sin(t * 3.3) * 0.4));
                pose.setPitch(pose.getPitch() + (float) (s * 6 * Math.sin(t * 2.7)));
                pose.setYaw(pose.getYaw() + (float) (s * 6 * Math.sin(t * 2.1 + 0.5)));
                shakeLeft--;
            }
            camera.teleport(pose);
            for (CinematicEffect fx : shot.effectsAt(tick)) {
                try {
                    if (fx instanceof CinematicEffect.World) fx.play(vs.isEmpty() ? null : vs.get(0), this);   // once
                    else for (Player p : vs) fx.play(p, this);
                } catch (RuntimeException ex) { plugin.getLogger().warning("[PiCinematic] effect failed: " + ex); }
            }
            for (Player p : vs) expireEffects(p);
            if (++tick > shot.length()) stop();
        }

        private void expireEffects(Player p) {
            UUID id = p.getUniqueId();
            Integer v = vignetteUntil.get(id);
            if (v != null && tick >= v) { vignetteUntil.remove(id); p.setWorldBorder(null); }
            Integer f = frostUntil.get(id);
            if (f != null && tick >= f) { frostUntil.remove(id); p.lockFreezeTicks(false); p.setFreezeTicks(0); }
        }

        @Override public int tick() { return tick; }
        @Override public boolean running() { return running; }

        @Override
        public Collection<Player> viewers() {
            List<Player> out = new ArrayList<>(saved.size());
            for (UUID id : saved.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) out.add(p); }
            return out;
        }

        @Override
        public void stop() {
            if (!running && task == null) return;
            running = false;
            if (task != null) { task.cancel(); task = null; }
            removeDoubles(false);
            for (UUID id : new ArrayList<>(saved.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) restore(p);
                else dropRecord(id);
            }
            if (camera != null) camera.remove();
            // belt and braces: a tick later nobody may still be spectating the (now removed) camera
            List<UUID> was = new ArrayList<>(lastViewers);
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : was) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || p.getGameMode() != GameMode.SPECTATOR || active.containsKey(id)) continue;
                    Entity t = p.getSpectatorTarget();
                    if (t != null && (!t.isValid() || t.equals(camera))) p.setSpectatorTarget(null);
                }
            }, 2L);
            finished();
        }

        private void finished() {
            PiCinematicService.this.running.remove(this);
            if (onEnd != null) {
                try { onEnd.run(); } catch (RuntimeException ex) { plugin.getLogger().warning("[PiCinematic] end callback failed: " + ex); }
            }
        }

        @Override
        public void release(Player viewer) {
            if (!saved.containsKey(viewer.getUniqueId())) return;
            restore(viewer);
            if (saved.isEmpty() && running) stop();
        }

        private void restore(Player p) {
            Saved s = saved.get(p.getUniqueId());
            dropRecord(p.getUniqueId());
            if (s == null) return;
            if (p.getGameMode() == GameMode.SPECTATOR) {
                // The client can still be riding the camera while the server already counts it as free (a lock the
                // client refused, a re-lock in flight). setSpectatorTarget(null) only sends a camera packet when the
                // SERVER's camera changes, so such a viewer kept looking through a camera that is about to vanish -
                // frozen until they rejoined. Force the packet both ways while the camera still exists.
                if (camera != null && camera.isValid() && camera.getWorld().equals(p.getWorld()) && !camera.equals(p.getSpectatorTarget()))
                    p.setSpectatorTarget(camera);
                p.setSpectatorTarget(null);
            }
            p.setWorldBorder(null);
            p.lockFreezeTicks(false);
            p.setFreezeTicks(0);
            p.setGameMode(s.mode);
            p.teleport(s.at);
            p.setFallDistance(0);
            if (s.mode == GameMode.SURVIVAL || s.mode == GameMode.ADVENTURE) {
                p.setAllowFlight(s.allowFlight);
                if (s.allowFlight) p.setFlying(s.flying);
            }
        }

        private void dropRecord(UUID id) {
            saved.remove(id);
            active.remove(id, this);
            attachAt.remove(id);
            attached.remove(id);
            lastLock.remove(id);
            vignetteUntil.remove(id);
            frostUntil.remove(id);
        }

        /** Remove every body double — by reference, then by tag around where they stood (also one tick later, in
         *  case the world finished adding one after we looked). */
        private void removeDoubles(boolean poof) {
            for (Mannequin m : doubles) {
                if (poof && m.isValid()) {
                    Location l = m.getLocation().add(0, 1, 0);
                    l.getWorld().spawnParticle(Particle.POOF, l, 30, 0.35, 0.6, 0.35, 0.02);
                    l.getWorld().spawnParticle(Particle.END_ROD, l, 12, 0.3, 0.6, 0.3, 0.05);
                    l.getWorld().playSound(l, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
                }
                m.remove();
            }
            doubles.clear();
            List<Location> spots = new ArrayList<>(doubleSpots);
            Runnable sweep = () -> {
                for (Location l : spots) {
                    if (l.getWorld() == null) continue;
                    for (Entity e : l.getWorld().getNearbyEntities(l, 3, 3, 3)) if (isDouble(e)) e.remove();
                }
            };
            sweep.run();
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, sweep, 1L);
        }

        @Override public void vanishDoubles() { removeDoubles(true); }

        @Override
        public void join(Player p) {
            if (!running || saved.containsKey(p.getUniqueId()) || !p.getWorld().equals(camera.getWorld())) return;
            Playback other = active.get(p.getUniqueId());
            if (other != null) other.release(p);
            saved.put(p.getUniqueId(), new Saved(p.getGameMode(), p.getLocation(), p.getAllowFlight(), p.isFlying()));
            active.put(p.getUniqueId(), this);
            lastViewers.add(p.getUniqueId());
            // A late joiner must NOT be locked on at once (the "try anyway after 20 ticks" fallback is long past):
            // their client doesn't have the camera yet and would refuse - half-attached, the shot looked broken.
            // Wait for the track event (it moves this to age + 2), or 1 s at most.
            attachAt.put(p.getUniqueId(), age + 20);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(camera.getLocation());
        }

        @Override
        public void shake(int ticks, double strength) {
            shakeLeft = shakeTotal = Math.max(1, ticks);
            shakeStrength = strength;
        }

        @Override
        public void vignette(Player viewer, int ticks) {
            WorldBorder wb = Bukkit.createWorldBorder();
            wb.setCenter(camera.getLocation());
            wb.setSize(4000);
            wb.setWarningDistance(1_000_000);
            viewer.setWorldBorder(wb);
            vignetteUntil.put(viewer.getUniqueId(), tick + ticks);
        }

        @Override
        public void frost(Player viewer, int ticks) {
            viewer.lockFreezeTicks(true);
            viewer.setFreezeTicks(Math.max(0, viewer.getMaxFreezeTicks() - 1));
            frostUntil.put(viewer.getUniqueId(), tick + ticks);
        }

        /**
         * Mannequins only exist from 1.21.9 (protocol 773). Older clients joining through ViaBackwards get a protocol
         * error and drop when one is sent to them (seen on 1.21.1 players at every match intro), so they never get
         * the body double - they still see the camera shot. Without ViaVersion everyone is on the server's version.
         */
        private boolean knowsMannequins(Player o) {
            int min = plugin.getConfig().getInt("cinematic.body-double-min-protocol", 773);
            try {
                var vv = Bukkit.getPluginManager().getPlugin("ViaVersion");
                if (vv == null) return true;
                Class<?> via = Class.forName("com.viaversion.viaversion.api.Via", true, vv.getClass().getClassLoader());
                Object api = via.getMethod("getAPI").invoke(null);
                Object v = api.getClass().getMethod("getPlayerVersion", java.util.UUID.class).invoke(api, o.getUniqueId());
                return !(v instanceof Integer proto) || proto < 0 || proto >= min;
            } catch (Throwable t) {
                return true;
            }
        }

        private void spawnDouble(Player p) {
            Location at = p.getLocation();
            doubleSpots.add(at.clone());
            spawningDouble = true;
            try {
                Mannequin made = at.getWorld().spawn(at, Mannequin.class, m -> {
                    m.getPersistentDataContainer().set(doubleTag, PersistentDataType.BYTE, (byte) 1);
                    m.setPersistent(false);
                    m.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                    m.setImmovable(true);
                    m.setInvulnerable(true);
                    m.setSilent(true);
                    m.setGravity(false);
                    m.setAI(false);
                    m.setMainHand(p.getMainHand());
                    m.setDescription(Component.empty());
                    m.customName(p.displayName());
                    m.setCustomNameVisible(true);
                    m.setBodyYaw(at.getYaw());
                    m.setRotation(at.getYaw(), at.getPitch());
                    EntityEquipment src = p.getEquipment(), dst = m.getEquipment();
                    dst.setHelmet(src.getHelmet());
                    dst.setChestplate(src.getChestplate());
                    dst.setLeggings(src.getLeggings());
                    dst.setBoots(src.getBoots());
                    dst.setItemInMainHand(src.getItemInMainHand());
                    dst.setItemInOffHand(src.getItemInOffHand());
                    for (var slot : org.bukkit.inventory.EquipmentSlot.values()) {
                        try { dst.setDropChance(slot, 0f); } catch (IllegalArgumentException ignored) { }
                    }
                });
                if (made != null) {
                    doubles.add(made);                      // kept even if not yet valid: removal must never miss it
                    // Visible by default, hidden only from clients that can't decode a Mannequin. (It used to be
                    // hidden by default + showEntity per viewer - on 26.2 the reveal never reached the client, so the
                    // camera swooped onto an empty spawn.)
                    for (Player o : Bukkit.getOnlinePlayers()) if (!knowsMannequins(o)) o.hideEntity(plugin, made);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[PiCinematic] body double unavailable: " + ex);
            } finally {
                spawningDouble = false;
            }
        }
    }
}
