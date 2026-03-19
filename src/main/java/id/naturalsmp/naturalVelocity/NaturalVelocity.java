package id.naturalsmp.naturalvelocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import org.slf4j.Logger;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.moandjiezana.toml.Toml;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.Scanner;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import id.naturalsmp.naturalvelocity.headmotd.*;
import id.naturalsmp.naturalvelocity.networking.SyncServer;

@Plugin(id = "naturalvelocity", name = "NaturalVelocity", version = "2.0-SNAPSHOT", authors = {
        "NaturalSMP" }, dependencies = { @Dependency(id = "packetevents", optional = true) })
public class NaturalVelocity {

    public static final com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier IDENTIFIER = com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
            .from("natural:main");

    private boolean maintenanceActive = false;
    private final Set<String> whitelistedPlayers = new HashSet<>();
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Toml config;
    private id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler messageHandler;
    private PingListener pingListener;
    private DatabaseManager databaseManager;

    private static NaturalVelocity INSTANCE;
    private SyncServer syncServer;

    // HeadMOTD System
    private HeadMotdHandler headMotdHandler;
    private ImageProcessor imageProcessor;
    private TextureCache mappingCache;
    private final List<List<List<String>>> motdUrls = new CopyOnWriteArrayList<>();
    private boolean packetEventsAvailable = false;

    @Inject
    public NaturalVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        INSTANCE = this;
        loadConfig();
        loadWhitelist();

        // Initialize Database
        this.databaseManager = new DatabaseManager(this, logger);
        if (databaseManager.isEnabled()) {
            databaseManager.connect();
            startDatabasePolling();
        }

        this.maintenanceActive = config.getBoolean("integration.maintenance-mode", false);
        this.messageHandler = new id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler(this);

        // Initialize PacketEvents for HeadMOTD
        initPacketEvents();

        logger.info("NaturalVelocity v2.0 has been initialized! 🚀");

        // Register Channel
        server.getChannelRegistrar().register(IDENTIFIER);

        // Register Listeners
        this.pingListener = new PingListener(this);
        server.getEventManager().register(this, pingListener);
        server.getEventManager().register(this, new MaintenanceListener(this));

        // Register Command
        com.velocitypowered.api.command.CommandManager cmdManager = server.getCommandManager();
        com.velocitypowered.api.command.CommandMeta meta = cmdManager.metaBuilder("nvelocity")
                .aliases("nv")
                .plugin(this)
                .build();
        cmdManager.register(meta, new NaturalVelocityCommand(this));

