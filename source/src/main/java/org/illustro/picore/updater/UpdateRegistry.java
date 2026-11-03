package org.illustro.picore.updater;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.illustro.picore.PiCore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all client plugins that depend on PiCore.
 * Coordinates version checks, compatibility reports, and updates.
 */
public class UpdateRegistry {

    private static final UpdateRegistry INSTANCE = new UpdateRegistry();

    public static UpdateRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<String, ClientRegistration> clients = new ConcurrentHashMap<>();

    private UpdateRegistry() { }

    /**
     * Registers a downstream plugin with PiCore.
     */
    public synchronized ClientRegistration register(Plugin plugin, String minCoreVersion, String githubRepo) {
        if (plugin == null) return null;

        ClientRegistration reg = new ClientRegistration(plugin, minCoreVersion, githubRepo);
        clients.put(plugin.getName().toLowerCase(), reg);

        String coreVersion = PiCore.getInstance() != null ? PiCore.getInstance().getDescription().getVersion() : "1.0.0";
        reg.recalculateStatus(coreVersion);

        if (reg.getStatus().isCritical()) {
            Bukkit.getConsoleSender().sendMessage("§8[§bPiCore§8] §cWARNING: " + plugin.getName() + " requires PiCore >= "
                    + minCoreVersion + ", but current running PiCore is v" + coreVersion + "!");
        } else {
            Bukkit.getConsoleSender().sendMessage("§8[§bPiCore§8] §aRegistered client module: §e" + plugin.getName()
                    + " §7(v" + plugin.getDescription().getVersion() + ", Req Core: " + minCoreVersion + ")");
        }

        return reg;
    }

    public synchronized void unregister(String pluginName) {
        if (pluginName != null) {
            clients.remove(pluginName.toLowerCase());
        }
    }

    public ClientRegistration getClient(String pluginName) {
        if (pluginName == null) return null;
        return clients.get(pluginName.toLowerCase());
    }

    public Collection<ClientRegistration> getAllClients() {
        return Collections.unmodifiableCollection(clients.values());
    }

    public int getClientCount() {
        return clients.size();
    }

    public void recalculateAll() {
        String coreVersion = PiCore.getInstance() != null ? PiCore.getInstance().getDescription().getVersion() : "1.0.0";
        for (ClientRegistration reg : clients.values()) {
            reg.recalculateStatus(coreVersion);
        }
    }
}

