package id.naturalsmp.naturalvelocity.headmotd;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

public class ImageSlicer {
    public record Segment(BufferedImage image, String hash) {
    }

    public static List<List<Segment>> slice(File file) throws Exception {
        BufferedImage img = ImageIO.read(file);
        List<List<Segment>> rows = new ArrayList<>();
        for (int y = 0; y < img.getHeight(); y += 8) {
            List<Segment> row = new ArrayList<>();
            for (int x = 0; x < img.getWidth(); x += 8) {
                BufferedImage sub = img.getSubimage(x, y, 8, 8);
                row.add(new Segment(sub, hash(sub)));
            }
            rows.add(row);
        }
        return rows;
    }

    public static Path saveTempSegment(BufferedImage img, String hash, File dir) throws IOException {
        File f = new File(dir, "temp_" + hash + ".png");
        ImageIO.write(img, "png", f);
        return f.toPath();
    }

    private static String hash(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] hash = MessageDigest.getInstance("MD5").digest(baos.toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
