package id.naturalsmp.naturalvelocity.util.headmotd;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.*;

import id.naturalsmp.naturalvelocity.NaturalVelocity;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MotdHandler implements PacketListener {
    // Bedrock clients connected via Geyser/Floodgate get an attribute on the Netty
    // channel
    private static final AttributeKey<?> FLOODGATE_ATTR = AttributeKey.valueOf("floodgate-player");
    private final NaturalVelocity plugin;

    public MotdHandler(NaturalVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Status.Server.RESPONSE) {

            // Skip Bedrock clients if configured, since Bedrock clients can crash or bug
            // out with custom head components
            if (plugin.getConfig().getBoolean("head-motd.ignore-bedrock", true) && isBedrock(event)) {
                return;
            }

            WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(event);
            String originalJson = wrapper.getComponentJson();
            JsonObject fullStatus = (originalJson != null) ? JsonParser.parseString(originalJson).getAsJsonObject()
                    : new JsonObject();

            JsonCacheManager cacheManager = plugin.getJsonCacheManager();
            if (cacheManager == null)
                return;

            JsonArray hoverCache = cacheManager.getHoverCache();
            JsonObject players = fullStatus.getAsJsonObject("players");

            if (players == null) {
                players = new JsonObject();
                players.addProperty("max", 0);
                players.addProperty("online", 0);
                fullStatus.add("players", players);
            }

            if (hoverCache.size() > 0) {
                players.add("sample", hoverCache);
            }

            // Optional Always +1 Players logic
            if (plugin.getConfig().getBoolean("head-motd.always-plus-one", false)) {
                int online = players.has("online") ? players.get("online").getAsInt() : 0;
                players.addProperty("max", online + 1);
            }

            // Minimum protocol 773 is 1.21.9/1.21.10+ containing the modern ObjectComponent
            // features used for heads in chat/motd
            int minProtocol = plugin.getConfig().getLong("head-motd.minimum-protocol", 773L).intValue();

            if (event.getUser().getClientVersion().getProtocolVersion() >= minProtocol) {
                JsonArray motdCache = cacheManager.getMotdCache();
                if (motdCache.size() > 0) {
                    JsonObject description = new JsonObject();
                    description.addProperty("color", "white");
                    description.addProperty("italic", false);
                    description.addProperty("bold", false);
                    description.add("extra", motdCache);
                    description.addProperty("shadow_color", -1);
                    description.addProperty("text", "");
                    fullStatus.add("description", description);
                }
            } else {
                List<String> fallback = plugin.getConfig().getList("head-motd.fallback-motd");
                if (fallback != null && !fallback.isEmpty()) {
                    String line1 = fallback.get(0);
                    String line2 = fallback.size() > 1 ? fallback.get(1) : "";
                    fullStatus.add("description", createTextComponent(line1 + "\n" + line2));
                }
            }

            wrapper.setComponentJson(fullStatus.toString());
            wrapper.write();
            event.markForReEncode(true);
        }
    }

    private boolean isBedrock(PacketSendEvent event) {
        try {
            Object channelObj = event.getUser().getChannel();
            if (channelObj instanceof Channel channel) {
                return channel.hasAttr(FLOODGATE_ATTR) && channel.attr(FLOODGATE_ATTR).get() != null;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private JsonObject createTextComponent(String text) {
        Component component = text.contains("<") && text.contains(">")
                ? MiniMessage.miniMessage().deserialize(text)
                : LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(component)).getAsJsonObject();
    }
}
