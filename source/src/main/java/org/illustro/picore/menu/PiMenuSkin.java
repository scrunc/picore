package org.illustro.picore.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Universal YAML configuration-driven skinner for PiCore menus.
 * Allows menu titles, filler panes, custom button materials, custom model data,
 * slot mappings, and accent panes to be skinned via YAML configs with {placeholder} support.
 */
public final class PiMenuSkin {

    public static final PiMenuSkin EMPTY = new PiMenuSkin(null);

    private final ConfigurationSection sec;
    private final Map<String, Object> cache = new HashMap<>();

    private PiMenuSkin(ConfigurationSection sec) {
        this.sec = sec;
    }

    /** Wraps a {@code skin:} or menu configuration section (null returns EMPTY defaults). */
    public static PiMenuSkin load(ConfigurationSection sec) {
        return sec == null ? EMPTY : new PiMenuSkin(sec);
    }

    public boolean isEmpty() {
        return sec == null;
    }

    public String prefix(String def) {
        return str("prefix", def);
    }

    public String get(String path, String def) {
        return str(path, def);
    }

    public String title(String view, String def) {
        return str("titles." + view, def);
    }

    public Material filler(String view, Material def) {
        String s = str("fillers." + view, null);
        if (s == null) return def;
        Material m = Material.matchMaterial(s.trim().toUpperCase());
        return m == null ? def : m;
    }

    public int slot(String view, String key, int def) {
        if (sec == null) return def;
        String path = "slots." + view + "." + key;
        Object c = cache.get(path);
        if (c instanceof Integer i) return i;
        int v = sec.isInt(path) ? sec.getInt(path) : def;
        cache.put(path, v);
        return v;
    }

    public int[] slots(String view, String key, int[] def) {
        if (sec == null) return def;
        String path = "slots." + view + "." + key;
        Object c = cache.get(path);
        if (c instanceof int[] a) return a;
        List<Integer> list = sec.getIntegerList(path);
        int[] v = def;
        if (list != null && !list.isEmpty()) {
            v = new int[list.size()];
            for (int i = 0; i < v.length; i++) v[i] = list.get(i);
        }
        cache.put(path, v);
        return v;
    }

    /**
     * Paints accent panes for a view: {@code accents.<view>} is a list of maps containing
     * {@code {material, slots: [..], name?}} painted over fillers.
     */
    public void paintAccents(String view, BiConsumer<Integer, ItemStack> put) {
        if (sec == null || put == null) return;
        List<Map<?, ?>> groups = sec.getMapList("accents." + view);
        if (groups == null || groups.isEmpty()) return;
        for (Map<?, ?> g : groups) {
            Object ms = g.get("material");
            Material mat = ms == null ? null : Material.matchMaterial(String.valueOf(ms).trim().toUpperCase());
            if (mat == null) continue;
            Object nm = g.get("name");
            ItemStack pane = build(mat, nm == null ? " " : String.valueOf(nm), null, null);
            Object slots = g.get("slots");
            if (slots instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Number n) {
                        put.accept(n.intValue(), pane);
                    }
                }
            }
        }
    }

    /** Builds an ItemStack using skinned material, name, lore, and custom model data. */
    public ItemStack item(String key, Material defMat, String defName, List<String> defLore, Map<String, String> vars) {
        Material mat = defMat;
        String name = defName;
        List<String> lore = defLore;
        Integer model = null;
        ConfigurationSection b = sec == null ? null : sec.getConfigurationSection("buttons." + key);
        if (b != null) {
            String ms = b.getString("material");
            if (ms != null) {
                Material mm = Material.matchMaterial(ms.trim().toUpperCase());
                if (mm != null) mat = mm;
            }
            if (b.isString("name")) name = b.getString("name");
            if (b.isList("lore")) lore = b.getStringList("lore");
            if (b.isInt("model")) model = b.getInt("model");
        }
        return build(mat, fill(name, vars), fillAll(lore, vars), model);
    }

    private String str(String path, String def) {
        if (sec == null) return def;
        Object c = cache.get(path);
        if (c instanceof String s) return s;
        String v = sec.isString(path) ? sec.getString(path) : def;
        if (v != null) cache.put(path, v);
        return v;
    }

    public static Map<String, String> vars(Object... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return m;
    }

    public static String fill(String s, Map<String, String> vars) {
        if (s == null || vars == null || vars.isEmpty() || s.indexOf('{') < 0) return s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    private static List<String> fillAll(List<String> lines, Map<String, String> vars) {
        if (lines == null) return null;
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(fill(l, vars));
        return out;
    }

    public static ItemStack build(Material mat, String name, List<String> lore, Integer model) {
        ItemStack it = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String l : lore) colored.add(ChatColor.translateAlternateColorCodes('&', l));
                meta.setLore(colored);
            }
            if (model != null) meta.setCustomModelData(model);
            it.setItemMeta(meta);
        }
        return it;
    }
}

