package id.naturalsmp.naturalvelocity.messaging;

import id.naturalsmp.naturalvelocity.NaturalVelocity;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PluginMessageHandler {

    private final NaturalVelocity plugin;
    public static final MinecraftChannelIdentifier IDENTIFIER = NaturalVelocity.IDENTIFIER;

    public PluginMessageHandler(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends a data packet to a specific server.
     */
    public void sendToCore(ServerConnection server, String subChannel, Object... data) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
            out.writeUTF(subChannel);
            for (Object obj : data) {
                if (obj instanceof String)
                    out.writeUTF((String) obj);
                else if (obj instanceof Integer)
                    out.writeInt((Integer) obj);
                else if (obj instanceof Boolean)
                    out.writeBoolean((Boolean) obj);
                else if (obj instanceof Double)
                    out.writeDouble((Double) obj);
            }
            server.sendPluginMessage(IDENTIFIER, b.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Broadcasts a data packet to all connected servers.
     */
    public void broadcastToAll(String subChannel, Object... data) {
        plugin.getServer().getAllServers().forEach(server -> {
            server.getPlayersConnected().stream().findFirst().ifPresent(player -> {
                player.getCurrentServer().ifPresent(serverConn -> {
                    sendToCore(serverConn, subChannel, data);
                });
            });
        });
    }
}
