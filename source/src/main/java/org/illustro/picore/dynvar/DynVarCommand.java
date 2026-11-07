package org.illustro.picore.dynvar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.illustro.picore.PiCore;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Command handler and tab completer for /primalvar (aliases: /pv, /dynvar, /dv, /dyn).
 */
public class DynVarCommand implements CommandExecutor, TabCompleter {

    private final PiCore plugin;
    private final DynVarManager manager;

    public DynVarCommand(PiCore plugin, DynVarManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6PrimalVar&8] &7" + text));
    }

    /**
     * Resolves a player's UUID without triggering blocking main-thread Mojang HTTP lookups.
     */
    private UUID resolvePlayerUuid(String input) {
        if (input == null || input.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getUniqueId();

        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {}

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null) return cached.getUniqueId();

        Player partial = Bukkit.getPlayer(input);
        if (partial != null) return partial.getUniqueId();

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + input).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("picore.primalvar.admin") && !sender.hasPermission("picore.dynvar.admin") && !sender.isOp()) {
            msg(sender, "&cYou do not have permission to use /" + label + ".");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // 1. RELOAD
        if (sub.equals("reload")) {
            manager.reload();
            msg(sender, "&aReloaded &fvariables/ &asuccessfully (&f"
                    + manager.getAllStatics().size() + " &astatics, &f"
                    + manager.getAllLadders().size() + " &aladders, &f"
                    + manager.getAllTemplates().size() + " &atemplates).");
            return true;
        }

        // 2. LIST
        if (sub.equals("list")) {
            msg(sender, "&6Statics (" + manager.getAllStatics().size() + "): &f" + String.join(", ", manager.getAllStatics().keySet()));
            msg(sender, "&6Ladders (" + manager.getAllLadders().size() + "): &f" + String.join(", ", manager.getAllLadders().keySet()));
            if (!manager.getAllTemplates().isEmpty()) {
                msg(sender, "&6Templates (" + manager.getAllTemplates().size() + "): &f" + String.join(", ", manager.getAllTemplates().keySet()));
            }
            return true;
        }

        // 3. CREATE
        if (sub.equals("create")) {
            if (args.length < 3) {
                msg(sender, "&cUsage: /" + label + " create static <name> <global|player> [default]");
                msg(sender, "&cUsage: /" + label + " create ladder <name> [val1,val2,...]");
                return true;
            }
            String type = args[1].toLowerCase(Locale.ROOT);
            String name = args[2].toLowerCase(Locale.ROOT);

            if (type.equals("static")) {
                if (args.length < 4) {
                    msg(sender, "&cSpecify scope: /" + label + " create static <name> <global|player> [default]");
                    return true;
                }
                StaticVar.Scope scope = StaticVar.Scope.fromString(args[3]);
                String def = args.length >= 5 ? join(args, 4) : "none";
                manager.createStatic(name, scope, def);
                msg(sender, "&aCreated static variable &e" + name + " &a(scope: &f" + scope.name() + "&a, default: &f" + def + "&a).");
                return true;
            } else if (type.equals("ladder")) {
                List<Object> initialRow = null;
                if (args.length >= 4) {
                    String[] parts = join(args, 3).split(",");
                    initialRow = new ArrayList<>(Arrays.asList(parts));
                }
                manager.createLadder(name, initialRow);
                msg(sender, "&aCreated ladder &e" + name + (initialRow != null ? " with row 0: &f" + initialRow : "") + "&a.");
                return true;
            } else {
                msg(sender, "&cUnknown type. Usage: /" + label + " create <static|ladder> ...");
                return true;
            }
        }

        // 4. SET
        if (sub.equals("set")) {
            if (args.length < 4) {
                msg(sender, "&cUsage: /" + label + " set static <var> <value>");
                msg(sender, "&cUsage: /" + label + " set player <player> <var> <value>");
                return true;
            }
            String targetType = args[1].toLowerCase(Locale.ROOT);
            if (targetType.equals("static")) {
                String var = args[2].toLowerCase(Locale.ROOT);
                String val = join(args, 3);
                if (manager.setStaticValue(var, val)) {
                    msg(sender, "&aSet global static &e" + var + " &a= &f" + val);
                } else {
                    msg(sender, "&cFailed. Ensure static &e" + var + " &cexists and is of type 'global'.");
                }
                return true;
            } else if (targetType.equals("player")) {
                if (args.length < 5) {
                    msg(sender, "&cUsage: /" + label + " set player <player> <var> <value>");
                    return true;
                }
                UUID uuid = resolvePlayerUuid(args[2]);
                String var = args[3].toLowerCase(Locale.ROOT);
                String val = join(args, 4);
                manager.setPlayerValue(uuid, var, val);
                msg(sender, "&aSet player &e" + args[2] + " &avar &e" + var + " &a= &f" + val);
                return true;
            }
        }

        // 5. GET
        if (sub.equals("get")) {
            if (args.length < 3) {
                msg(sender, "&cUsage: /" + label + " get static <var>");
                msg(sender, "&cUsage: /" + label + " get player <player> <var>");
                return true;
            }
            String targetType = args[1].toLowerCase(Locale.ROOT);
            if (targetType.equals("static")) {
                String var = args[2].toLowerCase(Locale.ROOT);
                String val = manager.getStaticValue(var);
                msg(sender, "&7Static &e" + var + " &7= &f" + (val != null ? val : "&c(not found)"));
                return true;
            } else if (targetType.equals("player")) {
                if (args.length < 4) {
                    msg(sender, "&cUsage: /" + label + " get player <player> <var>");
                    return true;
                }
                UUID uuid = resolvePlayerUuid(args[2]);
                String var = args[3].toLowerCase(Locale.ROOT);
                String val = manager.getPlayerValue(uuid, var);
                msg(sender, "&7Player &e" + args[2] + " &7var &e" + var + " &7= &f" + val);
                return true;
            }
        }

        // 6. LADDER
        if (sub.equals("ladder")) {
            if (args.length < 2) {
                msg(sender, "&cUsage: /" + label + " ladder <set|add|get> ...");
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);

            if (action.equals("set")) {
                if (args.length < 6) {
                    msg(sender, "&cUsage: /" + label + " ladder set <name> <row> <col> <value>");
                    return true;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                try {
                    int row = Integer.parseInt(args[3]);
                    int col = Integer.parseInt(args[4]);
                    String val = join(args, 5);
                    manager.setLadderValue(name, row, col, val);
                    msg(sender, "&aUpdated ladder &e" + name + "&a[&e" + row + "&a, &e" + col + "&a] = &f" + val);
                } catch (NumberFormatException e) {
                    msg(sender, "&cRow and column must be integers.");
                }
                return true;
            } else if (action.equals("add")) {
                if (args.length < 4) {
                    msg(sender, "&cUsage: /" + label + " ladder add <name> <val1,val2,val3...>");
                    return true;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                String[] items = join(args, 3).split(",");
                Ladder ladder = manager.getLadder(name);
                if (ladder == null) {
                    manager.createLadder(name, new ArrayList<>(Arrays.asList(items)));
                } else {
                    ladder.addRow(new ArrayList<>(Arrays.asList(items)));
                    manager.saveVariableConfig();
                }
                msg(sender, "&aAdded new row to ladder &e" + name + "&a: &f" + Arrays.toString(items));
                return true;
            } else if (action.equals("get")) {
                if (args.length < 4) {
                    msg(sender, "&cUsage: /" + label + " ladder get <name> <row> [col]");
                    return true;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                try {
                    int row = Integer.parseInt(args[3]);
                    Ladder ladder = manager.getLadder(name);
                    if (ladder == null) {
                        msg(sender, "&cLadder " + name + " not found.");
                        return true;
                    }
                    if (args.length >= 5) {
                        int col = Integer.parseInt(args[4]);
                        msg(sender, "&7" + name + "[" + row + ", " + col + "] = &f" + ladder.getString(row, col));
                    } else {
                        List<Object> r = ladder.getRow(row);
                        msg(sender, "&7" + name + "[" + row + "] = &f" + (r != null ? r.toString() : "&c(empty)"));
                    }
                } catch (NumberFormatException e) {
                    msg(sender, "&cRow and column must be integers.");
                }
                return true;
            } else if (action.equals("floor")) {
                if (args.length < 4) {
                    msg(sender, "&cUsage: /" + label + " ladder floor <name> <score> [col]");
                    return true;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                try {
                    double score = Double.parseDouble(args[3]);
                    Ladder ladder = manager.getLadder(name);
                    if (ladder == null) {
                        msg(sender, "&cLadder " + name + " not found.");
                        return true;
                    }
                    int col = args.length >= 5 ? Integer.parseInt(args[4]) : 1;
                    Object val = ladder.getFloor(score, col);
                    msg(sender, "&7" + name + "[floor(" + score + "), col " + col + "] = &f" + (val != null ? val : "&c(empty)"));
                } catch (NumberFormatException e) {
                    msg(sender, "&cScore must be a number.");
                }
                return true;
            }
        }

        // 7. MATH
        if (sub.equals("math")) {
            // /dynvar math player <player> <var> <+|*|-|/|%> <operand> [min:X] [max:X]
            if (args.length < 6 || !args[1].equalsIgnoreCase("player")) {
                msg(sender, "&cUsage: /" + label + " math player <player> <var> <+|-|*|/|%> <value> [min:X] [max:X]");
                return true;
            }
            String playerName = args[2];
            UUID uuid = resolvePlayerUuid(playerName);
            String var = args[3].toLowerCase(Locale.ROOT);
            String op = args[4];

            double operand;
            try {
                operand = Double.parseDouble(args[5]);
            } catch (NumberFormatException e) {
                msg(sender, "&cOperand must be a number: " + args[5]);
                return true;
            }

            Double min = null;
            Double max = null;
            for (int i = 6; i < args.length; i++) {
                String token = args[i].toLowerCase(Locale.ROOT);
                if (token.startsWith("min:")) {
                    try { min = Double.parseDouble(token.substring(4)); } catch (NumberFormatException ignored) {}
                } else if (token.startsWith("max:")) {
                    try { max = Double.parseDouble(token.substring(4)); } catch (NumberFormatException ignored) {}
                }
            }

            manager.mathPlayerValue(uuid, var, op, operand, min, max);
            String newVal = manager.getPlayerValue(uuid, var);
            msg(sender, "&aUpdated &e" + playerName + " &a(&e" + var + "&a) " + op + " " + operand + " -> &f" + newVal);
            return true;
        }

        // 8. EVAL (Direct expression evaluation)
        if (sub.equals("eval")) {
            if (args.length < 2) {
                msg(sender, "&cUsage: /" + label + " eval <expression>");
                return true;
            }
            String expr = join(args, 1);
            String result = MathEvaluator.eval(expr);
            msg(sender, "&e" + expr + " &7= &a" + result);
            return true;
        }

        sendHelp(sender, label);
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        msg(sender, "&6&lPiCore Dynamic Variables (/" + label + ")");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "  &e/" + label + " reload &7- Reload variables/ and player data\n" +
                "  &e/" + label + " list &7- List all registered statics and ladders\n" +
                "  &e/" + label + " create static <name> <global|player> [default] &7- Create static var\n" +
                "  &e/" + label + " create ladder <name> [val1,val2...] &7- Create ladder\n" +
                "  &e/" + label + " set static <var> <value> &7- Set global static value\n" +
                "  &e/" + label + " set player <player> <var> <value> &7- Set player variable\n" +
                "  &e/" + label + " get <static|player> ... &7- Read variable value\n" +
                "  &e/" + label + " ladder <set|add|get> ... &7- Manage matrix ladder rows/cols\n" +
                "  &e/" + label + " math player <player> <var> <+|-|*|/|%> <val> [min:X] [max:X] &7- Math mutation\n" +
                "  &e/" + label + " eval <expr> &7- Evaluate arithmetic expression"
        ));
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "list", "create", "set", "get", "ladder", "math", "eval"), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "create" -> filter(Arrays.asList("static", "ladder"), args[1]);
                case "get", "set" -> filter(Arrays.asList("static", "player"), args[1]);
                case "ladder" -> filter(Arrays.asList("set", "add", "get", "floor"), args[1]);
                case "math" -> filter(Collections.singletonList("player"), args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("get")) {
                if (args[1].equalsIgnoreCase("static")) {
                    return filter(new ArrayList<>(manager.getAllStatics().keySet()), args[2]);
                } else if (args[1].equalsIgnoreCase("player")) {
                    return null; // suggest online players
                }
            } else if (sub.equals("create")) {
                return Collections.singletonList("<name>");
            } else if (sub.equals("ladder")) {
                return filter(new ArrayList<>(manager.getAllLadders().keySet()), args[2]);
            } else if (sub.equals("math")) {
                return null; // suggest online players
            }
        }

        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create") && args[1].equalsIgnoreCase("static")) {
                return filter(Arrays.asList("global", "player"), args[3]);
            }
            if ((sub.equals("set") || sub.equals("get")) && args[1].equalsIgnoreCase("player")) {
                return filter(new ArrayList<>(manager.getAllStatics().keySet()), args[3]);
            }
            if (sub.equals("math") && args[1].equalsIgnoreCase("player")) {
                return filter(new ArrayList<>(manager.getAllStatics().keySet()), args[3]);
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("math")) {
            return filter(Arrays.asList("+", "-", "*", "/", "%"), args[4]);
        }

        return list;
    }

    private List<String> filter(List<String> src, String prefix) {
        List<String> res = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) res.add(s);
        }
        return res;
    }
}
