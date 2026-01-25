package id.naturalsmp.naturalvelocity.utils;

import com.velocitypowered.api.util.Favicon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class IconResizer {

    /**
     * Resizes an image file to 64x64 and converts it to a Velocity Favicon.
     */
    public static Favicon createFavicon(File file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file);
        if (originalImage == null) {
            throw new IOException("Failed to read image file: " + file.getName());
        }

        // Standard Minecraft Icon size
        int targetSize = 64;

        BufferedImage resizedImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();

        // High quality scaling
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetSize, targetSize, null);
        g2d.dispose();

        return Favicon.create(resizedImage);
    }
}
