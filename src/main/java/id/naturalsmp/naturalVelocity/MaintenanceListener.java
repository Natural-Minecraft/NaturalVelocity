package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;

public class MaintenanceListener {

    private final NaturalVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MaintenanceListener(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();

        // === TEMPORARY CLOSED CHECK (highest priority) ===
        if (plugin.isTempClosedActive()) {
            if (!player.hasPermission("naturalvelocity.tempclosed.bypass")) {
                String kickReason = plugin.getConfig().getString("temp-closed.kick-reason",
                        "<gradient:#FF4444:#FF8800><bold>NaturalSMP</bold></gradient> <dark_gray>┃</dark_gray> <red><bold>TEMPORARY CLOSED</bold></red>\n\n<gray>Server sedang tutup sementara.\nKunjungi kami lagi nanti!</gray>\n\n<dark_gray>» <aqua>link.naturalsmp.net</aqua></dark_gray>");
                Component component = parse(kickReason);
                Component flattened = LegacyComponentSerializer.legacySection()
                        .deserialize(LegacyComponentSerializer.legacySection().serialize(component));
                event.setResult(LoginEvent.ComponentResult.denied(flattened));
            }
            return;
        }

        // === MAINTENANCE CHECK ===
        if (!plugin.isMaintenanceActive() && !plugin.getMaintenanceServers().contains("global"))
            return;

        if (player.hasPermission("naturalvelocity.maintenance.bypass") ||
                plugin.getWhitelistedPlayers().contains(player.getUsername().toLowerCase())) {
            return;
        }

        String kickReason = plugin.getConfig().getString("maintenance.kick-reason",
                "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP MAINTENANCE</bold></gradient>\n\n<gray>Server sedang dalam perbaikan.\nMohon kembali lagi nanti!");

        Component component = parse(kickReason);

        // Flatten the component to legacy format for maximum compatibility during Login
        // state
        // This prevents DecoderException on some clients (like Optifine)
        Component flattened = LegacyComponentSerializer.legacySection()
                .deserialize(LegacyComponentSerializer.legacySection().serialize(component));

        event.setResult(LoginEvent.ComponentResult.denied(flattened));
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("naturalvelocity.maintenance.bypass") ||
                plugin.getWhitelistedPlayers().contains(player.getUsername().toLowerCase())) {
            return;
        }

        RegisteredServer targetServer = event.getOriginalServer();
        String targetName = targetServer.getServerInfo().getName().toLowerCase();

        if (plugin.isMaintenanceActive() || plugin.getMaintenanceServers().contains("global")) {
            if (player.getCurrentServer().isEmpty()) {
                String kickReason = plugin.getConfig().getString("maintenance.kick-reason",
                        "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP MAINTENANCE</bold></gradient>\n\n<gray>Server sedang dalam perbaikan.\nMohon kembali lagi nanti!");
                Component component = parse(kickReason);
                Component flattened = LegacyComponentSerializer.legacySection()
                        .deserialize(LegacyComponentSerializer.legacySection().serialize(component));
                player.disconnect(flattened);
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            } else {
                player.sendMessage(Component.text("§c[Maintenance] Server sedang maintenance global. Anda tidak dapat berpindah server."));
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
            return;
        }

        if (plugin.getMaintenanceServers().contains(targetName)) {
            if (player.getCurrentServer().isEmpty()) {
                if (targetName.equalsIgnoreCase("lobby")) {
                    String kickReason = plugin.getConfig().getString("maintenance.kick-reason",
                            "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP MAINTENANCE</bold></gradient>\n\n<gray>Server sedang dalam perbaikan.\nMohon kembali lagi nanti!");
                    Component component = parse(kickReason);
                    Component flattened = LegacyComponentSerializer.legacySection()
                            .deserialize(LegacyComponentSerializer.legacySection().serialize(component));
                    player.disconnect(flattened);
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                } else {
                    Optional<RegisteredServer> lobby = plugin.getServer().getServer("lobby");
                    if (lobby.isPresent() && !plugin.getMaintenanceServers().contains("lobby")) {
                        event.setResult(ServerPreConnectEvent.ServerResult.allowed(lobby.get()));
                        player.sendMessage(Component.text("§c[Maintenance] Server " + targetServer.getServerInfo().getName() + " sedang maintenance. Anda dialihkan ke Lobby."));
                    } else {
                        String kickReason = plugin.getConfig().getString("maintenance.kick-reason",
                                "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP MAINTENANCE</bold></gradient>\n\n<gray>Server sedang dalam perbaikan.\nMohon kembali lagi nanti!");
                        Component component = parse(kickReason);
                        Component flattened = LegacyComponentSerializer.legacySection()
                                .deserialize(LegacyComponentSerializer.legacySection().serialize(component));
                        player.disconnect(flattened);
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    }
                }
            } else {
                player.sendMessage(Component.text("§c[Maintenance] Server " + targetServer.getServerInfo().getName() + " sedang dalam mode maintenance!"));
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
        }
    }

    private Component parse(String text) {
        if (text == null)
            return Component.empty();
        // 1. Support &#RRGGBB by converting to MiniMessage <#RRGGBB>
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        // 2. Support legacy & codes
        processed = processed.replace("&", "§");

        if (processed.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(processed);
        }
        return mm.deserialize(processed);
    }
}
