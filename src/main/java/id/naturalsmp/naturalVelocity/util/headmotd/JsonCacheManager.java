package id.naturalsmp.naturalvelocity.util.headmotd;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class JsonCacheManager {
    private JsonArray motdJsonCache = new JsonArray();
    private JsonArray hoverJsonCache = new JsonArray();

    public void buildMotdCache(List<List<String>> motdUrls) {
        JsonArray newMotdCache = new JsonArray();
        if (!motdUrls.isEmpty()) {
            for (int i = 0; i < Math.min(2, motdUrls.size()); i++) {
                motdUrls.get(i).forEach(url -> newMotdCache.add(createHeadJson(url)));
                // Insert a newline between rows 1 and 2
                if (i < motdUrls.size() - 1 && i < 1) {
                    JsonObject newline = new JsonObject();
                    newline.addProperty("text", "\n");
                    newMotdCache.add(newline);
                }
            }
        }
        this.motdJsonCache = newMotdCache;
    }

    public void buildHoverCache(List<String> rawHover) {
        JsonArray newHoverCache = new JsonArray();
        for (String msg : rawHover) {
            Component component = msg.contains("<") && msg.contains(">")
                    ? MiniMessage.miniMessage().deserialize(msg)
                    : LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
            String legacy = LegacyComponentSerializer.legacySection().serialize(component);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", legacy);
            entry.addProperty("id", UUID.randomUUID().toString());
            newHoverCache.add(entry);
        }
        this.hoverJsonCache = newHoverCache;
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

    public JsonArray getMotdCache() {
        return motdJsonCache;
    }

    public JsonArray getHoverCache() {
        return hoverJsonCache;
    }
}
