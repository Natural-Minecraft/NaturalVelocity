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
        logger.info("NaturalVelocity has been initialized! 🚀");

        // Register Ping Listener
        server.getEventManager().register(this, new PingListener(this));
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

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }
}
