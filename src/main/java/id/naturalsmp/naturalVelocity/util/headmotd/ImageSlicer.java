package id.naturalsmp.naturalvelocity.util.headmotd;

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
    public static class Segment {
        private final BufferedImage image;
        private final String hash;

        public Segment(BufferedImage image, String hash) {
            this.image = image;
            this.hash = hash;
        }

        public BufferedImage image() {
            return image;
        }

        public String hash() {
            return hash;
        }
    }

    public static List<List<Segment>> slice(File file) throws Exception {
        BufferedImage img = ImageIO.read(file);
        List<List<Segment>> rows = new ArrayList<>();
        for (int y = 0; y < img.getHeight(); y += 8) {
            List<Segment> row = new ArrayList<>();
            for (int x = 0; x < img.getWidth(); x += 8) {
                BufferedImage sub = img.getSubimage(x, y, 8, 8);

                // MineSkin requires exactly 64x32 or 64x64 valid skin layouts.
                // The front of the head (the face) in a Minecraft skin starts at X=8, Y=8 and
                // is 8x8 pixels.
                BufferedImage paddedSkin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = paddedSkin.createGraphics();

                // Make background fully transparent
                g2d.setBackground(new java.awt.Color(0, 0, 0, 0));
                g2d.clearRect(0, 0, 64, 64);

                // Draw the 8x8 MOTD chunk onto the front face area of the skin
                g2d.drawImage(sub, 8, 8, null);
                g2d.dispose();

                row.add(new Segment(paddedSkin, hash(paddedSkin)));
            }
            rows.add(row);
        }
        return rows;
    }

    public static Path saveTempSegment(BufferedImage img, String hash, File dir) throws IOException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
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
