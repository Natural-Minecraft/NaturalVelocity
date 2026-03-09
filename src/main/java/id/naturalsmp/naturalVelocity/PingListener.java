package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

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

        // 2. Convert standard Legacy & codes to MiniMessage tags or handle them
        // Simplest way for mixed content:
        // If it contains <gradient> or <# (hex), treat as MiniMessage.
        // If it contains ONLY &, treating as Legacy is safer.
        // But for gradients + & legacy, we need mixed handling.

        // Strategy: Convert & -> §, then use LegacySection serializer to deserialize
        // basic codes,
        // BUT Logic issue: LegacySection deserializer DOES NOT support MiniMessage
        // tags.

        // Correct Strategy:
        // Use MiniMessage for everything. Convert legacy &x to <color>.
        // Replace &([0-9a-f]) with <$1> ? No, mapped colors.

        // Easier: Just replace & with § and let the final serialization handle it?
        // No, MiniMessage ignores §.

        // Final Robust Strategy:
        // Convert legacy & codes to MiniMessage tags manually before parsing.
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

        // Use standard legacy serializer to prevent "§x..." weirdness in older clients
        // or hover text
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection();

        if (plugin.isMaintenanceActive()) {
            // 1. Maintenance MOTD
            String line1 = config.getString("maintenance.motd-line1",
                    "<gradient:#FF0000:#FF8800><bold>MAINTENANCE MODE</bold></gradient>    <gray>•</gray> <white>Natural SMP");
            String line2 = config.getString("maintenance.motd-line2",
                    "<gray>» <white>Server sedang dalam tahap perbaikan rutin.");
            builder.description(parse(line1 + "\n" + line2));

            // 2. Custom Player Count Text
            builder.version(new ServerPing.Version(ping.getVersion().getProtocol(), "\u00A7cMAINTENANCE"));

            // 3. Hover (Optional different hover for maintenance)
            List<ServerPing.SamplePlayer> samples = new ArrayList<>();
            samples.add(new ServerPing.SamplePlayer(
                    legacy.serialize(parse("<gradient:#FFAA00:#FFFF55><bold>UNDER MAINTENANCE</bold></gradient>")),
                    UUID.randomUUID()));
            builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
        } else {
            // 1. Premium MOTD (Line 1 & 2) or Custom Head MOTD
            if (config.getBoolean("head-motd.enabled", false)) {
                JsonArray motdCache = plugin.getJsonCacheManager() != null ? plugin.getJsonCacheManager().getMotdCache()
                        : null;

                if (motdCache != null && motdCache.size() > 0) {
                    // Try to inject our custom MOTD JSON directly into the pipeline!
                    injectNettyHandler(event.getConnection(), motdCache);
                    builder.description(Component.empty()); // Leave empty, our pipeline handler will inject the real
                                                            // JSON!
                } else {
                    List<String> fallback = config.getList("head-motd.fallback-motd");
                    if (fallback != null && !fallback.isEmpty()) {
                        String f1 = fallback.get(0);
                        String f2 = fallback.size() > 1 ? fallback.get(1) : "";
                        builder.description(parse(f1 + "\n" + f2));
                    }
                }
            } else {
                String line1 = config.getString("motd.line1",
                        "<gradient:#00AAFF:#55FF55><bold>NATURAL SMP</bold></gradient>");
                String line2 = config.getString("motd.line2", "<gray>» <white>The Most Immersive Experience");
                builder.description(parse(line1 + "\n" + line2));
            }

            // 2. Custom Player Count Text
            String versionText = config.getString("server-list.version-text", "NaturalSMP v1.21");
            builder.version(
                    new ServerPing.Version(ping.getVersion().getProtocol(), legacy.serialize(parse(versionText))));

            // 3. Player List Hover (Sample)
            boolean alwaysPlusOne = config.getBoolean("head-motd.always-plus-one", false);
            int onlineCount = ping.getPlayers().isPresent() ? ping.getPlayers().get().getOnline() : 0;
            long configuredMax = config.getLong("head-motd.max-players", 69L);
            int maxCount = alwaysPlusOne ? onlineCount + 1 : (int) configuredMax;

            List<ServerPing.SamplePlayer> samples = new ArrayList<>();
            List<String> hoverLines = config.getList("server-list.hover-lines");
            if (hoverLines != null && !hoverLines.isEmpty()) {
                for (String line : hoverLines) {
                    samples.add(new ServerPing.SamplePlayer(legacy.serialize(parse(line)), UUID.randomUUID()));
                }
            }

            builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
            builder.onlinePlayers(onlineCount);
            builder.maximumPlayers(maxCount);
        }

        if (cachedIcon != null) {
            builder.favicon(cachedIcon);
        }

        event.setPing(builder.build());
    }

    private void injectNettyHandler(Object connection, JsonArray motdCache) {
        try {
            // Get underlying MinecraftConnection from InitialInboundConnection
            Method getConnectionMethod = connection.getClass().getMethod("getConnection");
            Object mcConnection = getConnectionMethod.invoke(connection);

            // Get Netty Channel
            Method getChannelMethod = mcConnection.getClass().getMethod("getChannel");
            Channel channel = (Channel) getChannelMethod.invoke(mcConnection);

            if (channel.pipeline().get("natural_motd_injector") == null) {
                channel.pipeline().addBefore("minecraft-encoder", "natural_motd_injector",
                        new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                                    throws Exception {
                                if (msg.getClass().getSimpleName().equals("StatusResponse")) {
                                    try {
                                        Field statusField = msg.getClass().getDeclaredField("status");
                                        statusField.setAccessible(true);
                                        String json = (String) statusField.get(msg);

                                        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                                        JsonObject newDesc = new JsonObject();
                                        newDesc.add("extra", motdCache);
                                        newDesc.addProperty("text", "");
                                        root.add("description", newDesc);

                                        statusField.set(msg, root.toString());
                                    } catch (Exception ex) {
                                        plugin.getLogger()
                                                .error("Error modifying StatusResponse JSON: " + ex.getMessage());
                                    }
                                }
                                super.write(ctx, msg, promise);
                            }
                        });
            }
        } catch (Exception e) {
            // Log reflection or pipeline errors
            plugin.getLogger().error("Failed to inject Netty Handler: " + e.getMessage(), e);
        }
    }
}
