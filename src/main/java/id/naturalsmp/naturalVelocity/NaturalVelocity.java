package id.naturalsmp.naturalvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.moandjiezana.toml.Toml;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import com.velocitypowered.api.plugin.PluginContainer;

import id.naturalsmp.naturalvelocity.util.headmotd.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Arrays;

@Plugin(id = "naturalvelocity", name = "NaturalVelocity", version = "1.0-SNAPSHOT", authors = { "NaturalSMP" })
public class NaturalVelocity {

    public static final com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier IDENTIFIER = com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
            .from("natural:main");
    private boolean maintenanceActive = false;
    private final java.util.Set<String> whitelistedPlayers = new java.util.HashSet<>();
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Toml config;
    private id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler messageHandler;
    private PingListener pingListener;
    private DatabaseManager databaseManager;

    // Head MOTD variables
    private JsonCacheManager jsonCacheManager;
    private ImageProcessor headImageProcessor;
    private TextureCache textureCache;
    private final List<List<String>> motdUrls = new CopyOnWriteArrayList<>();

    @Inject
    public NaturalVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        loadWhitelist(); // Persistent whitelist

        // Initialize PacketEvents for Head MOTD manipulation
        PluginContainer container = server.getPluginManager().fromInstance(this).orElse(null);
        if (container != null) {
            PacketEvents.setAPI(VelocityPacketEventsBuilder.build(server, container, logger, dataDirectory));
            PacketEvents.getAPI().getSettings().checkForUpdates(false).bStats(false);
            PacketEvents.getAPI().load();
            PacketEvents.getAPI().init();

            this.jsonCacheManager = new JsonCacheManager();
            this.textureCache = new TextureCache(dataDirectory.resolve("head-motd-texture-cache.json").toFile());

            PacketEvents.getAPI().getEventManager().registerListener(new MotdHandler(this),
                    PacketListenerPriority.HIGHEST);

            loadHeadMotd();
        }

        // Initialize Database
        this.databaseManager = new DatabaseManager(this, logger);
        if (databaseManager.isEnabled()) {
            databaseManager.connect();
            startDatabasePolling();
        }

        // Load maintenance state from config or separate file
        this.maintenanceActive = config.getBoolean("integration.maintenance-mode", false);
        this.messageHandler = new id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler(this);

        logger.info("NaturalVelocity has been initialized! 🚀");

        // Register Channel
        server.getChannelRegistrar().register(IDENTIFIER);

        // 3. Register standard events (commands, chat, ping)
        this.pingListener = new PingListener(this);
        server.getEventManager().register(this, pingListener);
        server.getEventManager().register(this, new MaintenanceListener(this));

        // Register Commands
        com.velocitypowered.api.command.CommandManager cmdManager = server.getCommandManager();
        cmdManager.register(
                cmdManager.metaBuilder("nvelocity")
                        .aliases("naturalvelocity")
                        .plugin(this)
                        .build(),
                new NaturalVelocityCommand(this));

