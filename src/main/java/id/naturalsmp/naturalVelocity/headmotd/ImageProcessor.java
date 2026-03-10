package id.naturalsmp.naturalvelocity.headmotd;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import org.slf4j.Logger;

public class ImageProcessor {
    private final MineSkinClient client;
    private final TextureCache cache;
    private final File dataDir;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final int mineSkinDelay;

    public ImageProcessor(MineSkinClient client, TextureCache cache, File dataDir, Logger logger, int mineSkinDelay) {
        this.client = client;
        this.cache = cache;
        this.dataDir = dataDir;
        this.logger = logger;
        this.mineSkinDelay = mineSkinDelay;
    }

    public CompletableFuture<List<List<String>>> process(File file, int pct) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(file);
                int w = img.getWidth();
                int h = img.getHeight();
                if (w > 264 || w % 8 != 0 || (h != 8 && h != 16)) {
                    throw new RuntimeException("Invalid image dimensions: " + w + "x" + h
                            + " (must be multiple of 8 width, max 264, height 8 or 16)");
                }
                List<List<ImageSlicer.Segment>> rows = ImageSlicer.slice(file);

                int total = rows.stream().mapToInt(r -> (int) Math.ceil(r.size() * (pct / 100.0))).sum();
                logger.info("[HeadMOTD] Processing image: {}x{}, total segments: {}", w, h, total);
                return processRows(rows, pct, total);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private List<List<String>> processRows(List<List<ImageSlicer.Segment>> rows, int pct, int total) throws Exception {
        List<List<String>> rowUrls = new ArrayList<>();
        int count = 0;
        for (var segments : rows) {
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
            logger.info("[HeadMOTD] Segment {}/{} - cached ✓", cur, total);
            return cached;
        }
        logger.info("[HeadMOTD] Segment {}/{} - uploading to MineSkin...", cur, total);
        Path temp = ImageSlicer.saveTempSegment(seg.image(), seg.hash(), dataDir);
        try {
            String url = client.upload(temp).join();
            cache.put(seg.hash(), url);
            logger.info("[HeadMOTD] Segment {}/{} - done ✓", cur, total);
            if (mineSkinDelay > 0)
                Thread.sleep(mineSkinDelay);
            return url;
        } finally {
            temp.toFile().delete();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
