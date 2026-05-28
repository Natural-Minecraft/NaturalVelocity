package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.stream.Collectors;

public class MaintenanceCommand implements SimpleCommand {

    private final NaturalVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MaintenanceCommand(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        boolean hasAdmin = invocation.source() instanceof com.velocitypowered.api.proxy.ConsoleCommandSource || 
                           invocation.source().hasPermission("naturalsmp.admin");
        if (!hasAdmin) {
            invocation.source().sendMessage(mm.deserialize("<red>Anda tidak memiliki permission untuk menggunakan perintah ini!"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(invocation.source());
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> handleOn(invocation, args);
            case "off" -> handleOff(invocation, args);
            case "add" -> handleAdd(invocation, args);
            case "remove" -> handleRemove(invocation, args);
            case "status" -> handleStatus(invocation);
            default -> invocation.source().sendMessage(mm.deserialize("<red>Subcommand tidak dikenal. Gunakan /maintenance help"));
        }
    }

    private void sendHelp(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(mm.deserialize("<gradient:#FF4444:#FF8800><bold>Maintenance System Help</bold></gradient>"));
        source.sendMessage(mm.deserialize("<gray>» <white>/maintenance on [time] [server] <gray>- Mengaktifkan maintenance (countdown opsi)"));
        source.sendMessage(mm.deserialize("<gray>» <white>/maintenance off [server] <gray>- Menonaktifkan maintenance"));
        source.sendMessage(mm.deserialize("<gray>» <white>/maintenance add <username> <gray>- Menambah player ke whitelist bypass"));
        source.sendMessage(mm.deserialize("<gray>» <white>/maintenance remove <username> <gray>- Menghapus player dari whitelist bypass"));
        source.sendMessage(mm.deserialize("<gray>» <white>/maintenance status <gray>- Melihat status maintenance aktif"));
    }

    private void handleOn(Invocation invocation, String[] args) {
        int time = 0;
        String serverName = "global";

        if (args.length > 1) {
            try {
                time = Integer.parseInt(args[1]);
                if (time < 0) time = 0;
                
                if (args.length > 2) {
                    serverName = args[2].toLowerCase();
                }
            } catch (NumberFormatException e) {
                serverName = args[1].toLowerCase();
                time = 0;
            }
        }

        if (!serverName.equalsIgnoreCase("global")) {
            Optional<RegisteredServer> target = plugin.getServer().getServer(serverName);
            if (target.isEmpty()) {
                invocation.source().sendMessage(mm.deserialize("<red>Server '" + serverName + "' tidak ditemukan di proxy!"));
                return;
            }
        }

        if (time > 0) {
            plugin.startCountdown(serverName, time, invocation.source());
        } else {
            plugin.activateMaintenance(serverName, invocation.source());
        }
    }

    private void handleOff(Invocation invocation, String[] args) {
        String serverName = "global";
        if (args.length > 1) {
            serverName = args[1].toLowerCase();
        }

        if (plugin.getActiveCountdownTask() != null) {
            if (plugin.getCountdownServerName().equalsIgnoreCase(serverName)) {
                plugin.cancelCountdown(true);
                return;
            }
        }

        plugin.deactivateMaintenance(serverName, invocation.source());
    }

    private void handleAdd(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(mm.deserialize("<red>Penggunaan: /maintenance add <username>"));
            return;
        }

        String username = args[1];
        String lowercaseUser = username.toLowerCase();

        if (plugin.getWhitelistedPlayers().contains(lowercaseUser)) {
            invocation.source().sendMessage(mm.deserialize("<yellow>Pemain '" + username + "' sudah ada di dalam whitelist bypass."));
            return;
        }

        String uuidStr;
        Optional<Player> onlinePlayer = plugin.getServer().getPlayer(username);
        if (onlinePlayer.isPresent()) {
            uuidStr = onlinePlayer.get().getUniqueId().toString();
        } else {
            uuidStr = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + lowercaseUser).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        }

        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            boolean success = plugin.getDatabaseManager().addPlayerToWhitelist(lowercaseUser, uuidStr);
            if (!success) {
                invocation.source().sendMessage(mm.deserialize("<red>Gagal menyimpan ke database!"));
                return;
            }
        }

        plugin.getWhitelistedPlayers().add(lowercaseUser);
        plugin.saveWhitelist();

        invocation.source().sendMessage(mm.deserialize("<green>Pemain '" + username + "' (" + uuidStr + ") berhasil ditambahkan ke whitelist bypass."));
    }

    private void handleRemove(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(mm.deserialize("<red>Penggunaan: /maintenance remove <username>"));
            return;
        }

        String username = args[1];
        String lowercaseUser = username.toLowerCase();

        if (!plugin.getWhitelistedPlayers().contains(lowercaseUser)) {
            invocation.source().sendMessage(mm.deserialize("<yellow>Pemain '" + username + "' tidak ditemukan di whitelist bypass."));
            return;
        }

        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            boolean success = plugin.getDatabaseManager().removePlayerFromWhitelist(lowercaseUser);
            if (!success) {
                invocation.source().sendMessage(mm.deserialize("<red>Gagal menghapus dari database!"));
                return;
            }
        }

        plugin.getWhitelistedPlayers().remove(lowercaseUser);
        plugin.saveWhitelist();

        invocation.source().sendMessage(mm.deserialize("<green>Pemain '" + username + "' berhasil dihapus dari whitelist bypass."));
    }

    private void handleStatus(Invocation invocation) {
        invocation.source().sendMessage(mm.deserialize("<gradient:#FF4444:#FF8800><bold>Maintenance Status</bold></gradient>"));
        
        Set<String> servers = plugin.getMaintenanceServers();
        if (servers.contains("global")) {
            invocation.source().sendMessage(mm.deserialize("<gray>» <white>Status: <red>Global Maintenance Aktif"));
        } else if (!servers.isEmpty()) {
            invocation.source().sendMessage(mm.deserialize("<gray>» <white>Status: <yellow>Maintenance Aktif untuk server: <aqua>" + String.join(", ", servers)));
        } else {
            invocation.source().sendMessage(mm.deserialize("<gray>» <white>Status: <green>Tidak Aktif"));
        }

        if (plugin.getActiveCountdownTask() != null) {
            invocation.source().sendMessage(mm.deserialize("<gray>» <white>Countdown: <yellow>" + plugin.getCountdownSecondsRemaining() + " detik <gray>(target: <aqua>" + plugin.getCountdownServerName() + "<gray>)"));
        }

        invocation.source().sendMessage(mm.deserialize("<gray>» <white>Total Player Whitelist Bypass: <green>" + plugin.getWhitelistedPlayers().size() + " pemain"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("on", "off", "add", "remove", "status", "help").stream()
                    .filter(s -> s.startsWith(args.length == 0 ? "" : args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                if (sub.equals("remove")) {
                    return plugin.getWhitelistedPlayers().stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return plugin.getServer().getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (sub.equals("on") || sub.equals("off")) {
                List<String> suggestions = new ArrayList<>();
                if (sub.equals("on")) {
                    suggestions.addAll(List.of("10", "30", "60", "300"));
                }
                suggestions.add("global");
                plugin.getServer().getAllServers().forEach(s -> suggestions.add(s.getServerInfo().getName()));
                return suggestions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("on")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("global");
                plugin.getServer().getAllServers().forEach(s -> suggestions.add(s.getServerInfo().getName()));
                return suggestions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