        cmdManager.register(
                cmdManager.metaBuilder("headmotd")
                        .plugin(this)
                        .build(),
                new id.naturalsmp.naturalvelocity.commands.HeadMotdCommand(this));
    }

    public void reload() {
        loadConfig();
        loadWhitelist();
        if (pingListener != null) {
            pingListener.loadIcon();
        }
        logger.info("NaturalVelocity configuration reloaded! 🔄");
    }

    @Subscribe
    public void onPluginMessage(com.velocitypowered.api.event.connection.PluginMessageEvent event) {
        if (!event.getIdentifier().equals(IDENTIFIER))
            return;

        java.io.ByteArrayInputStream b = new java.io.ByteArrayInputStream(event.getData());
        java.io.DataInputStream in = new java.io.DataInputStream(b);

        try {
            String subChannel = in.readUTF();
            if (subChannel.equalsIgnoreCase("Maintenance")) {
                this.maintenanceActive = in.readBoolean();

                // Read Whitelist
                this.whitelistedPlayers.clear();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    this.whitelistedPlayers.add(in.readUTF().toLowerCase());
                }

                logger.info("Maintenance Mode updated to: " + (maintenanceActive ? "ON" : "OFF") + " (Whitelist: "
                        + size + ")");

                // Persistence
                saveMaintenanceState();
                saveWhitelist();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveMaintenanceState() {
        File file = new File(dataDirectory.toFile(), "velocity.toml");
        try {
            // Read current file content
            String content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);

            // Robust Regex replace
            // Replaces "maintenance-mode = true/false" with new value, preserving
            // whitespace
            String regex = "(maintenance-mode\\s*=\\s*)(true|false)";
            String replacement = "$1" + maintenanceActive;

            String newContent = content.replaceAll(regex, replacement);

            if (!content.equals(newContent)) {
                Files.write(file.toPath(), newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                logger.warn("Could not find/update 'maintenance-mode' key in velocity.toml");
            }
        } catch (IOException e) {
            logger.error("Failed to save maintenance state!", e);
        }
    }

    private void loadWhitelist() {
        File file = new File(dataDirectory.toFile(), "whitelist.json");
        if (!file.exists())
            return;

        try {
            String content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            JsonArray array = JsonParser.parseString(content).getAsJsonArray();
            this.whitelistedPlayers.clear();
            for (JsonElement el : array) {
                this.whitelistedPlayers.add(el.getAsString().toLowerCase());
            }
        } catch (Exception e) {
            logger.error("Failed to load whitelist.json!", e);
        }
    }

    private void saveWhitelist() {
        File file = new File(dataDirectory.toFile(), "whitelist.json");
        try {
            JsonArray array = new JsonArray();
            for (String p : whitelistedPlayers) {
                array.add(p);
            }
            Files.write(file.toPath(), array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to save whitelist.json!", e);
        }
    }

    public boolean isMaintenanceActive() {
        return maintenanceActive;
    }

    private void loadConfig() {
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                logger.error("Could not create data directory!", e);
            }
        }

        File file = new File(dataDirectory.toFile(), "velocity.toml");
        if (!file.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/velocity.toml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                logger.error("Could not save default config!", e);
            }
        }

        this.config = new Toml().read(file);
    }

    public void reloadConfigAndIcon() {
        loadConfig();
        pingListener.loadIcon();
        loadWhitelist();
        loadHeadMotd();
    }

    private void loadHeadMotd() {
        if (!getConfig().getBoolean("head-motd.enabled", false)) {
            return;
        }

        if (this.headImageProcessor != null) {
            this.headImageProcessor.shutdown();
        }

        String apiKey = getConfig().getString("head-motd.mineskin-api-key", "");
        if (apiKey.isEmpty()) {
            logger.warn(
                    "Head MOTD is enabled but no MineSkin API key is configured. Please add one in velocity.toml to generate custom MOTD headers.");
            return;
        }

        this.headImageProcessor = new ImageProcessor(
                new MineSkinClient(apiKey),
                this.textureCache,
                dataDirectory.toFile(),
                this);

        motdUrls.clear();
        textureCache.load();

        // Load existing mapping from cache
        String motdCache = textureCache.get("motd-mapping-cache");
        if (motdCache != null && !motdCache.isEmpty()) {
            for (String row : motdCache.split(";")) {
                if (!row.isEmpty())
                    motdUrls.add(Arrays.asList(row.split(",")));
            }
        }

        jsonCacheManager.buildMotdCache(motdUrls);

        List<String> hoverList = getConfig().getList("server-list.hover-lines");
        if (hoverList != null) {
            jsonCacheManager.buildHoverCache(hoverList);
        }
    }

    // --- Accessors ---

    public JsonCacheManager getJsonCacheManager() {
        return jsonCacheManager;
    }

    public ImageProcessor getHeadImageProcessor() {
        return headImageProcessor;
    }

    public TextureCache getTextureCache() {
        return textureCache;
    }

    public List<List<String>> getMotdUrls() {
        return motdUrls;
    }

    public Toml getConfig() {
        return config;
    }

    public java.util.Set<String> getWhitelistedPlayers() {
        return whitelistedPlayers;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler getMessageHandler() {
        return messageHandler;
    }

    private void startDatabasePolling() {
        server.getScheduler().buildTask(this, () -> {
            if (!databaseManager.isEnabled())
                return;

            boolean active = databaseManager.getMaintenanceActive();
            boolean stateChanged = (active != this.maintenanceActive);

            if (active) {
                // Fetch latest whitelist
                java.util.List<String> whitelist = databaseManager.getMaintenanceWhitelist();
                java.util.Set<String> newWhitelist = new java.util.HashSet<>(whitelist);

                if (!newWhitelist.equals(this.whitelistedPlayers)) {
                    this.whitelistedPlayers.clear();
                    this.whitelistedPlayers.addAll(newWhitelist);
                    logger.info("[CoreDB] Maintenance Whitelist updated via MySQL. Size: " + whitelistedPlayers.size());
                    saveWhitelist();
                }

                if (stateChanged) {
                    this.maintenanceActive = true;
                    logger.info("[CoreDB] Maintenance Mode ENABLED via MySQL.");
                    saveMaintenanceState();
                }

                // Kick online players who are not whitelisted and don't have bypass
                for (com.velocitypowered.api.proxy.Player player : server.getAllPlayers()) {
                    if (player.hasPermission("naturalsmp.maintenance.bypass"))
                        continue;
                    if (whitelistedPlayers.contains(player.getUsername().toLowerCase()))
                        continue;

                    String kickReason = config.getString("maintenance.kick-reason");
                    player.disconnect(parse(kickReason));
                }
            } else if (stateChanged) {
                this.maintenanceActive = false;
                logger.info("[CoreDB] Maintenance Mode DISABLED via MySQL.");
                saveMaintenanceState();
            }

        }).repeat(10, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    public Component parse(String text) {
        if (text == null)
            return Component.empty();
        // 1. Support &#RRGGBB by converting to MiniMessage <#RRGGBB>
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        // 2. Support legacy & codes
        processed = processed.replace("&", "§");

        if (processed.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(processed);
        }
        return MiniMessage.miniMessage().deserialize(processed);
    }
}
