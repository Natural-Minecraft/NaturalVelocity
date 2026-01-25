package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PingListener {

    private final NaturalVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private com.velocitypowered.api.util.Favicon cachedIcon;

    public PingListener(NaturalVelocity plugin) {
        this.plugin = plugin;
        loadIcon();
    }

    private Component parse(String text) {
        if (text == null) return Component.empty();
        // Support &#RRGGBB by converting to MiniMessage <#RRGGBB>
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        return mm.deserialize(processed);
    }

    public void loadIcon() {
        java.io.File iconFile = new java.io.File(plugin.getDataDirectory().toFile(), "server-icon.png");
        if (!iconFile.exists()) {
            iconFile = new java.io.File(plugin.getDataDirectory().toFile(), "server-icon.PNG");
        }

        if (iconFile.exists()) {
            try {
                this.cachedIcon = id.naturalsmp.naturalvelocity.utils.IconResizer.createFavicon(iconFile);
                plugin.getLogger().info(
                        "Successfully loaded and resized dynamic server icon from: " + iconFile.getName() + " 🎨");
            } catch (java.io.IOException e) {
                plugin.getLogger().error("Failed to process " + iconFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().warn("Dynamic server-icon.png not found in " + plugin.getDataDirectory().toString()
                    + ". Using default icon.");
        }
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        ServerPing ping = event.getPing();
        ServerPing.Builder builder = ping.asBuilder();
        com.moandjiezana.toml.Toml config = plugin.getConfig();

        // Use a builder that explicitly enables hex colors for modern client support
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .builder()
                .character('§')
                .hexColors()
                .build();

        if (plugin.isMaintenanceActive()) {
            // 1. Maintenance MOTD
            String line1 = config.getString("maintenance.motd-line1",
                    "<gradient:#FFAA00:#FFFF55><bold>NATURAL SMP</bold></gradient> <gray>•</gray> <red>MAINTENANCE MODE");
            String line2 = config.getString("maintenance.motd-line2",
                    "<gray>» <white>Server sedang dalam tahap perbaikan rutin.");
            builder.description(mm.deserialize(line1 + "\n" + line2));

            // 2. Custom Player Count Text
            builder.version(new ServerPing.Version(ping.getVersion().getProtocol(), "§cMAINTENANCE"));

            // 3. Hover (Optional different hover for maintenance)
            List<ServerPing.SamplePlayer> samples = new ArrayList<>();
            samples.add(new ServerPing.SamplePlayer(
                    legacy.serialize(
                            mm.deserialize("<gradient:#FFAA00:#FFFF55><bold>UNDER MAINTENANCE</bold></gradient>")),
                    UUID.randomUUID()));
            builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
        } else {
            // 1. Premium MOTD (Line 1 & 2)
            String line1 = config.getString("motd.line1",
                    "<gradient:#00AAFF:#55FF55><bold>NATURAL SMP</bold></gradient>");
            String line2 = config.getString("motd.line2", "<gray>» <white>The Most Immersive Experience");
            builder.description(mm.deserialize(line1 + "\n" + line2));

            // 2. Custom Player Count Text
            String versionText = config.getString("server-list.version-text", "NaturalSMP v1.9");
            builder.version(new ServerPing.Version(ping.getVersion().getProtocol(), versionText));

            // 3. Player List Hover (Sample)
            List<String> hoverLines = config.getList("server-list.hover-lines");
            if (hoverLines != null && !hoverLines.isEmpty()) {
                List<ServerPing.SamplePlayer> samples = new ArrayList<>();
                for (String line : hoverLines) {
                    samples.add(new ServerPing.SamplePlayer(legacy.serialize(mm.deserialize(line)), UUID.randomUUID()));
                }
                builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
            }
        }

        if (cachedIcon != null) {
            builder.favicon(cachedIcon);
        }

        event.setPing(builder.build());
    }
}
