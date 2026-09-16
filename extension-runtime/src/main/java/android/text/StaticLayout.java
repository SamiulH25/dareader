package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Dareader-owned StaticLayout with real word-wrap measurement via AWT font
 * metrics (headless-safe). Only the constructor shape extensions call plus
 * draw/getHeight are modeled.
 */
public class StaticLayout extends Layout {
    private final List<String> lines;
    private final int lineHeight;
    private final Paint paint;

    public StaticLayout(
        CharSequence source,
        TextPaint paint,
        int width,
        Alignment align,
        float spacingMult,
        float spacingAdd,
        boolean includePad
    ) {
        this.paint = paint;
        FontMetrics metrics = measure(paint);
        this.lineHeight = Math.max(1, Math.round(metrics.getHeight() * spacingMult + spacingAdd));
        this.lines = wrap(source.toString(), metrics, Math.max(1, width));
    }

    private static FontMetrics measure(Paint paint) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            g.setFont(paint.awtFont());
            return g.getFontMetrics();
        } finally {
            g.dispose();
        }
    }

    private static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (metrics.stringWidth(candidate) <= width || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public void draw(Canvas canvas) {
        Graphics2D g = canvas.graphics();
        g.setFont(paint.awtFont());
        g.setColor(paint.awtColor());
        int y = lineHeight;
        for (String line : lines) {
            g.drawString(line, 0, y - g.getFontMetrics().getDescent());
            y += lineHeight;
        }
    }

    @Override
    public int getHeight() {
        return lines.size() * lineHeight;
    }
}
