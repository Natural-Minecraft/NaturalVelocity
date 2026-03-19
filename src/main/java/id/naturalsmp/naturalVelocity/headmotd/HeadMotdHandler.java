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
 * hover messages, always+1 slots, Bedrock exclusion, and maintenance mode.
 */
public class HeadMotdHandler implements PacketListener {
    private static final AttributeKey<?> FLOODGATE_ATTR = AttributeKey.valueOf("floodgate-player");

    // Normal MOTD cached JSON arrays
    private final List<JsonArray> motdJsonCaches = new CopyOnWriteArrayList<>();
    private int rotatingDelay = 30; // seconds
    private JsonArray hoverJsonCache = new JsonArray();

    // Maintenance MOTD cached JSON arrays
    private JsonArray maintenanceMotdJsonCache = new JsonArray();
    private JsonArray maintenanceHoverJsonCache = new JsonArray();

    // Config state
    private boolean enabled = false;
    private boolean alwaysPlusOne = true;
    private boolean ignoreBedrock = true;
    private int minimumProtocol = 773;
    private int maximumProtocol = 775;
    private String fallbackLine1 = "";
    private String fallbackLine2 = "";
    private final List<List<List<String>>> motdUrls = new CopyOnWriteArrayList<>();

    // Maintenance state
    private volatile boolean maintenanceActive = false;
    private String maintenanceLine1 = "";
    private String maintenanceLine2 = "";

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

        int clientProtocol = event.getUser().getClientVersion().getProtocolVersion();

