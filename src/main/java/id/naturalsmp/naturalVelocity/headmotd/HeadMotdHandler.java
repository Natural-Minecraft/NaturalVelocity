package id.naturalsmp.naturalvelocity.headmotd;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.*;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HeadMotdHandler - Merged HeadPacket MotdHandler + JsonCacheManager
 * Intercepts STATUS_RESPONSE packets and injects head pixel art MOTD,
 * hover messages, always+1 slots, and Bedrock exclusion.
 */
public class HeadMotdHandler implements PacketListener {
    private static final AttributeKey<?> FLOODGATE_ATTR = AttributeKey.valueOf("floodgate-player");

    // Cached JSON arrays
    private JsonArray motdJsonCache = new JsonArray();
    private JsonArray hoverJsonCache = new JsonArray();

    // Config state
    private boolean enabled = false;
    private boolean alwaysPlusOne = true;
    private boolean ignoreBedrock = true;
    private int minimumProtocol = 773;
    private String fallbackLine1 = "";
    private String fallbackLine2 = "";
    private final List<List<String>> motdUrls = new CopyOnWriteArrayList<>();

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!enabled)
            return;
        if (event.getPacketType() != PacketType.Status.Server.RESPONSE)
            return;
        if (ignoreBedrock && isBedrock(event))
            return;

        WrapperStatusServerResponse wrapper = new WrapperStatusServerResponse(event);
        String originalJson = wrapper.getComponentJson();
        JsonObject fullStatus = (originalJson != null)
                ? JsonParser.parseString(originalJson).getAsJsonObject()
                : new JsonObject();

        // Inject hover messages into players.sample
        if (hoverJsonCache.size() > 0) {
            JsonObject players = fullStatus.getAsJsonObject("players");
            if (players == null) {
                players = new JsonObject();
                players.addProperty("max", 0);
                players.addProperty("online", 0);
                fullStatus.add("players", players);
            }
            players.add("sample", hoverJsonCache);
        }

        // Always +1 slots
        if (alwaysPlusOne) {
            JsonObject players = fullStatus.getAsJsonObject("players");
            if (players != null) {
                int online = players.has("online") ? players.get("online").getAsInt() : 0;
                int originalMax = players.has("max") ? players.get("max").getAsInt() : 20;
                int nextMax = online == 0 ? originalMax : online + 1;
                if (nextMax > 69) {
                    nextMax = 69;
                }
                players.addProperty("max", nextMax);
            }
        }

        // Head MOTD (protocol check)
        if (event.getUser().getClientVersion().getProtocolVersion() >= minimumProtocol) {
            if (motdJsonCache.size() > 0) {
                // For 1.21.x MOTD parsing, it expects a strict text component wrapper
                JsonObject description = new JsonObject();
                description.addProperty("text", "");
                description.add("extra", motdJsonCache);
                fullStatus.add("description", description);
            }
        } else {
            // Fallback MOTD for older clients
            if (!fallbackLine1.isEmpty() || !fallbackLine2.isEmpty()) {
                String combined = fallbackLine1 + "\n" + fallbackLine2;
                fullStatus.add("description", createTextComponent(combined));
            }
        }

        wrapper.setComponentJson(fullStatus.toString());
        wrapper.write();
        event.markForReEncode(true);
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
        if (text == null)
            text = "";
        String processed = text.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        processed = processed
                .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");
        Component component = MiniMessage.miniMessage().deserialize(processed);
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(component)).getAsJsonObject();
    }

    // ========== Cache Builders ==========

    public void buildMotdCache(List<List<String>> urls) {
        this.motdUrls.clear();
        this.motdUrls.addAll(urls);
        JsonArray newCache = new JsonArray();
        if (!urls.isEmpty()) {
            for (int i = 0; i < Math.min(2, urls.size()); i++) {
                urls.get(i).forEach(url -> newCache.add(createHeadJson(url)));
                if (i < urls.size() - 1 && i < 1) {
                    JsonObject newline = new JsonObject();
                    newline.addProperty("text", "\n");
                    newCache.add(newline);
                }
            }
        }
        this.motdJsonCache = newCache;
    }

    public void buildHoverCache(List<String> rawHover) {
        JsonArray newCache = new JsonArray();
        for (String msg : rawHover) {
            String processed = msg.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
            processed = processed
                    .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                    .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                    .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                    .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                    .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                    .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");
            Component component = MiniMessage.miniMessage().deserialize(processed);
            String legacy = LegacyComponentSerializer.legacySection().serialize(component);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", legacy);
            entry.addProperty("id", UUID.randomUUID().toString());
            newCache.add(entry);
        }
        this.hoverJsonCache = newCache;
    }

    private JsonObject createHeadJson(String url) {
        String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String textureBase64 = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
        JsonObject player = new JsonObject();
        player.addProperty("name", "");
        JsonArray properties = new JsonArray();
        JsonObject textureProp = new JsonObject();
        textureProp.addProperty("name", "textures");
        textureProp.addProperty("value", textureBase64);
        properties.add(textureProp);
        player.add("properties", properties);
        JsonObject headObj = new JsonObject();
        headObj.addProperty("hat", true);
        headObj.addProperty("italic", false);
        headObj.addProperty("shadow", false);
        headObj.add("player", player);
        return headObj;
    }

    // ========== Config Setters ==========

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAlwaysPlusOne(boolean alwaysPlusOne) {
        this.alwaysPlusOne = alwaysPlusOne;
    }

    public void setIgnoreBedrock(boolean ignoreBedrock) {
        this.ignoreBedrock = ignoreBedrock;
    }

    public void setMinimumProtocol(int minimumProtocol) {
        this.minimumProtocol = minimumProtocol;
    }

    public void setFallbackLine1(String fallbackLine1) {
        this.fallbackLine1 = fallbackLine1;
    }

    public void setFallbackLine2(String fallbackLine2) {
        this.fallbackLine2 = fallbackLine2;
    }

    // ========== Getters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public List<List<String>> getMotdUrls() {
        return motdUrls;
    }

    public JsonArray getMotdJsonCache() {
        return motdJsonCache;
    }
}
