package org.illustro.picore.screen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PiScreens} implementation. Frame placement and the RGB→palette lookup table come from ArcadeCab's
 * ScreenManager / MapColor; rendering goes through a Bukkit {@link MapRenderer} (no packet library needed),
 * which the server already re-sends to everyone tracking the frame whenever its pixels change.
 */
public final class PiScreenService implements PiScreens, Listener {

    private static final int MAP = 128;

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Map<String, Screen> screens = new HashMap<>();
    private static volatile byte[] lut;

    public PiScreenService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "screen");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getServicesManager().register(PiScreens.class, this, plugin, ServicePriority.Normal);
    }

    public void shutdown() {
        plugin.getServer().getServicesManager().unregister(PiScreens.class, this);
        screens.clear();                                         // frames stay in the world; re-open finds them
    }

    private static String full(Plugin owner, String id) { return owner.getName().toLowerCase() + ":" + id.toLowerCase(); }

    @Override
    public PiScreen get(Plugin owner, String id) { return screens.get(full(owner, id)); }

    @Override
    public void remove(PiScreen screen) {
        if (!(screen instanceof Screen s)) return;
        screens.remove(s.full);
        for (ItemFrame f : s.frames) if (f.isValid()) f.remove();
    }

    @Override
    public PiScreen open(Plugin owner, String id, Block topLeft, BlockFace face, int cols, int rows) {
        if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST)
            throw new IllegalArgumentException("A screen faces N/S/E/W, not " + face);
        if (cols < 1 || rows < 1 || cols * rows > 128) throw new IllegalArgumentException("1..128 maps");
        String full = full(owner, id);
        World w = topLeft.getWorld();
        int nx = face.getModX(), nz = face.getModZ(), rx = nz, rz = -nx;      // viewer-right across the face

        // every frame cell, row-major
        List<Block> cells = new ArrayList<>(cols * rows);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cells.add(w.getBlockAt(topLeft.getX() + rx * c, topLeft.getY() - r, topLeft.getZ() + rz * c).getRelative(face));
        BoundingBox box = null;
        for (Block b : cells) {
            b.getChunk().load();
            BoundingBox bb = BoundingBox.of(b).expand(0.5);
            box = box == null ? bb : box.union(bb);
        }

        // what's there already: our tagged frames (reuse their maps) + stray frames in the cells (removed)
        int[] reuse = new int[cols * rows];
        Arrays.fill(reuse, -1);
        List<ItemFrame> found = new ArrayList<>();
        boolean intact = true;
        for (Entity e : w.getNearbyEntities(box, x -> x instanceof ItemFrame)) {
            ItemFrame f = (ItemFrame) e;
            String tag = f.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (tag != null && tag.startsWith(full + "|")) {
                found.add(f);
                String[] p = tag.split("\\|");
                try {
                    int idx = Integer.parseInt(p[1]), mapId = Integer.parseInt(p[2]);
                    if (p.length < 5 || Integer.parseInt(p[3]) != cols || Integer.parseInt(p[4]) != rows || idx >= reuse.length
                            || !f.getLocation().getBlock().equals(cells.get(idx)) || f.getFacing() != face) intact = false;
                    else reuse[idx] = mapId;
                } catch (RuntimeException ex) { intact = false; }
            } else if (cells.contains(f.getLocation().getBlock())) {
                f.remove();                                      // something else hanging where the screen goes
            }
        }
        for (int m : reuse) if (m < 0) { intact = false; break; }
        Screen s = new Screen(full, id, w, face, cols, rows, topLeft);
        if (intact && found.size() == cols * rows) {
            found.sort((a, b) -> Integer.compare(index(a), index(b)));
            for (int i = 0; i < found.size(); i++) {
                ItemFrame f = found.get(i);
                @SuppressWarnings("deprecation") MapView v = Bukkit.getMap(reuse[i]);
                if (v == null) { intact = false; break; }
                s.attach(i, f, v);
            }
        } else intact = false;
        if (!intact) {
            for (ItemFrame f : found) if (f.isValid()) f.remove();
            s = new Screen(full, id, w, face, cols, rows, topLeft);
            for (int i = 0; i < cells.size(); i++) {
                Block cell = cells.get(i);
                ItemFrame f = w.spawn(cell.getLocation(), ItemFrame.class, x -> {
                    x.setFacingDirection(face, true);
                    x.setVisible(false);
                    x.setFixed(true);
                    x.setInvulnerable(true);
                    x.setPersistent(true);
                    x.setRotation(Rotation.NONE);
                });
                MapView v = Bukkit.createMap(w);
                v.setTrackingPosition(false);
                v.setUnlimitedTracking(false);
                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                meta.setMapView(v);
                item.setItemMeta(meta);
                f.setItem(item, false);
                f.getPersistentDataContainer().set(key, PersistentDataType.STRING, full + "|" + i + "|" + v.getId() + "|" + cols + "|" + rows);
                s.attach(i, f, v);
            }
        }
        screens.put(full, s);
        return s;
    }

    private int index(ItemFrame f) {
        String t = f.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        try { return t == null ? 0 : Integer.parseInt(t.split("\\|")[1]); } catch (RuntimeException e) { return 0; }
    }

    private Screen screenOf(Entity e) {
        if (!(e instanceof ItemFrame f)) return null;
        String t = f.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (t == null) return null;
        int bar = t.indexOf('|');
        return bar < 0 ? null : screens.get(t.substring(0, bar));
    }

    private boolean tagged(Entity e) {
        return e instanceof ItemFrame f && f.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ protection: a screen can't be taken apart

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent e) { if (tagged(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) { if (tagged(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) { if (tagged(e.getRightClicked())) e.setCancelled(true); }

    @Override
    public PiScreen screenAt(Entity e) { return screenOf(e); }

    // ------------------------------------------------------------------ colour

    @SuppressWarnings("deprecation")
    static byte match(int argb) {
        byte[] t = lut;
        if (t == null) {
            synchronized (PiScreenService.class) {
                if (lut == null) {
                    byte[] table = new byte[65536];
                    for (int i = 0; i < 65536; i++) {
                        int r5 = (i >> 11) & 0x1F, g6 = (i >> 5) & 0x3F, b5 = i & 0x1F;
                        table[i] = MapPalette.matchColor(new Color((r5 << 3) | (r5 >> 2), (g6 << 2) | (g6 >> 4), (b5 << 3) | (b5 >> 2)));
                    }
                    lut = table;
                }
                t = lut;
            }
        }
        return t[((argb & 0xF80000) >> 8) | ((argb & 0xFC00) >> 5) | ((argb & 0xF8) >> 3)];
    }

    // ------------------------------------------------------------------ one screen

    private static final class Screen implements PiScreen {
        final String full, id;
        final World world;
        final BlockFace face;
        final int cols, rows;
        final Block topLeft;
        final PiCanvas canvas;
        final ItemFrame[] frames;
        final byte[][] tiles;
        final int[] tileVersion;

        Screen(String full, String id, World world, BlockFace face, int cols, int rows, Block topLeft) {
            this.full = full; this.id = id; this.world = world; this.face = face; this.cols = cols; this.rows = rows; this.topLeft = topLeft;
            this.canvas = new PiCanvas(cols * MAP, rows * MAP);
            this.frames = new ItemFrame[cols * rows];
            this.tiles = new byte[cols * rows][MAP * MAP];
            this.tileVersion = new int[cols * rows];
            canvas.clear(0x000000);
        }

        void attach(int idx, ItemFrame f, MapView v) {
            frames[idx] = f;
            for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
            v.addRenderer(new Tile(idx));
        }

        private final class Tile extends MapRenderer {
            final int idx;
            int seen = -1;

            Tile(int idx) { super(false); this.idx = idx; }

            @Override
            @SuppressWarnings("deprecation")
            public void render(MapView view, MapCanvas c, Player player) {
                int v = tileVersion[idx];
                if (v == seen) return;
                seen = v;
                byte[] t = tiles[idx];
                for (int y = 0; y < MAP; y++)
                    for (int x = 0; x < MAP; x++) c.setPixel(x, y, t[y * MAP + x]);
            }
        }

        @Override public String id() { return id; }
        @Override public int cols() { return cols; }
        @Override public int rows() { return rows; }
        @Override public int width() { return canvas.width(); }
        @Override public int height() { return canvas.height(); }
        @Override public PiCanvas canvas() { return canvas; }
        @Override public BlockFace face() { return face; }

        @Override
        public void push() {
            int w = canvas.width();
            byte[] tmp = new byte[MAP * MAP];
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++) {
                    for (int y = 0; y < MAP; y++) {
                        int row = (r * MAP + y) * w + c * MAP;
                        for (int x = 0; x < MAP; x++) tmp[y * MAP + x] = match(canvas.px[row + x]);
                    }
                    int idx = r * cols + c;
                    if (!Arrays.equals(tmp, tiles[idx])) {
                        System.arraycopy(tmp, 0, tiles[idx], 0, tmp.length);
                        tileVersion[idx]++;
                    }
                }
        }

        @Override
        public Location center() {
            int rx = face.getModZ(), rz = -face.getModX();
            double x = topLeft.getX() + 0.5 + face.getModX() * 0.5 + rx * (cols / 2.0 - 0.5);
            double z = topLeft.getZ() + 0.5 + face.getModZ() * 0.5 + rz * (cols / 2.0 - 0.5);
            double y = topLeft.getY() + 1 - rows / 2.0;
            return new Location(world, x, y, z);
        }

        @Override
        public boolean isFrame(Entity e) {
            for (ItemFrame f : frames) if (f != null && f.getUniqueId().equals(e.getUniqueId())) return true;
            return false;
        }

        @Override
        public int[] pixelAt(Player p, Entity frame) {
            Vector n = new Vector(face.getModX(), 0, face.getModZ());
            Vector origin = new Vector(topLeft.getX() + 0.5, topLeft.getY() + 0.5, topLeft.getZ() + 0.5).add(n.clone().multiply(0.5));
            Vector eye = p.getEyeLocation().toVector(), dir = p.getEyeLocation().getDirection();
            double denom = dir.dot(n);
            if (Math.abs(denom) < 1e-6) return null;
            double t = origin.clone().subtract(eye).dot(n) / denom;
            if (t < 0) return null;
            Vector hit = eye.add(dir.multiply(t));
            Vector right = new Vector(face.getModZ(), 0, -face.getModX());
            double u = hit.clone().subtract(origin).dot(right) + 0.5;
            double v = topLeft.getY() + 1 - hit.getY();
            int x = (int) Math.floor(u * MAP), y = (int) Math.floor(v * MAP);
            if (x < 0 || y < 0 || x >= width() || y >= height()) return null;
            return new int[]{x, y};
        }
    }
}
