package rttt.client.hud;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small pictures for event cards: vanilla spawn eggs for mobs and a few drawn glyphs. */
public final class Icons {
    private Icons() {}

    private static final Map<String, BufferedImage> EGGS = new ConcurrentHashMap<>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** The spawn-egg texture of a mob, or null when the game has none for that id. */
    public static BufferedImage mob(String id) {
        if (id == null || !id.matches("[a-z0-9_]+")) return null;
        BufferedImage img = EGGS.computeIfAbsent(id, k -> {
            try (InputStream in = Icons.class.getResourceAsStream("/assets/minecraft/textures/item/" + k + "_spawn_egg.png")) {
                BufferedImage b = in == null ? null : ImageIO.read(in);
                return b == null ? MISSING : b;
            } catch (Throwable t) {
                return MISSING;
            }
        });
        return img == MISSING ? null : img;
    }

    /** Pixel art keeps its hard edges when it is scaled up. */
    public static BufferedImage pixel(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int side = Math.max(src.getWidth(), 1);
        g.drawImage(src, 0, 0, size, size, 0, 0, side, Math.min(src.getHeight(), side), null);
        g.dispose();
        return out;
    }

    public static void heart(Graphics2D g, float x, float y, float size, int argb) {
        float s = size;
        Path2D p = new Path2D.Float();
        p.moveTo(x + s * 0.5f, y + s * 0.92f);
        p.curveTo(x - s * 0.15f, y + s * 0.5f, x + s * 0.05f, y + s * 0.02f, x + s * 0.5f, y + s * 0.3f);
        p.curveTo(x + s * 0.95f, y + s * 0.02f, x + s * 1.15f, y + s * 0.5f, x + s * 0.5f, y + s * 0.92f);
        p.closePath();
        g.setColor(new Color(argb, true));
        g.fill(p);
    }

    public static void star(Graphics2D g, float x, float y, float size, int argb) {
        Path2D p = new Path2D.Float();
        float cx = x + size / 2f, cy = y + size / 2f;
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0 ? 0.5 : 0.21) * size;
            float px = (float) (cx + Math.cos(a) * r), py = (float) (cy + Math.sin(a) * r);
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.closePath();
        g.setColor(new Color(argb, true));
        g.fill(p);
    }

    /** Arrow pointing forward (right) or back (left). */
    public static void arrow(Graphics2D g, float x, float y, float size, boolean forward, int argb) {
        Path2D p = new Path2D.Float();
        float a = forward ? x : x + size, b = forward ? x + size : x, m = y + size / 2f;
        float mid = (a + b) / 2f;
        p.moveTo(a, y + size * 0.32f);
        p.lineTo(mid, y + size * 0.32f);
        p.lineTo(mid, y + size * 0.08f);
        p.lineTo(b, m);
        p.lineTo(mid, y + size * 0.92f);
        p.lineTo(mid, y + size * 0.68f);
        p.lineTo(a, y + size * 0.68f);
        p.closePath();
        g.setColor(new Color(argb, true));
        g.fill(p);
    }

    /** A die showing five. */
    public static void dice(Graphics2D g, float x, float y, float size, int argb) {
        float s = size * 0.86f, o = (size - s) / 2f;
        g.setColor(new Color(argb, true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(x + o, y + o, s, s, s * 0.28f, s * 0.28f));
        g.setColor(new Color(0xFF2A1F08, true));
        float d = s * 0.17f;
        float[][] pips = {{0.27f, 0.27f}, {0.73f, 0.27f}, {0.5f, 0.5f}, {0.27f, 0.73f}, {0.73f, 0.73f}};
        for (float[] p : pips) g.fill(new Ellipse2D.Float(x + o + s * p[0] - d / 2, y + o + s * p[1] - d / 2, d, d));
    }

    public static void dot(Graphics2D g, float x, float y, float size, int argb) {
        g.setColor(new Color(argb, true));
        g.fill(new Ellipse2D.Float(x + size * 0.2f, y + size * 0.2f, size * 0.6f, size * 0.6f));
    }
}
