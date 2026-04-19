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
        if (text == null)
            return Component.empty();

        // 1. Convert &#RRGGBB to <#RRGGBB> (MiniMessage Hex)
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        // 2. Convert legacy & codes to MiniMessage tags
        processed = processed
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");

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

        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection();

        if (plugin.isMaintenanceActive()) {
            // === MAINTENANCE MODE ===
            // 1. Maintenance MOTD
            String line1 = config.getString("maintenance.motd-line1",
                    "<gradient:#FF0000:#FF8800><bold>MAINTENANCE MODE</bold></gradient>    <gray>•</gray> <white>Natural SMP");
            String line2 = config.getString("maintenance.motd-line2",
                    "<gray>» <white>Server sedang dalam tahap perbaikan rutin.");
            builder.description(parse(line1 + "\n" + line2));

            // 2. Custom Player Count Text
            builder.version(new ServerPing.Version(ping.getVersion().getProtocol(), "\u00A7cMAINTENANCE"));

            // 3. Hover
            List<ServerPing.SamplePlayer> samples = new ArrayList<>();
            samples.add(new ServerPing.SamplePlayer(
                    legacy.serialize(parse("<gradient:#FFAA00:#FFFF55><bold>UNDER MAINTENANCE</bold></gradient>")),
                    UUID.randomUUID()));
            builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));

        } else {
            // === NORMAL MODE ===

            // 1. MOTD - Only set via Velocity API if HeadMOTD is NOT active
            // (When HeadMOTD is active, the PacketEvents handler overrides the description
            // at packet level)
            if (config.getBoolean("motd.enabled", true) && !plugin.isHeadMotdActive()) {
                String line1 = config.getString("motd.line1",
                        "<gradient:#00AAFF:#55FF55><bold>NATURAL SMP</bold></gradient>");
                String line2 = config.getString("motd.line2", "<gray>» <white>The Most Immersive Experience");
                builder.description(parse(line1 + "\n" + line2));
            }

            // 2. Custom Player Count Text
            if (config.getBoolean("server-list.enabled", true)) {
                String versionText = config.getString("server-list.version-text", "&c> 1.21.4 - 26.1 [ %online% / %max_player% ]");
                
                int online = ping.getPlayers().isPresent() ? ping.getPlayers().get().getOnline() : 0;
                int max = ping.getPlayers().isPresent() ? ping.getPlayers().get().getMax() : 0;
                
                String parsedVersionText = versionText.replace("%online%", String.valueOf(online))
                                                      .replace("%max_player%", String.valueOf(max));

                builder.version(
                        new ServerPing.Version(ping.getVersion().getProtocol(), legacy.serialize(parse(parsedVersionText))));

                // 3. Player List Hover (only if HeadMOTD is NOT handling hover at packet level)
                if (!plugin.isHeadMotdActive()) {
                    List<String> hoverLines = config.getList("server-list.hover-lines");
                    if (hoverLines != null && !hoverLines.isEmpty()) {
                        List<ServerPing.SamplePlayer> samples = new ArrayList<>();
                        for (String line : hoverLines) {
                            samples.add(new ServerPing.SamplePlayer(legacy.serialize(parse(line)), UUID.randomUUID()));
                        }
                        builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
                    }
                }
            }
        }

        if (cachedIcon != null) {
            builder.favicon(cachedIcon);
        }

        event.setPing(builder.build());
    }
}
