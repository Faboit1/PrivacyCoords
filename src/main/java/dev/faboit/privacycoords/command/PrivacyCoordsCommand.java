package dev.faboit.privacycoords.command;

import dev.faboit.privacycoords.PrivacyCoordsPlugin;
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.offset.CoordinateOffset;
import dev.faboit.privacycoords.offset.OffsetService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /privacycoords <enable|disable|status|reroll|reload> [player]}
 */
public final class PrivacyCoordsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS =
            Arrays.asList("enable", "disable", "status", "reroll", "reload");

    private final PrivacyCoordsPlugin plugin;
    private final OffsetService service;

    public PrivacyCoordsCommand(PrivacyCoordsPlugin plugin, OffsetService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PrivacyCoordsConfig config = service.getConfig();
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        if (action.equals("reload")) {
            if (!sender.hasPermission("privacycoords.reload")) {
                sender.sendMessage(config.message("no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(service.getConfig().message("reloaded"));
            return true;
        }

        if (!SUB_COMMANDS.contains(action)) {
            sender.sendMessage(config.message("usage"));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("privacycoords.others")) {
                sender.sendMessage(config.message("no-permission"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(config.message("unknown-player", "player", args[1]));
                return true;
            }
        } else if (sender instanceof Player) {
            if (!sender.hasPermission("privacycoords.use")) {
                sender.sendMessage(config.message("no-permission"));
                return true;
            }
            target = (Player) sender;
        } else {
            sender.sendMessage(config.message("players-only"));
            return true;
        }

        boolean self = sender.equals(target);
        switch (action) {
            case "enable":
                setEnabled(sender, target, true, self, config);
                return true;
            case "disable":
                setEnabled(sender, target, false, self, config);
                return true;
            case "reroll":
                reroll(sender, target, self, config);
                return true;
            default:
                status(sender, target, self, config);
                return true;
        }
    }

    private void setEnabled(CommandSender sender, Player target, boolean enabled, boolean self,
                            PrivacyCoordsConfig config) {
        if (service.isEnabled(target.getUniqueId()) == enabled) {
            sender.sendMessage(config.message(enabled ? "already-enabled" : "already-disabled"));
            return;
        }

        service.setEnabled(target.getUniqueId(), enabled);

        // Turning the feature on or off cannot be applied to a client that is already holding a
        // world full of chunks, so it lands on the next login - unless we kick them into one.
        boolean immediate = config.isKickOnChange();
        if (self) {
            sender.sendMessage(config.message(enabled
                    ? (immediate ? "enabled-now" : "enabled")
                    : (immediate ? "disabled-now" : "disabled")));
        } else {
            sender.sendMessage(config.message("changed-other",
                    "player", target.getName(),
                    "state", config.rawMessage(enabled ? "state-on" : "state-off")));
            target.sendMessage(config.message(enabled
                    ? (immediate ? "enabled-now" : "enabled")
                    : (immediate ? "disabled-now" : "disabled")));
        }

        if (immediate) {
            kick(target, config);
        }
    }

    private void reroll(CommandSender sender, Player target, boolean self, PrivacyCoordsConfig config) {
        service.reroll(target.getUniqueId());
        if (self) {
            sender.sendMessage(config.message("rerolled"));
        } else {
            sender.sendMessage(config.message("rerolled-other", "player", target.getName()));
            target.sendMessage(config.message("rerolled"));
        }
        if (config.isKickOnChange()) {
            kick(target, config);
        }
    }

    private void status(CommandSender sender, Player target, boolean self, PrivacyCoordsConfig config) {
        String status = describe(target, config);
        sender.sendMessage(self
                ? config.prefixed(status)
                : config.message("status-other", "player", target.getName(), "status", status));
    }

    /** The status as a raw, uncoloured fragment so it can be embedded in another message. */
    private String describe(Player target, PrivacyCoordsConfig config) {
        if (!service.isEnabled(target.getUniqueId())) {
            return config.rawMessage("status-off");
        }
        CoordinateOffset active = service.activeOffset(target.getUniqueId());
        if (active.isZero()) {
            return config.rawMessage("status-on-pending");
        }
        return config.rawMessage("status-on",
                "x", formatSigned(active.getX()),
                "z", formatSigned(active.getZ()));
    }

    private static String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private void kick(Player target, PrivacyCoordsConfig config) {
        // Never kick from inside a packet or async context; the command already runs on the
        // server thread, but scheduling keeps the ordering obvious if that ever changes.
        Bukkit.getScheduler().runTask(plugin, () -> target.kickPlayer(config.getKickMessage()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    options.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")
                && sender.hasPermission("privacycoords.others")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(online.getName());
                }
            }
        }
        return options;
    }
}
