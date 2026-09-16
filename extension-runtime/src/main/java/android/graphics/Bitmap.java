package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;

/**
 * Dareader-owned Bitmap backed by {@link BufferedImage} (headless-safe).
 * Only the members extensions actually call are modeled.
 */
public class Bitmap {
    /** WEBP is deliberately absent: ImageIO cannot encode it, so declaring it would be a lie. */
    public enum CompressFormat { PNG, JPEG }

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

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        BufferedImage scaled = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            filter
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );
        graphics.drawImage(src.image, 0, 0, dstWidth, dstHeight, null);
        graphics.dispose();
        return new Bitmap(scaled);
    }

    public Bitmap copy(Config config, boolean mutable) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return new Bitmap(copy);
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            if (format == CompressFormat.JPEG) {
                // JPEG has no alpha channel: flatten first so the encoder accepts it.
                BufferedImage flattened = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = flattened.createGraphics();
                graphics.drawImage(image, 0, 0, null);
                graphics.dispose();
                return ImageIO.write(flattened, "jpg", stream);
            }
            return ImageIO.write(image, "png", stream);
        } catch (IOException e) {
            return false;
        }
    }
}