        if (maintenanceActive) {
            // ========== MAINTENANCE MODE ==========

            // 1. Override version name to §cMAINTENANCE
            JsonObject version = fullStatus.getAsJsonObject("version");
            if (version == null) {
                version = new JsonObject();
                version.addProperty("protocol", clientProtocol);
                fullStatus.add("version", version);
            }
            version.addProperty("name", "\u00A7cMAINTENANCE");

            // 2. Override players.sample with UNDER MAINTENANCE hover
            JsonObject players = fullStatus.getAsJsonObject("players");
            if (players == null) {
                players = new JsonObject();
                players.addProperty("max", 0);
                players.addProperty("online", 0);
                fullStatus.add("players", players);
            }
            if (maintenanceHoverJsonCache.size() > 0) {
                players.add("sample", maintenanceHoverJsonCache);
            }

            // 3. Maintenance MOTD
            if (clientProtocol >= minimumProtocol && clientProtocol <= maximumProtocol && maintenanceMotdJsonCache.size() > 0) {
                // Show maintenance head banner for supported protocol
                JsonObject description = new JsonObject();
                description.addProperty("color", "white");
                description.addProperty("shadow_color", -1);
                description.add("extra", maintenanceMotdJsonCache);
                description.addProperty("text", "");
                fullStatus.add("description", description);
            } else {
                // Show maintenance text MOTD (fallback or for unsupported protocol)
                if (!maintenanceLine1.isEmpty() || !maintenanceLine2.isEmpty()) {
                    String combined = maintenanceLine1 + "\n" + maintenanceLine2;
                    fullStatus.add("description", createTextComponent(combined));
                }
            }

        } else {
            // ========== NORMAL MODE ==========

            // 1. Inject hover messages into players.sample
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

            // 2. Always +1 slots
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

            // 3. Head MOTD (protocol check — range support)
            if (clientProtocol >= minimumProtocol && clientProtocol <= maximumProtocol) {
                if (!motdJsonCaches.isEmpty()) {
                    int index = (int) ((System.currentTimeMillis() / (rotatingDelay * 1000L)) % motdJsonCaches.size());
                    JsonArray currentMotdCache = motdJsonCaches.get(index);
                    JsonObject description = new JsonObject();
                    description.addProperty("color", "white");
                    description.addProperty("shadow_color", -1);
                    description.add("extra", currentMotdCache);
                    description.addProperty("text", "");
                    fullStatus.add("description", description);
                }
            } else {
                // Fallback MOTD for older/newer clients
                if (!fallbackLine1.isEmpty() || !fallbackLine2.isEmpty()) {
                    String combined = fallbackLine1 + "\n" + fallbackLine2;
                    fullStatus.add("description", createTextComponent(combined));
                }
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

    public void buildMotdCaches(List<List<List<String>>> multiUrls) {
        this.motdUrls.clear();
        this.motdUrls.addAll(multiUrls);
        this.motdJsonCaches.clear();
        for (List<List<String>> urls : multiUrls) {
            this.motdJsonCaches.add(buildHeadCacheFromUrls(urls));
        }
    }

    public void buildMaintenanceMotdCache(List<List<String>> urls) {
        this.maintenanceMotdJsonCache = buildHeadCacheFromUrls(urls);
    }

    private JsonArray buildHeadCacheFromUrls(List<List<String>> urls) {
        JsonArray newCache = new JsonArray();
        if (!urls.isEmpty()) {
            for (int i = 0; i < Math.min(2, urls.size()); i++) {
                urls.get(i).forEach(url -> newCache.add(createHeadJson(url)));
                if (i < urls.size() - 1 && i < 1) {
                    // Invisible black dot with newline (matches ZedarMC format)
                    JsonObject newline = new JsonObject();
                    newline.addProperty("color", "black");
                    newline.addProperty("shadow_color", 0);
                    JsonArray nlExtra = new JsonArray();
                    nlExtra.add("\n");
                    newline.add("extra", nlExtra);
                    newline.addProperty("text", ".");
                    newCache.add(newline);
                }
            }
            // Trailing invisible black dot
            JsonObject trailing = new JsonObject();
            trailing.addProperty("color", "black");
            trailing.addProperty("shadow_color", 0);
            trailing.addProperty("text", ".");
            newCache.add(trailing);
        }
        return newCache;
    }

    public void buildHoverCache(List<String> rawHover) {
        this.hoverJsonCache = buildHoverCacheFromLines(rawHover);
    }

    public void buildMaintenanceHoverCache(List<String> rawHover) {
        this.maintenanceHoverJsonCache = buildHoverCacheFromLines(rawHover);
    }

    private JsonArray buildHoverCacheFromLines(List<String> rawHover) {
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
        return newCache;
    }

    private JsonObject createHeadJson(String url) {
        String textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String textureBase64 = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
        JsonObject player = new JsonObject();
        JsonArray properties = new JsonArray();
        JsonObject textureProp = new JsonObject();
        textureProp.addProperty("name", "textures");
        textureProp.addProperty("value", textureBase64);
        properties.add(textureProp);
        player.add("properties", properties);
        JsonObject headObj = new JsonObject();
        headObj.addProperty("hat", true);
        headObj.add("player", player);
        return headObj;
    }

    // ========== Config Setters ==========

    public void setRotatingDelay(int rotatingDelay) {
        this.rotatingDelay = rotatingDelay > 0 ? rotatingDelay : 1;
    }

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

    public void setMaximumProtocol(int maximumProtocol) {
        this.maximumProtocol = maximumProtocol;
    }

    public void setFallbackLine1(String fallbackLine1) {
        this.fallbackLine1 = fallbackLine1;
    }

    public void setFallbackLine2(String fallbackLine2) {
        this.fallbackLine2 = fallbackLine2;
    }

    public void setMaintenanceActive(boolean maintenanceActive) {
        this.maintenanceActive = maintenanceActive;
    }

    public void setMaintenanceLine1(String maintenanceLine1) {
        this.maintenanceLine1 = maintenanceLine1;
    }

    public void setMaintenanceLine2(String maintenanceLine2) {
        this.maintenanceLine2 = maintenanceLine2;
    }

    // ========== Getters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMaintenanceActive() {
        return maintenanceActive;
    }

    public List<List<List<String>>> getMotdUrls() {
        return motdUrls;
    }

    public List<JsonArray> getMotdJsonCaches() {
        return motdJsonCaches;
    }

    public JsonArray getMaintenanceMotdJsonCache() {
        return maintenanceMotdJsonCache;
    }
}
