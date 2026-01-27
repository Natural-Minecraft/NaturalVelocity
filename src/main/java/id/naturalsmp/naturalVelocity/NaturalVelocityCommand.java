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
                    mm.deserialize("<gradient:#00AAFF:#55FF55><bold>NaturalVelocity Help</bold></gradient>"));
            invocation.source()
                    .sendMessage(mm.deserialize("<gray>» <white>/nv reload <gray>- Reload configuration and icon"));
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
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return Arrays.asList("reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
