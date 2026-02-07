package id.naturalsmp.naturalvelocity;

import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private final NaturalVelocity plugin;
    private final Logger logger;
    private Connection connection;

    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean enabled;

    public DatabaseManager(NaturalVelocity plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        loadConfig();
    }

    private void loadConfig() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("database.enabled", false);
        this.host = config.getString("database.host", "localhost");
        this.port = config.getLong("database.port", 3306L).intValue();
        this.database = config.getString("database.database", "naturalsmp_core");
        this.username = config.getString("database.username", "root");
        this.password = config.getString("database.password", "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean connect() {
        if (!enabled)
            return false;

        try {
            if (connection != null && !connection.isClosed() && connection.isValid(1)) {
                return true;
            }

            // Explicitly load driver for shaded environments
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                logger.error("[CoreDB] MySQL Driver not found! Ensure it is shaded correctly.");
                return false;
            }

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
            return true;
        } catch (SQLException e) {
            logger.error("[CoreDB] Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Error closing connection: " + e.getMessage());
        }
    }

    public boolean getMaintenanceActive() {
        if (!connect())
            return false;

        String query = "SELECT `value` FROM core_state WHERE `key` = 'maintenance_active'";
        try (PreparedStatement stmt = connection.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Boolean.parseBoolean(rs.getString("value"));
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to get maintenance state: " + e.getMessage());
        }
        return false;
    }

    public List<String> getMaintenanceWhitelist() {
        List<String> whitelist = new ArrayList<>();
        if (!connect())
            return whitelist;

        String query = "SELECT `value` FROM core_state WHERE `key` = 'maintenance_whitelist'";
        try (PreparedStatement stmt = connection.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String value = rs.getString("value");
                // Simple parsing for [p1, p2, p3]
                String clean = value.replace("[", "").replace("]", "").replace(" ", "");
                if (!clean.isEmpty()) {
                    for (String s : clean.split(",")) {
                        whitelist.add(s.toLowerCase());
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to get maintenance whitelist: " + e.getMessage());
        }
        return whitelist;
    }
}
