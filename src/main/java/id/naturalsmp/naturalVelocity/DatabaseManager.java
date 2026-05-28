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

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                logger.error("[CoreDB] MySQL Driver not found! Ensure it is shaded correctly.");
                return false;
            }

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
            setupTable();
            return true;
        } catch (SQLException e) {
            logger.error("[CoreDB] Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }

    private void setupTable() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS nvelo_mt (" +
                    "id INT PRIMARY KEY, " +
                    "username VARCHAR(64), " +
                    "uuid VARCHAR(64), " +
                    "value VARCHAR(255))");
            
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM nvelo_mt WHERE id = 0")) {
                if (!rs.next()) {
                    try (PreparedStatement insertStmt = connection.prepareStatement(
                            "INSERT INTO nvelo_mt (id, username, uuid, value) VALUES (0, NULL, NULL, 'false')")) {
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("[CoreDB] Failed to setup table nvelo_mt: " + e.getMessage());
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

    public String getMaintenanceStatus() {
        if (!connect())
            return "false";

        String query = "SELECT value FROM nvelo_mt WHERE id = 0";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String val = rs.getString("value");
                return val != null ? val : "false";
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to get maintenance state from nvelo_mt: " + e.getMessage());
        }
        return "false";
    }

    public boolean setMaintenanceStatus(String status) {
        if (!connect())
            return false;

        String query = "UPDATE nvelo_mt SET value = ? WHERE id = 0";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, status);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to set maintenance status in nvelo_mt: " + e.getMessage());
            return false;
        }
    }

    public List<String[]> getMaintenanceWhitelist() {
        List<String[]> whitelist = new ArrayList<>();
        if (!connect())
            return whitelist;

        String query = "SELECT username, uuid FROM nvelo_mt WHERE id >= 1";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String user = rs.getString("username");
                String idVal = rs.getString("uuid");
                if (user != null) {
                    whitelist.add(new String[]{user, idVal != null ? idVal : ""});
                }
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to get maintenance whitelist from nvelo_mt: " + e.getMessage());
        }
        return whitelist;
    }

    public boolean addPlayerToWhitelist(String username, String uuid) {
        if (!connect())
            return false;

        int nextId = 1;
        String maxIdQuery = "SELECT MAX(id) FROM nvelo_mt";
        try (PreparedStatement stmt = connection.prepareStatement(maxIdQuery);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int max = rs.getInt(1);
                if (max >= 0) {
                    nextId = max + 1;
                }
            }
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to retrieve max ID from nvelo_mt: " + e.getMessage());
        }

        String query = "INSERT INTO nvelo_mt (id, username, uuid, value) VALUES (?, ?, ?, NULL)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, nextId);
            stmt.setString(2, username.toLowerCase());
            stmt.setString(3, uuid);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to add player to whitelist in nvelo_mt: " + e.getMessage());
            return false;
        }
    }

    public boolean removePlayerFromWhitelist(String username) {
        if (!connect())
            return false;

        String query = "DELETE FROM nvelo_mt WHERE username = ? AND id >= 1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warn("[CoreDB] Failed to remove player from whitelist in nvelo_mt: " + e.getMessage());
            return false;
        }
    }
}
