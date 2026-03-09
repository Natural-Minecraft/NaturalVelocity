package id.naturalsmp.naturalvelocity.util.headmotd;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;

import id.naturalsmp.naturalvelocity.NaturalVelocity;

public class ImageProcessor {
    private final MineSkinClient client;
    private final TextureCache cache;
    private final File dataDir;
    // Fallback to cached thread pool since virtual threads aren't in Java 17
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final NaturalVelocity plugin;

    public ImageProcessor(MineSkinClient c, TextureCache t, File d, NaturalVelocity p) {
        this.client = c;
        this.cache = t;
        this.dataDir = d;
        this.plugin = p;
    }

    public CompletableFuture<List<List<String>>> process(File file, int pct) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(file);
                int w = img.getWidth();
                int h = img.getHeight();
                if (w > 264 || w % 8 != 0 || (h != 8 && h != 16)) {
                    plugin.getLogger().error("Invalid image dimensions: " + w + "x" + h
                            + ". Must be exactly 8 or 16 pixels tall, and width must be multiple of 8 (max 264).");
                    throw new RuntimeException("Invalid image dimensions");
                }
                List<List<ImageSlicer.Segment>> rows = ImageSlicer.slice(file);

                int total = rows.stream().mapToInt(r -> (int) Math.ceil(r.size() * (pct / 100.0))).sum();
                plugin.getLogger()
                        .info("Found valid " + w + "x" + h + " MOTD image. Total texture blocks to process: " + total);

                return processRows(rows, pct, total);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private List<List<String>> processRows(List<List<ImageSlicer.Segment>> rows, int pct, int total) throws Exception {
        List<List<String>> rowUrls = new ArrayList<>();
        int count = 0;
        for (List<ImageSlicer.Segment> segments : rows) {
            List<String> urls = new ArrayList<>();
            int limit = (int) Math.ceil(segments.size() * (pct / 100.0));
            for (int i = 0; i < limit; i++) {
                count++;
                urls.add(processSegment(segments.get(i), count, total));
            }
            if (!urls.isEmpty())
                rowUrls.add(urls);
        }
        return rowUrls;
    }

    private String processSegment(ImageSlicer.Segment seg, int cur, int total) throws Exception {
        String cached = cache.get(seg.hash());
        if (cached != null) {
            return cached;
        }

        plugin.getLogger().info("Uploading MineSkin texture " + cur + "/" + total + "...");
        Path temp = ImageSlicer.saveTempSegment(seg.image(), seg.hash(), dataDir);
        try {
            String url = client.upload(temp).join();
            if (url == null || url.isEmpty()) {
                throw new Exception("MineSkin API returned an empty URL for segment " + cur);
            }
            cache.put(seg.hash(), url);
            plugin.getLogger().info("Successfully uploaded segment " + cur);

            // Respect MineSkin public rate limits (2000ms delay)
            Thread.sleep(2000);
            return url;
        } finally {
            temp.toFile().delete();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
