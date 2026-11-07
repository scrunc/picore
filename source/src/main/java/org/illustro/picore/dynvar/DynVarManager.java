package org.illustro.picore.dynvar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.illustro.picore.PiCore;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Central manager for PiCore Dynamic Variables and Ladders (PrimalVar engine).
 * Loads variables and templates from `variables/` and player variables from `data/player_vars.json`.
 */
public class DynVarManager {

    private static DynVarManager instance;
    private final PiCore plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, StaticVar> statics = new ConcurrentHashMap<>();
    private final Map<String, Ladder> ladders = new ConcurrentHashMap<>();
    private final Map<String, Object> templates = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> playerVars = new ConcurrentHashMap<>();

    private final AtomicBoolean playerDataDirty = new AtomicBoolean(false);
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final Object fileWriteLock = new Object();

    private File variablesFolder;
    private File playerDataFile;

    public DynVarManager(PiCore plugin) {
        this.plugin = plugin;
        instance = this;
        initFiles();
        load();
    }

    public static DynVarManager getInstance() {
        return instance;
    }

    private void initFiles() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        variablesFolder = new File(dataFolder, "variables");
        if (!variablesFolder.exists()) {
            variablesFolder.mkdirs();
        }

        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        playerDataFile = new File(dataDir, "player_vars.json");
    }

    /**
     * Reloads configuration and player data from disk.
     */
    public synchronized void reload() {
        if (playerDataDirty.get()) {
            savePlayerData();
        }
        statics.clear();
        ladders.clear();
        templates.clear();
        load();
        plugin.getLogger().info("[PrimalVar] Loaded variables/ (" + statics.size() + " statics, " + ladders.size() + " ladders, " + templates.size() + " templates).");
    }

    /**
     * Loads definitions from `variables/` and player data.
     */
    public synchronized void load() {
        // 1. Load modular files from variables/ folder (recursively)
        if (variablesFolder != null && variablesFolder.exists()) {
            loadVariablesFolder(variablesFolder);
        }

        // 2. Load player data
        loadPlayerData();
    }

    private void loadVariablesFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                loadVariablesFolder(f);
                continue;
            }
            String nameLower = f.getName().toLowerCase(Locale.ROOT);
            if (!nameLower.endsWith(".yml") && !nameLower.endsWith(".yaml")) continue;

            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                parseVariableFile(cfg, f);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Error parsing " + f.getPath(), ex);
            }
        }
    }

    private void parseVariableFile(YamlConfiguration cfg, File f) {
        String parentName = f.getParentFile() != null ? f.getParentFile().getName().toLowerCase(Locale.ROOT) : "";
        String baseName = f.getName().contains(".")
                ? f.getName().substring(0, f.getName().lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : f.getName().toLowerCase(Locale.ROOT);

        ConfigurationSection root = cfg.getConfigurationSection("primalvar");
        if (root == null) root = cfg;

        // 1. Statics
        ConfigurationSection staticsSec = root.getConfigurationSection("statics");
        if (staticsSec != null) {
            parseStaticsSection(staticsSec);
        } else if (parentName.equals("statics")) {
            parseStaticsSection(root);
        }

        // 2. Ladders
        ConfigurationSection laddersSec = root.getConfigurationSection("ladders");
        if (laddersSec != null) {
            parseLaddersSection(laddersSec);
        } else if (parentName.equals("ladders") || hasNumericKeys(root)) {
            parseSingleLadder(baseName, root);
        }

        // 3. Templates / Vars
        ConfigurationSection templatesSec = root.getConfigurationSection("templates");
        if (templatesSec == null) templatesSec = root.getConfigurationSection("vars");
        if (templatesSec != null) {
            parseTemplatesSection(templatesSec);
        } else if (parentName.equals("templates") || parentName.equals("palettes")) {
            parseTemplatesSection(root);
        }
    }

    private boolean hasNumericKeys(ConfigurationSection sec) {
        if (sec == null) return false;
        for (String k : sec.getKeys(false)) {
            try {
                Integer.parseInt(k.trim());
                return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private void parseStaticsSection(ConfigurationSection sec) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            if (sec.isConfigurationSection(key)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                String typeStr = s != null ? s.getString("type", "global") : "global";
                String def = s != null ? s.getString("default", "none") : "none";
                StaticVar.Scope scope = StaticVar.Scope.fromString(typeStr);
                StaticVar var = new StaticVar(key, scope, def);
                if (scope == StaticVar.Scope.GLOBAL && s != null && s.contains("value")) {
                    var.setGlobalValue(s.getString("value"));
                }
                statics.put(normalizeKey(key), var);
            } else {
                StaticVar var = new StaticVar(key, StaticVar.Scope.GLOBAL, String.valueOf(sec.get(key)));
                var.setGlobalValue(String.valueOf(sec.get(key)));
                statics.put(normalizeKey(key), var);
            }
        }
    }

    private void parseLaddersSection(ConfigurationSection sec) {
        if (sec == null) return;
        for (String ladderKey : sec.getKeys(false)) {
            ConfigurationSection rowsSec = sec.getConfigurationSection(ladderKey);
            if (rowsSec != null) {
                parseSingleLadder(ladderKey, rowsSec);
            }
        }
    }

    private void parseSingleLadder(String ladderKey, ConfigurationSection rowsSec) {
        Ladder ladder = ladders.computeIfAbsent(normalizeKey(ladderKey), Ladder::new);
        for (String rowKey : rowsSec.getKeys(false)) {
            try {
                int rowIndex = Integer.parseInt(rowKey.trim());
                List<?> values = rowsSec.getList(rowKey);
                if (values != null) {
                    ladder.setRow(rowIndex, new ArrayList<>(values));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void parseTemplatesSection(ConfigurationSection sec) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            Object val = sec.get(key);
            if (val != null) {
                templates.put(normalizeKey(key), val);
            }
        }
    }

    private void loadPlayerData() {
        if (playerDataFile == null || !playerDataFile.exists()) return;
        synchronized (fileWriteLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
                Map<String, Map<String, String>> raw = gson.fromJson(reader, type);
                if (raw != null) {
                    playerVars.clear();
                    for (Map.Entry<String, Map<String, String>> e : raw.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(e.getKey());
                            Map<String, String> normalizedMap = new ConcurrentHashMap<>();
                            if (e.getValue() != null) {
                                for (Map.Entry<String, String> ve : e.getValue().entrySet()) {
                                    if (ve.getKey() != null && ve.getValue() != null) {
                                        normalizedMap.put(normalizeKey(ve.getKey()), ve.getValue());
                                    }
                                }
                            }
                            playerVars.put(uuid, normalizedMap);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                playerDataDirty.set(false);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Failed to load player_vars.json", ex);
            }
        }
    }

    /**
     * Schedules an asynchronous debounced disk flush of `player_vars.json` (coalesces rapid mutations).
     */
    public void savePlayerDataAsync() {
        playerDataDirty.set(true);
        if (!plugin.isEnabled()) {
            savePlayerData();
            return;
        }
        if (flushScheduled.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                flushScheduled.set(false);
                if (playerDataDirty.compareAndSet(true, false)) {
                    writePlayerSnapshotToDisk(createPlayerSnapshot());
                }
            }, 40L);
        }
    }

    /**
     * Immediately flushes player data to disk (used on server shutdown and reload).
     */
    public synchronized void savePlayerData() {
        playerDataDirty.set(false);
        writePlayerSnapshotToDisk(createPlayerSnapshot());
    }

    private Map<String, Map<String, String>> createPlayerSnapshot() {
        Map<String, Map<String, String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, String>> e : playerVars.entrySet()) {
            snapshot.put(e.getKey().toString(), new LinkedHashMap<>(e.getValue()));
        }
        return snapshot;
    }

    private void writePlayerSnapshotToDisk(Map<String, Map<String, String>> snapshot) {
        if (playerDataFile == null) return;
        synchronized (fileWriteLock) {
            File tempFile = new File(playerDataFile.getParentFile(), "player_vars.json.tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, writer);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Failed to write player_vars.json.tmp", ex);
                return;
            }
            try {
                Files.move(tempFile.toPath(), playerDataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Failed to finalize player_vars.json", ex);
            }
        }
    }

    /**
     * Persists dynamically created static and ladder definitions into `variables/custom.yml`.
     */
    public synchronized void saveVariableConfig() {
        if (variablesFolder == null) return;
        if (!variablesFolder.exists()) {
            variablesFolder.mkdirs();
        }
        File customFile = new File(variablesFolder, "custom.yml");
        YamlConfiguration cfg = customFile.exists() ? YamlConfiguration.loadConfiguration(customFile) : new YamlConfiguration();

        // Statics
        ConfigurationSection staticsSec = cfg.isConfigurationSection("statics")
                ? cfg.getConfigurationSection("statics")
                : cfg.createSection("statics");
        for (StaticVar v : statics.values()) {
            ConfigurationSection vSec = staticsSec.isConfigurationSection(v.getName())
                    ? staticsSec.getConfigurationSection(v.getName())
                    : staticsSec.createSection(v.getName());
            vSec.set("type", v.getScope().name().toLowerCase(Locale.ROOT));
            vSec.set("default", v.getDefaultValue());
            if (v.isGlobal()) {
                vSec.set("value", v.getGlobalValue());
            }
        }

        // Ladders
        ConfigurationSection laddersSec = cfg.isConfigurationSection("ladders")
                ? cfg.getConfigurationSection("ladders")
                : cfg.createSection("ladders");
        for (Ladder l : ladders.values()) {
            ConfigurationSection lSec = laddersSec.isConfigurationSection(l.getName())
                    ? laddersSec.getConfigurationSection(l.getName())
                    : laddersSec.createSection(l.getName());
            List<Integer> sortedRows = new ArrayList<>(l.getRows().keySet());
            Collections.sort(sortedRows);
            for (Integer rowKey : sortedRows) {
                lSec.set(String.valueOf(rowKey), l.getRows().get(rowKey));
            }
        }

        if (plugin.isEnabled() && Bukkit.isPrimaryThread()) {
            final String yamlData = cfg.saveToString();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeCustomConfigFile(customFile, yamlData));
        } else {
            writeCustomConfigFile(customFile, cfg.saveToString());
        }
    }

    private void writeCustomConfigFile(File targetFile, String content) {
        synchronized (fileWriteLock) {
            File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writer.write(content);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Failed to write " + tempFile.getName(), ex);
                return;
            }
            try {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "[PrimalVar] Failed to finalize " + targetFile.getName(), ex);
            }
        }
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    // --- Query & Mutation API ---

    public boolean hasStatic(String name) {
        return name != null && statics.containsKey(normalizeKey(name));
    }

    public StaticVar getStaticVar(String name) {
        return name != null ? statics.get(normalizeKey(name)) : null;
    }

    public String getStaticValue(String name) {
        StaticVar var = getStaticVar(name);
        return var != null ? var.getGlobalValue() : null;
    }

    public boolean setStaticValue(String name, String value) {
        StaticVar var = getStaticVar(name);
        if (var != null && var.isGlobal()) {
            var.setGlobalValue(value);
            saveVariableConfig();
            return true;
        }
        return false;
    }

    public String getPlayerValue(UUID uuid, String name) {
        if (uuid == null || name == null) return "none";
        String key = normalizeKey(name);
        Map<String, String> vars = playerVars.get(uuid);
        if (vars != null && vars.containsKey(key)) {
            return vars.get(key);
        }
        StaticVar staticDef = statics.get(key);
        return staticDef != null ? staticDef.getDefaultValue() : "none";
    }

    public void setPlayerValue(UUID uuid, String name, String value) {
        if (uuid == null || name == null) return;
        String key = normalizeKey(name);
        Map<String, String> vars = playerVars.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());
        vars.put(key, value != null ? value : "none");
        savePlayerDataAsync();
    }

    public boolean mathPlayerValue(UUID uuid, String name, String op, double operand, Double min, Double max) {
        if (uuid == null || name == null || op == null) return false;
        String currentStr = getPlayerValue(uuid, name);
        double current;
        try {
            current = Double.parseDouble(currentStr);
        } catch (NumberFormatException e) {
            current = 0.0;
        }

        double result = switch (op.toLowerCase(Locale.ROOT)) {
            case "+", "add" -> current + operand;
            case "-", "sub" -> current - operand;
            case "*", "mul" -> current * operand;
            case "/", "div" -> operand == 0 ? current : current / operand;
            case "%", "mod" -> operand == 0 ? current : current % operand;
            default -> current;
        };

        if (min != null) result = Math.max(min, result);
        if (max != null) result = Math.min(max, result);

        String finalVal = (result == (long) result) ? String.valueOf((long) result) : String.valueOf(result);
        setPlayerValue(uuid, name, finalVal);
        return true;
    }

    public boolean createStatic(String name, StaticVar.Scope scope, String defaultValue) {
        if (name == null || name.isBlank()) return false;
        String key = normalizeKey(name);
        statics.put(key, new StaticVar(key, scope, defaultValue));
        saveVariableConfig();
        return true;
    }

    public Ladder getLadder(String name) {
        return name != null ? ladders.get(normalizeKey(name)) : null;
    }

    public boolean createLadder(String name, List<Object> defaultRow) {
        if (name == null || name.isBlank()) return false;
        String key = normalizeKey(name);
        Ladder ladder = ladders.computeIfAbsent(key, Ladder::new);
        if (defaultRow != null && !defaultRow.isEmpty()) {
            ladder.addRow(defaultRow);
        }
        saveVariableConfig();
        return true;
    }

    public String getLadderValue(String ladderName, int row, int col) {
        Ladder ladder = getLadder(ladderName);
        if (ladder == null) return "";
        return ladder.getString(row, col);
    }

    public boolean setLadderValue(String ladderName, int row, int col, Object value) {
        if (ladderName == null || ladderName.isBlank()) return false;
        String key = normalizeKey(ladderName);
        Ladder ladder = ladders.computeIfAbsent(key, Ladder::new);
        ladder.set(row, col, value);
        saveVariableConfig();
        return true;
    }

    public Map<String, StaticVar> getAllStatics() {
        return Collections.unmodifiableMap(statics);
    }

    public Map<String, Ladder> getAllLadders() {
        return Collections.unmodifiableMap(ladders);
    }

    public Object getTemplate(String name) {
        return name != null ? templates.get(normalizeKey(name)) : null;
    }

    public String getTemplateString(String name) {
        Object val = getTemplate(name);
        if (val == null) return "";
        if (val instanceof List<?> l) {
            List<String> lines = new ArrayList<>();
            for (Object o : l) lines.add(String.valueOf(o));
            return String.join("\n", lines);
        }
        return String.valueOf(val);
    }

    public List<String> getTemplateLines(String name) {
        Object val = getTemplate(name);
        if (val == null) return Collections.emptyList();
        if (val instanceof List<?> l) {
            List<String> lines = new ArrayList<>();
            for (Object o : l) lines.add(String.valueOf(o));
            return lines;
        }
        return Collections.singletonList(String.valueOf(val));
    }

    public Map<String, Object> getAllTemplates() {
        return Collections.unmodifiableMap(templates);
    }
}