        // Initialize HeadMOTD images folder
        if (isHeadMotdEnabled()) {
            File imagesFolder = new File(dataDirectory.toFile(),
                    config.getString("head-motd.images-folder", "images"));
            if (!imagesFolder.exists())
                imagesFolder.mkdirs();
        }
    }

    private void initPacketEvents() {
        try {
            // Check if PacketEvents is available
            Class.forName("com.github.retrooper.packetevents.PacketEvents");

            PacketEvents.setAPI(VelocityPacketEventsBuilder.build(
                    server,
                    server.getPluginManager().getPlugin("naturalvelocity").orElse(null),
                    logger,
                    dataDirectory));
            PacketEvents.getAPI().getSettings().checkForUpdates(false);
            PacketEvents.getAPI().load();
            PacketEvents.getAPI().init();

            // Create & register HeadMotdHandler
            this.headMotdHandler = new HeadMotdHandler();
            PacketEvents.getAPI().getEventManager().registerListener(headMotdHandler, PacketListenerPriority.HIGHEST);

            // Initialize caches
            this.mappingCache = new TextureCache(dataDirectory.resolve("head_mapping.json").toFile());

            packetEventsAvailable = true;
            reloadHeadMotd();

            logger.info("[HeadMOTD] PacketEvents integration loaded! 🎨");
        } catch (ClassNotFoundException e) {
            packetEventsAvailable = false;
            logger.warn("[HeadMOTD] PacketEvents not found - Head MOTD features disabled.");
            logger.warn("[HeadMOTD] Install 'packetevents' on your Velocity server to enable pixel art MOTD.");
        } catch (Exception e) {
            packetEventsAvailable = false;
            logger.error("[HeadMOTD] Failed to initialize PacketEvents: " + e.getMessage());
        }
    }

    public void reloadHeadMotd() {
        if (!packetEventsAvailable || headMotdHandler == null)
            return;

        boolean enabled = config.getBoolean("head-motd.enabled", false);
        headMotdHandler.setEnabled(enabled);
        headMotdHandler.setAlwaysPlusOne(config.getBoolean("head-motd.always-plus-one", true));
        headMotdHandler.setIgnoreBedrock(config.getBoolean("head-motd.ignore-bedrock", true));
        headMotdHandler.setMinimumProtocol(config.getLong("head-motd.motd-minimum-protocol", 773L).intValue());
        headMotdHandler.setMaximumProtocol(config.getLong("head-motd.motd-maximum-protocol", 775L).intValue());
        headMotdHandler.setFallbackLine1(config.getString("head-motd.fallback-line1", ""));
        headMotdHandler.setFallbackLine2(config.getString("head-motd.fallback-line2", ""));

        long delayConfig = config.getLong("head-motd.rotating-motd-delay", 30L);
        headMotdHandler.setRotatingDelay((int) delayConfig);

        // Reload hover cache from main hover-lines config
        List<String> hoverLines = config.getList("server-list.hover-lines");
        if (hoverLines != null) {
            headMotdHandler.buildHoverCache(hoverLines);
        }

        // Reload MOTD URL caches
        motdUrls.clear();
        mappingCache.load();
        
        String singleCache = mappingCache.get("motd");
        if (singleCache != null && !singleCache.isEmpty()) {
            List<List<String>> singleUrls = new ArrayList<>();
            for (String row : singleCache.split(";")) {
                if (!row.isEmpty())
                    singleUrls.add(Arrays.asList(row.split(",")));
            }
            motdUrls.add(singleUrls);
        } else {
            int i = 0;
            while(true) {
                String cache = mappingCache.get("motd_" + i);
                if (cache == null || cache.isEmpty()) break;
                List<List<String>> urls = new ArrayList<>();
                for (String row : cache.split(";")) {
                    if (!row.isEmpty())
                        urls.add(Arrays.asList(row.split(",")));
                }
                if (!urls.isEmpty()) {
                    motdUrls.add(urls);
                }
                i++;
            }
        }
        headMotdHandler.buildMotdCaches(motdUrls);

        // === Maintenance MOTD config ===
        headMotdHandler.setMaintenanceLine1(config.getString("maintenance.motd-line1",
                "<b><gradient:#FF0000:#FF8800>MAINTENANCE MODE</gradient></b>    &#AAAAAA• &#FFFFFFNatural SMP"));
        headMotdHandler.setMaintenanceLine2(config.getString("maintenance.motd-line2",
                "&#AAAAAA» &#FFFFFFServer sedang dalam tahap perbaikan rutin."));
        headMotdHandler.setMaintenanceActive(this.maintenanceActive);

        // Maintenance hover
        List<String> maintenanceHover = new ArrayList<>();
        maintenanceHover.add("<gradient:#FFAA00:#FFFF55><bold>UNDER MAINTENANCE</bold></gradient>");
        maintenanceHover.add("<gray>Server sedang perbaikan rutin.");
        maintenanceHover.add("<gray>Silahkan coba lagi nanti.");
        headMotdHandler.buildMaintenanceHoverCache(maintenanceHover);

        // Maintenance head MOTD URL cache (from separate mapping key)
        String maintMotdCache = mappingCache.get("maintenance-motd");
        if (maintMotdCache != null && !maintMotdCache.isEmpty()) {
            List<List<String>> maintUrls = new ArrayList<>();
            for (String row : maintMotdCache.split(";")) {
                if (!row.isEmpty())
                    maintUrls.add(Arrays.asList(row.split(",")));
            }
            headMotdHandler.buildMaintenanceMotdCache(maintUrls);
            logger.info("[HeadMOTD] Maintenance head banner loaded with {} rows.", maintUrls.size());
        }

        // Shutdown old processor
        if (imageProcessor != null)
            imageProcessor.shutdown();

        if (enabled) {
            String apiKey = config.getString("head-motd.mineskin-api-key", "");
            int delay = config.getLong("head-motd.mineskin-delay", 2000L).intValue();
            this.imageProcessor = new ImageProcessor(
                    new MineSkinClient(apiKey),
                    new TextureCache(dataDirectory.resolve("head_cache.json").toFile()),
                    dataDirectory.toFile(),
                    logger,
                    delay);
            logger.info("[HeadMOTD] Head MOTD enabled with {} cached URL rows.", motdUrls.size());
        } else {
            this.imageProcessor = null;
        }
    }

    public void processMotd(com.velocitypowered.api.command.CommandSource source, int pct) {
        List<String> images = new ArrayList<>();
        if (config.contains("head-motd.motd-image")) {
            try {
                List<String> list = config.getList("head-motd.motd-image");
                if (list != null) {
                    images.addAll(list);
                } else {
                    String single = config.getString("head-motd.motd-image");
                    if (single != null) images.add(single);
                }
            } catch (Exception e) {
                images.add(config.getString("head-motd.motd-image"));
            }
        } else {
            images.add("motd.png");
        }
        if (images.isEmpty()) {
            source.sendMessage(Component.text("§c[HeadMOTD] Tidak ada gambar MOTD di config."));
            return;
        }

        source.sendMessage(Component.text("§a[HeadMOTD] Memproses " + images.size() + " gambar MOTD secara berurutan..."));
        
        // Clear old single cache key
        mappingCache.put("motd", ""); 
        
        processMotdImagesSeq(source, pct, images, 0);
    }
    
    private void processMotdImagesSeq(com.velocitypowered.api.command.CommandSource source, int pct, List<String> images, int index) {
        if (index >= images.size()) {
            for(int k = images.size(); k < 10; k++) {
                if (mappingCache.get("motd_" + k) != null) {
                    mappingCache.put("motd_" + k, "");
                }
            }
            source.sendMessage(Component.text("§a[HeadMOTD] ✓ Semua " + images.size() + " MOTD selesai diproses dan dirotasi!"));
            server.getScheduler().buildTask(this, this::reloadHeadMotd).schedule();
            return;
        }
        source.sendMessage(Component.text("§7[HeadMOTD] [ " + (index+1) + " / " + images.size() + " ] Memproses: " + images.get(index)));
        processMotdImageAsync(source, pct, "motd_" + index, images.get(index)).thenAccept(success -> {
            processMotdImagesSeq(source, pct, images, index + 1);
        });
    }

    public void processMaintenanceMotd(com.velocitypowered.api.command.CommandSource source, int pct) {
        String maintImage = config.getString("maintenance.motd-image", "");
        if (maintImage == null || maintImage.trim().isEmpty()) {
            source.sendMessage(Component.text(
                    "§c[HeadMOTD] maintenance.motd-image not set in config. Using text MOTD only for maintenance."));
            return;
        }
        source.sendMessage(Component.text("§a[HeadMOTD] Starting Maintenance MOTD image processing..."));
        processMotdImageAsync(source, pct, "maintenance-motd", maintImage).thenAccept(success -> {
            if(success) {
                source.sendMessage(Component.text("§a[HeadMOTD] ✓ Maintenance MOTD processing complete!"));
            }
        });
    }

    private java.util.concurrent.CompletableFuture<Boolean> processMotdImageAsync(com.velocitypowered.api.command.CommandSource source, int pct, String cacheKey, String imageName) {
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        if (!packetEventsAvailable) {
            source.sendMessage(Component.text("§c[HeadMOTD] PacketEvents not installed! Cannot process head MOTD."));
            future.complete(false);
            return future;
        }
        if (!isHeadMotdEnabled()) {
            source.sendMessage(Component.text("§c[HeadMOTD] Head MOTD is disabled in config. Set head-motd.enabled = true"));
            future.complete(false);
            return future;
        }
        String apiKey = config.getString("head-motd.mineskin-api-key", "");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            source.sendMessage(Component.text("§c[HeadMOTD] MineSkin API key not configured! Set head-motd.mineskin-api-key"));
            future.complete(false);
            return future;
        }
        String imagesFolder = config.getString("head-motd.images-folder", "images");
        File file = new File(dataDirectory.resolve(imagesFolder).toFile(), imageName);
        if (!file.exists()) {
            source.sendMessage(Component.text("§c[HeadMOTD] Image not found: " + file.getAbsolutePath()));
            future.complete(false);
            return future;
        }

        imageProcessor.process(file, pct).thenAccept(rows -> {
            List<String> rowStrings = new ArrayList<>();
            rows.forEach(urls -> rowStrings.add(String.join(",", urls)));
            mappingCache.put(cacheKey, String.join(";", rowStrings));
            
            if (cacheKey.equals("maintenance-motd")) {
                headMotdHandler.buildMaintenanceMotdCache(rows);
            }
            future.complete(true);
        }).exceptionally(ex -> {
            source.sendMessage(Component.text("§c[HeadMOTD] Processing failed: " + ex.getMessage()));
            future.complete(false);
            return null;
        });
        return future;
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (packetEventsAvailable) {
            try {
                PacketEvents.getAPI().terminate();
            } catch (Exception ignored) {
            }
        }
        if (imageProcessor != null)
            imageProcessor.shutdown();
    }

    public void reload() {
        loadConfig();
        loadWhitelist();
        if (pingListener != null) {
            pingListener.loadIcon();
        }
        reloadHeadMotd();
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

                this.whitelistedPlayers.clear();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    this.whitelistedPlayers.add(in.readUTF().toLowerCase());
                }

                logger.info("Maintenance Mode updated to: " + (maintenanceActive ? "ON" : "OFF") + " (Whitelist: "
                        + size + ")");

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
            String content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
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

    public boolean isHeadMotdEnabled() {
        return config.getBoolean("head-motd.enabled", false);
    }

    public boolean isHeadMotdActive() {
        return packetEventsAvailable && isHeadMotdEnabled() && !motdUrls.isEmpty();
    }

    public HeadMotdHandler getHeadMotdHandler() {
        return headMotdHandler;
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
        File config = new File(dataDirectory.toFile(), "syncCommand.txt");
        int port = 25666;
        String password = "defaultPassword";
        if (!config.exists()) {
            try {
                if (!config.getParentFile().exists()){
                    config.getParentFile().mkdirs();
                }
                config.createNewFile();

                FileWriter writer = new FileWriter(config);

                writer.write("port=25666\n");
                writer.write("password=defaultPassword");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Scanner reader = null;
        try {
            reader = new Scanner(config);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (reader.hasNextLine()) {
            String data = reader.nextLine();
            if (data.toLowerCase().contains("port=")) {
                port = Integer.valueOf(data.replace("port=", ""));
            }
            if (data.toLowerCase().contains("password=")) {
                password = data.replace("password=", "");
            }
        }
        logger.info("Config Dimuat!");
        reader.close();
        if (password.equals("defaultPassword")) {
            logger.warn("ganti passwordnya cui :v");
        }

        syncServer = new SyncServer();
        syncServer.runServer(port, password);

        this.config = new Toml().read(file);
    }

    public SyncServer getSyncServer() {
        return syncServer;
    }

    public static NaturalVelocity getInstance() {
        return INSTANCE;
    }

    public Toml getConfig() {
        return config;
    }

    public Set<String> getWhitelistedPlayers() {
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
                java.util.List<String> whitelist = databaseManager.getMaintenanceWhitelist();
                Set<String> newWhitelist = new HashSet<>(whitelist);

                if (!newWhitelist.equals(this.whitelistedPlayers)) {
                    this.whitelistedPlayers.clear();
                    this.whitelistedPlayers.addAll(newWhitelist);
                    logger.info("[CoreDB] Maintenance Whitelist updated via MySQL. Size: " + whitelistedPlayers.size());
                    saveWhitelist();
                }

                if (stateChanged) {
                    this.maintenanceActive = true;
                    if (headMotdHandler != null)
                        headMotdHandler.setMaintenanceActive(true);
                    logger.info("[CoreDB] Maintenance Mode ENABLED via MySQL.");
                    saveMaintenanceState();
                }

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
                if (headMotdHandler != null)
                    headMotdHandler.setMaintenanceActive(false);
                logger.info("[CoreDB] Maintenance Mode DISABLED via MySQL.");
                saveMaintenanceState();
            }

        }).repeat(10, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    public Component parse(String text) {
        if (text == null)
            return Component.empty();
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        processed = processed.replace("&", "§");
        if (processed.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(processed);
        }
        return MiniMessage.miniMessage().deserialize(processed);
    }
}
