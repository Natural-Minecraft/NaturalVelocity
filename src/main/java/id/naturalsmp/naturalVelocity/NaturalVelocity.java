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
    private boolean tempClosedActive = false;
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

    private final Set<String> maintenanceServers = new HashSet<>();
    private com.velocitypowered.api.scheduler.ScheduledTask activeCountdownTask = null;
    private String countdownServerName = null;
    private int countdownSecondsRemaining = 0;

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

        // Read temp-closed state immediately after config load
        this.tempClosedActive = config.getBoolean("temp-closed.enabled", false);

        if (tempClosedActive) {
            logger.info("[TempClosed] ⛔ Temporary Closed mode AKTIF — DB & NaturalCore sync dinonaktifkan.");
        } else {
            loadWhitelist();

            // Initialize Database (only when not temp-closed)
            this.databaseManager = new DatabaseManager(this, logger);
            if (databaseManager.isEnabled()) {
                databaseManager.connect();
                startDatabasePolling();
            }

            this.maintenanceActive = config.getBoolean("integration.maintenance-mode", false);
            this.messageHandler = new id.naturalsmp.naturalvelocity.messaging.PluginMessageHandler(this);

            // Register Channel
            server.getChannelRegistrar().register(IDENTIFIER);
        }

        // Initialize PacketEvents for HeadMOTD
        initPacketEvents();

        server.getConsoleCommandSource().sendMessage(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "\n&a===============\n" +
                    "&a _   _       _                  _     &e __     __   _            _ _\n" +
                    "&a| \\ | | __ _| |_ _   _ _ __ __ _| |   &e  \\ \\   / /__| | ___   ___(_) |\n" +
                    "&a|  \\| |/ _` | __| | | | '__/ _` | |   &e   \\ \\ / / _ \\| |/ _ \\ / __| | |\n" +
                    "&a| |\\  | (_| | |_| |_| | | | (_| | |   &e    \\ V /  __/ | (_) | (__| | |\n" +
                    "&a|_| \\_|\\__,_|\\__|\\__,_|_|        \\_/ \\&e___|_|\\___/ \\___|_|_|\n" +
                    "       >> &eNaturalVelocity v2.0 Enabled! <<\n" +
                    "&a===============\n"
                )
        );

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

        // Register Maintenance Command
        com.velocitypowered.api.command.CommandMeta mtMeta = cmdManager.metaBuilder("maintenance")
                .aliases("mt")
                .plugin(this)
                .build();
        cmdManager.register(mtMeta, new MaintenanceCommand(this));

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

        // === Temp-Closed MOTD config ===
        String closedSince = config.getString("temp-closed.closed-since", "08/05/2026");
        String closedUntil = config.getString("temp-closed.closed-until", "");
        String untilDisplay = (closedUntil == null || closedUntil.trim().isEmpty()) ? "-?-" : closedUntil;

        headMotdHandler.setTempClosedLine1(config.getString("temp-closed.motd-line1",
                "<b><gradient:#FF4444:#FF8800>NATURAL SMP</gradient></b>    <dark_gray>•</dark_gray> <white>Temporary Closed"));
        headMotdHandler.setTempClosedLine2(config.getString("temp-closed.motd-line2",
                "<gray>» </gray><red><bold>CLOSED</bold></red> <dark_gray>|</dark_gray> <gray>Since "
                + closedSince + " until </gray><white>" + untilDisplay));
        headMotdHandler.setTempClosedActive(this.tempClosedActive);

        // Temp-closed hover
        List<String> tempClosedHover = new ArrayList<>();
        tempClosedHover.add("<gradient:#FF4444:#FF8800><bold>TEMPORARY CLOSED</bold></gradient>");
        tempClosedHover.add("<gray>Dibuka sejak: <white>" + closedSince);
        tempClosedHover.add("<gray>Dibuka kembali: <white>" + untilDisplay);
        tempClosedHover.add("<dark_gray>» <aqua>link.naturalsmp.net</aqua>");
        headMotdHandler.buildTempClosedHoverCache(tempClosedHover);

        // Temp-closed head MOTD URL cache
        String tcMotdCache = mappingCache.get("tempclosed-motd");
        if (tcMotdCache != null && !tcMotdCache.isEmpty()) {
            List<List<String>> tcUrls = new ArrayList<>();
            for (String row : tcMotdCache.split(";")) {
                if (!row.isEmpty())
                    tcUrls.add(Arrays.asList(row.split(",")));
            }
            headMotdHandler.buildTempClosedMotdCache(tcUrls);
            logger.info("[HeadMOTD] Temp-Closed head banner loaded with {} rows.", tcUrls.size());
        }

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
            if (success) {
                source.sendMessage(Component.text("§a[HeadMOTD] ✓ Maintenance MOTD processing complete!"));
            }
        });
    }

    public void processTempClosedMotd(com.velocitypowered.api.command.CommandSource source, int pct) {
        String tcImage = config.getString("temp-closed.motd-image", "");
        if (tcImage == null || tcImage.trim().isEmpty()) {
            source.sendMessage(Component.text(
                    "§c[HeadMOTD] temp-closed.motd-image not set in config. Using text MOTD only."));
            return;
        }
        source.sendMessage(Component.text("§a[HeadMOTD] Starting Temp-Closed MOTD image processing..."));
        processMotdImageAsync(source, pct, "tempclosed-motd", tcImage).thenAccept(success -> {
            if (success) {
                source.sendMessage(Component.text("§a[HeadMOTD] ✓ Temp-Closed MOTD processing complete!"));
                server.getScheduler().buildTask(this, this::reloadHeadMotd).schedule();
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
            } else if (cacheKey.equals("tempclosed-motd")) {
                headMotdHandler.buildTempClosedMotdCache(rows);
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
        this.tempClosedActive = config.getBoolean("temp-closed.enabled", false);
        if (!tempClosedActive) {
            loadWhitelist();
            this.maintenanceActive = config.getBoolean("integration.maintenance-mode", false);
        }
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

    public void saveWhitelist() {
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

    public boolean isTempClosedActive() {
        return tempClosedActive;
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

        // Read TOML first so we can gate SyncServer on temp-closed flag
        this.config = new Toml().read(file);

        boolean isTempClosed = this.config.getBoolean("temp-closed.enabled", false);
        if (!isTempClosed) {
            if (syncServer == null) {
                syncServer = new SyncServer();
                syncServer.runServer(port, password);
            }
        } else {
            logger.info("[TempClosed] SyncServer (NaturalCore) tidak diinisialisasi.");
        }
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
            if (tempClosedActive)
                return;
            if (databaseManager == null || !databaseManager.isEnabled())
                return;

            String status = databaseManager.getMaintenanceStatus();
            if (status == null)
                return;

            List<String[]> dbWhitelist = databaseManager.getMaintenanceWhitelist();
            Set<String> newWhitelist = new HashSet<>();
            for (String[] entry : dbWhitelist) {
                newWhitelist.add(entry[0].toLowerCase());
            }

            if (!newWhitelist.equals(this.whitelistedPlayers)) {
                this.whitelistedPlayers.clear();
                this.whitelistedPlayers.addAll(newWhitelist);
                logger.info("[CoreDB] Maintenance Whitelist updated via MySQL. Size: " + whitelistedPlayers.size());
                saveWhitelist();
            }

            boolean newGlobalActive = false;
            Set<String> newMaintenanceServers = new HashSet<>();

            if (!status.equalsIgnoreCase("false")) {
                if (status.equalsIgnoreCase("true") || status.equalsIgnoreCase("global")) {
                    newGlobalActive = true;
                    newMaintenanceServers.add("global");
                } else {
                    for (String s : status.split(",")) {
                        String clean = s.trim().toLowerCase();
                        if (!clean.isEmpty()) {
                            newMaintenanceServers.add(clean);
                        }
                    }
                }
            }

            boolean globalStateChanged = (newGlobalActive != this.maintenanceActive);
            boolean serversStateChanged = !newMaintenanceServers.equals(this.maintenanceServers);

            if (globalStateChanged || serversStateChanged) {
                this.maintenanceActive = newGlobalActive;
                this.maintenanceServers.clear();
                this.maintenanceServers.addAll(newMaintenanceServers);

                if (headMotdHandler != null) {
                    headMotdHandler.setMaintenanceActive(this.maintenanceActive);
                }

                logger.info("[CoreDB] Maintenance State updated via MySQL. Global: " + maintenanceActive + ", Servers: " + maintenanceServers);
                saveMaintenanceState();
            }

            if (this.maintenanceActive) {
                for (com.velocitypowered.api.proxy.Player player : server.getAllPlayers()) {
                    if (player.hasPermission("naturalvelocity.maintenance.bypass"))
                        continue;
                    if (whitelistedPlayers.contains(player.getUsername().toLowerCase()))
                        continue;
                    String kickReason = config.getString("maintenance.kick-reason");
                    player.disconnect(parse(kickReason));
                }
            } else {
                for (String serverName : this.maintenanceServers) {
                    Optional<com.velocitypowered.api.proxy.server.RegisteredServer> targetServer = server.getServer(serverName);
                    if (targetServer.isPresent()) {
                        Optional<com.velocitypowered.api.proxy.server.RegisteredServer> lobby = server.getServer("lobby");
                        for (com.velocitypowered.api.proxy.Player player : targetServer.get().getPlayersConnected()) {
                            if (player.hasPermission("naturalvelocity.maintenance.bypass"))
                                continue;
                            if (whitelistedPlayers.contains(player.getUsername().toLowerCase()))
                                continue;
                            
                            if (lobby.isPresent()) {
                                player.createConnectionRequest(lobby.get()).fireAndForget();
                                sendMessage(player, Component.text("§c[Maintenance] Server " + serverName + " sedang maintenance. Anda dipindahkan ke Lobby."));
                            } else {
                                String kickReason = config.getString("maintenance.kick-reason");
                                player.disconnect(parse(kickReason));
                            }
                        }
                    }
                }
            }

        }).repeat(10, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    public void startCountdown(String serverName, int seconds, com.velocitypowered.api.command.CommandSource source) {
        if (activeCountdownTask != null) {
            activeCountdownTask.cancel();
            sendMessage(source, Component.text("§c[Maintenance] Countdown sebelumnya dibatalkan karena ada countdown baru."));
        }

        this.countdownServerName = serverName;
        this.countdownSecondsRemaining = seconds;

        sendMessage(source, Component.text("§a[Maintenance] Memulai countdown maintenance untuk " + serverName + " selama " + seconds + " detik."));

        this.activeCountdownTask = server.getScheduler().buildTask(this, () -> {
            if (countdownSecondsRemaining <= 0) {
                activateMaintenance(countdownServerName, server.getConsoleCommandSource());
                cancelCountdown(false);
                return;
            }

            if (countdownSecondsRemaining == 30 || countdownSecondsRemaining == 20 || countdownSecondsRemaining == 10 || 
                (countdownSecondsRemaining <= 5 && countdownSecondsRemaining >= 1)) {
                
                Component announceMsg = Component.text("§c[Maintenance] Server " + 
                        (countdownServerName.equalsIgnoreCase("global") ? "Global" : countdownServerName) + 
                        " akan masuk ke mode maintenance dalam " + countdownSecondsRemaining + " detik!");
                
                if (countdownServerName.equalsIgnoreCase("global")) {
                    for (com.velocitypowered.api.proxy.Player p : server.getAllPlayers()) {
                        sendMessage(p, announceMsg);
                    }
                } else {
                    Optional<com.velocitypowered.api.proxy.server.RegisteredServer> target = server.getServer(countdownServerName);
                    if (target.isPresent()) {
                        for (com.velocitypowered.api.proxy.Player p : target.get().getPlayersConnected()) {
                            sendMessage(p, announceMsg);
                        }
                    }
                }
            }

            countdownSecondsRemaining--;
        }).repeat(1, java.util.concurrent.TimeUnit.SECONDS).schedule();
    }

    public void cancelCountdown(boolean notify) {
        if (activeCountdownTask != null) {
            activeCountdownTask.cancel();
            activeCountdownTask = null;
            if (notify) {
                Component cancelMsg = Component.text("§a[Maintenance] Countdown maintenance untuk " + countdownServerName + " telah dibatalkan.");
                for (com.velocitypowered.api.proxy.Player p : server.getAllPlayers()) {
                    sendMessage(p, cancelMsg);
                }
            }
            countdownServerName = null;
            countdownSecondsRemaining = 0;
        }
    }

    public void activateMaintenance(String serverName, com.velocitypowered.api.command.CommandSource source) {
        logger.info("[Maintenance] Mengaktifkan maintenance untuk: " + serverName);

        if (databaseManager != null && databaseManager.isEnabled()) {
            if (serverName.equalsIgnoreCase("global")) {
                databaseManager.setMaintenanceStatus("global");
            } else {
                String current = databaseManager.getMaintenanceStatus();
                Set<String> servers = new HashSet<>();
                if (current != null && !current.equalsIgnoreCase("false") && !current.equalsIgnoreCase("global") && !current.equalsIgnoreCase("true")) {
                    for (String s : current.split(",")) {
                        if (!s.trim().isEmpty()) servers.add(s.trim().toLowerCase());
                    }
                }
                servers.add(serverName.toLowerCase());
                databaseManager.setMaintenanceStatus(String.join(",", servers));
            }
        }

        if (serverName.equalsIgnoreCase("global")) {
            this.maintenanceActive = true;
            this.maintenanceServers.clear();
            this.maintenanceServers.add("global");
            if (headMotdHandler != null) {
                headMotdHandler.setMaintenanceActive(true);
            }

            for (com.velocitypowered.api.proxy.Player player : server.getAllPlayers()) {
                if (player.hasPermission("naturalvelocity.maintenance.bypass"))
                    continue;
                if (whitelistedPlayers.contains(player.getUsername().toLowerCase()))
                    continue;
                String kickReason = config.getString("maintenance.kick-reason");
                player.disconnect(parse(kickReason));
            }
        } else {
            this.maintenanceServers.add(serverName.toLowerCase());

            Optional<com.velocitypowered.api.proxy.server.RegisteredServer> target = server.getServer(serverName);
            if (target.isPresent()) {
                Optional<com.velocitypowered.api.proxy.server.RegisteredServer> lobby = server.getServer("lobby");
                for (com.velocitypowered.api.proxy.Player player : target.get().getPlayersConnected()) {
                    if (player.hasPermission("naturalvelocity.maintenance.bypass"))
                        continue;
                    if (whitelistedPlayers.contains(player.getUsername().toLowerCase()))
                        continue;

                    if (lobby.isPresent()) {
                        player.createConnectionRequest(lobby.get()).fireAndForget();
                        sendMessage(player, Component.text("§c[Maintenance] Server " + serverName + " sedang maintenance. Anda dipindahkan ke Lobby."));
                    } else {
                        String kickReason = config.getString("maintenance.kick-reason");
                        player.disconnect(parse(kickReason));
                    }
                }
            }
        }

        saveMaintenanceState();
        sendMessage(source, Component.text("§a[Maintenance] Berhasil mengaktifkan maintenance untuk " + serverName + "."));
    }

    public void deactivateMaintenance(String serverName, com.velocitypowered.api.command.CommandSource source) {
        logger.info("[Maintenance] Menonaktifkan maintenance untuk: " + serverName);

        if (databaseManager != null && databaseManager.isEnabled()) {
            if (serverName.equalsIgnoreCase("global")) {
                databaseManager.setMaintenanceStatus("false");
            } else {
                String current = databaseManager.getMaintenanceStatus();
                if (current != null && !current.equalsIgnoreCase("false") && !current.equalsIgnoreCase("global") && !current.equalsIgnoreCase("true")) {
                    Set<String> servers = new HashSet<>();
                    for (String s : current.split(",")) {
                        String clean = s.trim().toLowerCase();
                        if (!clean.isEmpty() && !clean.equalsIgnoreCase(serverName.toLowerCase())) {
                            servers.add(clean);
                        }
                    }
                    if (servers.isEmpty()) {
                        databaseManager.setMaintenanceStatus("false");
                    } else {
                        databaseManager.setMaintenanceStatus(String.join(",", servers));
                    }
                } else {
                    databaseManager.setMaintenanceStatus("false");
                }
            }
        }

        if (serverName.equalsIgnoreCase("global")) {
            this.maintenanceActive = false;
            this.maintenanceServers.clear();
            if (headMotdHandler != null) {
                headMotdHandler.setMaintenanceActive(false);
            }
        } else {
            this.maintenanceServers.remove(serverName.toLowerCase());
            if (this.maintenanceServers.isEmpty()) {
                this.maintenanceActive = false;
                if (headMotdHandler != null) {
                    headMotdHandler.setMaintenanceActive(false);
                }
            }
        }

        saveMaintenanceState();
        sendMessage(source, Component.text("§a[Maintenance] Berhasil menonaktifkan maintenance untuk " + serverName + "."));
    }

    public Set<String> getMaintenanceServers() {
        return maintenanceServers;
    }

    public com.velocitypowered.api.scheduler.ScheduledTask getActiveCountdownTask() {
        return activeCountdownTask;
    }

    public String getCountdownServerName() {
        return countdownServerName;
    }

    public int getCountdownSecondsRemaining() {
        return countdownSecondsRemaining;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
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

    public static void sendMessage(com.velocitypowered.api.command.CommandSource source, Component component) {
        if (source == null) return;
        if (source instanceof com.velocitypowered.api.proxy.ConsoleCommandSource) {
            String plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
            plainText = plainText.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", "");
            source.sendMessage(Component.text(plainText));
        } else {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
            if (plain.contains("§")) {
                source.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(plain));
            } else {
                source.sendMessage(component);
            }
        }
    }
}
