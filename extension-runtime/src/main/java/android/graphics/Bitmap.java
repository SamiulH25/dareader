package android.graphics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;

/**
 * Dareader-owned Bitmap backed by {@link BufferedImage} (headless-safe).
 * Only the members extensions actually call are modeled.
 */
public class Bitmap {
    public enum CompressFormat { PNG }

    public enum Config { ARGB_8888 }

    private final BufferedImage image;

    Bitmap(BufferedImage image) {
        this.image = image;
    }

    BufferedImage image() {
        return image;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            return ImageIO.write(image, "png", stream);
        } catch (IOException e) {
            return false;
        }
    }
}
