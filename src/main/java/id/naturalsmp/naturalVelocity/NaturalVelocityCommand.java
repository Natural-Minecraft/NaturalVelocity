package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NaturalVelocityCommand implements SimpleCommand {

    private final NaturalVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public NaturalVelocityCommand(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            invocation.source().sendMessage(
                    mm.deserialize("<gradient:#00AAFF:#55FF55><bold>NaturalVelocity v2.0 Help</bold></gradient>"));
            invocation.source()
                    .sendMessage(
                            mm.deserialize("<gray>» <white>/nv reload <gray>- Reload configuration, icon & head MOTD"));
            invocation.source()
                    .sendMessage(
                            mm.deserialize("<gray>» <white>/nv process-motd [%] <gray>- Generate head pixel art MOTD"));
            invocation.source()
                    .sendMessage(
                            mm.deserialize(
                                    "<gray>» <white>/nv process-maintenance-motd [%] <gray>- Generate maintenance head banner"));
            invocation.source()
                    .sendMessage(
                            mm.deserialize(
                                    "<gray>» <white>/nv process-tempclosed-motd [%] <gray>- Generate temp-closed head banner"));
            invocation.source()
                    .sendMessage(mm.deserialize("<gray>» <white>/nv status <gray>- Show HeadMOTD status"));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!invocation.source().hasPermission("naturalsmp.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>You do not have permission!"));
                return;
            }

            plugin.reload();
            invocation.source().sendMessage(mm.deserialize(
                    "<gradient:#00AAFF:#55FF55><bold>NaturalVelocity</bold></gradient> <gray>» <green>Configuration reloaded successfully!"));
            return;
        }

        if (args[0].equalsIgnoreCase("process-motd")) {
            if (!invocation.source().hasPermission("naturalsmp.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>You do not have permission!"));
                return;
            }

            int percentage = 100;
            if (args.length > 1) {
                try {
                    percentage = Integer.parseInt(args[1]);
                    if (percentage < 1)
                        percentage = 1;
                    if (percentage > 100)
                        percentage = 100;
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(mm.deserialize(
                            "<red>Invalid percentage! Usage: /nv process-motd [1-100]"));
                    return;
                }
            }
            plugin.processMotd(invocation.source(), percentage);
            return;
        }

        if (args[0].equalsIgnoreCase("process-maintenance-motd")) {
            if (!invocation.source().hasPermission("naturalsmp.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>You do not have permission!"));
                return;
            }

            int percentage = 100;
            if (args.length > 1) {
                try {
                    percentage = Integer.parseInt(args[1]);
                    if (percentage < 1)
                        percentage = 1;
                    if (percentage > 100)
                        percentage = 100;
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(mm.deserialize(
                            "<red>Invalid percentage! Usage: /nv process-maintenance-motd [1-100]"));
                    return;
                }
            }
            plugin.processMaintenanceMotd(invocation.source(), percentage);
            return;
        }

        if (args[0].equalsIgnoreCase("process-tempclosed-motd")) {
            if (!invocation.source().hasPermission("naturalsmp.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>You do not have permission!"));
                return;
            }

            int percentage = 100;
            if (args.length > 1) {
                try {
                    percentage = Integer.parseInt(args[1]);
                    if (percentage < 1)
                        percentage = 1;
                    if (percentage > 100)
                        percentage = 100;
                } catch (NumberFormatException e) {
                    invocation.source().sendMessage(mm.deserialize(
                            "<red>Invalid percentage! Usage: /nv process-tempclosed-motd [1-100]"));
                    return;
                }
            }
            plugin.processTempClosedMotd(invocation.source(), percentage);
            return;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (!invocation.source().hasPermission("naturalsmp.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>You do not have permission!"));
                return;
            }

            invocation.source().sendMessage(mm.deserialize(
                    "<gradient:#00AAFF:#55FF55><bold>NaturalVelocity Status</bold></gradient>"));
            invocation.source().sendMessage(mm.deserialize(
                    "<gray>» <white>Head MOTD Enabled: " +
                            (plugin.isHeadMotdEnabled() ? "<green>YES" : "<red>NO")));
            invocation.source().sendMessage(mm.deserialize(
                    "<gray>» <white>Head MOTD Active: " +
                            (plugin.isHeadMotdActive() ? "<green>YES <gray>(heads loaded)"
                                    : "<red>NO <gray>(no heads cached)")));
            invocation.source().sendMessage(mm.deserialize(
                    "<gray>» <white>Temp-Closed: " +
                            (plugin.isTempClosedActive() ? "<red>ON" : "<green>OFF")));
            invocation.source().sendMessage(mm.deserialize(
                    "<gray>» <white>Maintenance: " +
                            (plugin.isMaintenanceActive() ? "<red>ON" : "<green>OFF")));
            invocation.source().sendMessage(mm.deserialize(
                    "<gray>» <white>Online Players: <aqua>" + plugin.getServer().getPlayerCount()));
            return;
        }

        // Unknown subcommand
        invocation.source().sendMessage(mm.deserialize(
                "<red>Unknown subcommand. Use <white>/nv help <red>for help."));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return Arrays.asList("reload", "help", "process-motd", "process-maintenance-motd", "process-tempclosed-motd", "status").stream()
                    .filter(s -> s.startsWith(args.length == 0 ? "" : args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
