package id.naturalsmp.naturalvelocity.headmotd;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class MineSkinClient {
    private final String apiKey;
    private final HttpClient client = HttpClient.newHttpClient();

    public MineSkinClient(String key) {
        this.apiKey = key;
    }

    public CompletableFuture<String> upload(Path file) {
        String boundary = "---" + System.currentTimeMillis();
        return client.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://api.mineskin.org/generate/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(createMultipart(file, boundary)))
                .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (json.has("data") && json.getAsJsonObject("data").has("texture")) {
                        return json.getAsJsonObject("data").getAsJsonObject("texture").get("url").getAsString();
                    } else if (json.has("error")) {
                        String errMsg = json.get("error").isJsonObject() ? json.getAsJsonObject("error").toString()
                                : json.get("error").getAsString();
                        throw new RuntimeException("MineSkin API Error: " + errMsg);
                    } else if (json.has("message")) {
                        throw new RuntimeException("MineSkin API Error: " + json.get("message").getAsString());
                    } else {
                        throw new RuntimeException("MineSkin API Unknown Response: " + res.body());
                    }
                });
    }

    private byte[] createMultipart(Path file, String b) {
        try {
            String head = "--" + b
                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"head.png\"\r\nContent-Type: image/png\r\n\r\n";
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file);
            String foot = "\r\n--" + b + "--\r\n";
            byte[] res = new byte[head.length() + fileBytes.length + foot.length()];
            System.arraycopy(head.getBytes(), 0, res, 0, head.length());
            System.arraycopy(fileBytes, 0, res, head.length(), fileBytes.length);
            System.arraycopy(foot.getBytes(), 0, res, head.length() + fileBytes.length, foot.length());
            return res;
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
