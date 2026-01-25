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

    @Inject
    public NaturalVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        // Load maintenance state from config or separate file
        this.maintenanceActive = config.getBoolean("integration.maintenance-mode", false);

        logger.info("NaturalVelocity has been initialized! 🚀");

        // Register Channel
        server.getChannelRegistrar().register(IDENTIFIER);

        // Register Listeners
        server.getEventManager().register(this, new PingListener(this));
        server.getEventManager().register(this, new MaintenanceListener(this));
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveMaintenanceState() {
        File file = new File(dataDirectory.toFile(), "velocity.toml");
        try {
            // Read current file content
            String content = new String(Files.readAllBytes(file.toPath()));

            // Simple replace for persistence (avoid over-complicating with full TOML writer
            // if possible)
            String target = "maintenance-mode = " + (!maintenanceActive);
            String replacement = "maintenance-mode = " + maintenanceActive;

            if (content.contains(target)) {
                content = content.replace(target, replacement);
            } else {
                // If not found, we might need a more robust approach or just log it
                logger.warn("Could not find maintenance-mode key in velocity.toml for persistent save.");
            }

            Files.write(file.toPath(), content.getBytes());
        } catch (IOException e) {
            logger.error("Failed to save maintenance state!", e);
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
}
