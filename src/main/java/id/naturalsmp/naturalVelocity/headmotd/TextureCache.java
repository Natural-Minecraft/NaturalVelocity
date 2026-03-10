package id.naturalsmp.naturalvelocity.headmotd;

import java.io.*;
import java.util.*;
import com.google.gson.*;

public class TextureCache {
    private final File file;
    private final Map<String, String> cache = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public TextureCache(File f) {
        this.file = f;
        load();
    }

    public void load() {
        if (!file.exists())
            return;
        try (Reader r = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            json.entrySet().forEach(e -> cache.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception ignored) {
        }
    }

    public void save() {
        try (Writer w = new FileWriter(file)) {
            gson.toJson(cache, w);
        } catch (Exception ignored) {
        }
    }

    public void put(String k, String v) {
        cache.put(k, v);
        save();
    }

    public String get(String k) {
        return cache.get(k);
    }
}
