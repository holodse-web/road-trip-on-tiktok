package rttt.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import rttt.RTTT;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class Sprites {
    private Sprites() {}

    public static final class Sprite {
        public Identifier id;
        public DynamicTexture tex;
        public int w, h;
        public int padX, padY;
        public float advance;
        /** Only kept when running without a GPU (offline previews). */
        public BufferedImage image;
        long lastUse;
    }

    /** Set by offline tools: sprites keep their pixels instead of being uploaded as textures. */
    public static boolean headless = false;

    /**
     * While set, {@link #get} only reports sizes: nothing is uploaded to the GPU. The overlay uses
     * this to find where a resized block sits by default before painting it at its final size.
     */
    public static boolean measureOnly = false;
    private static final Map<String, Sprite> METRICS = new java.util.HashMap<>();

    private static final Map<String, Sprite> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int counter = 0;
    private static Font inter;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    public static Font inter() {
        if (inter == null) {
            try (InputStream in = Sprites.class.getResourceAsStream("/assets/rttt/font/inter_bold.ttf")) {
                inter = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Throwable t) {
                RTTT.LOG.warn("Inter font not available, using fallback: {}", t.toString());
                inter = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            }
        }
        return inter;
    }

    public static Sprite peek(String key) {
        Sprite s = CACHE.get(key);
        if (s == null && measureOnly) return METRICS.get(key);
        if (s != null) s.lastUse = System.currentTimeMillis();
        return s;
    }

    public static Sprite get(String key, Supplier<BufferedImage> painter) {
        Sprite s = CACHE.get(key);
        if (s == null && measureOnly) {
            Sprite m = METRICS.get(key);
            if (m == null) {
                BufferedImage img = painter.get();
                if (img == null) return null;
                m = new Sprite();
                m.w = Math.max(1, img.getWidth());
                m.h = Math.max(1, img.getHeight());
                if (METRICS.size() > 800) METRICS.clear();
                METRICS.put(key, m);
            }
            return m;
        }
        if (s == null) {
            BufferedImage img = painter.get();
            if (img == null) return null;
            s = upload(img);
            CACHE.put(key, s);
            trim();
        }
        s.lastUse = System.currentTimeMillis();
        return s;
    }

    private static Sprite upload(BufferedImage img) {
        int w = Math.max(1, img.getWidth()), h = Math.max(1, img.getHeight());
        if (headless) {
            Sprite s = new Sprite();
            s.image = img;
            s.w = w;
            s.h = h;
            return s;
        }
        NativeImage ni = new NativeImage(w, h, false);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) ni.setPixel(x, y, row[x]);
        }
        Sprite s = new Sprite();
        s.id = RTTT.id("hud/dyn_" + (counter++));
        final Identifier id = s.id;
        s.tex = new DynamicTexture(() -> "rttt hud " + id, ni);
        Minecraft.getInstance().getTextureManager().register(s.id, s.tex);
        s.w = w;
        s.h = h;
        return s;
    }

    private static void trim() {
        if (CACHE.size() <= 220) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Sprite>> it = CACHE.entrySet().iterator();
        while (it.hasNext() && CACHE.size() > 160) {
            Map.Entry<String, Sprite> e = it.next();
            if (now - e.getValue().lastUse < 1500) continue;
            if (e.getValue().id != null) Minecraft.getInstance().getTextureManager().release(e.getValue().id);
            it.remove();
        }
    }

    public static void clear() {
        for (Sprite s : CACHE.values()) if (s.id != null) Minecraft.getInstance().getTextureManager().release(s.id);
        CACHE.clear();
        METRICS.clear();
    }

    public static void draw(GuiGraphicsExtractor g, Sprite s, int x, int y, int argb) {
        if (s == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, s.id, x, y, 0f, 0f, s.w, s.h, s.w, s.h, argb);
    }

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        return g;
    }

    static TextLayout layout(String text, float size) {
        if (text.isEmpty()) text = " ";
        Font f = inter().deriveFont(size);
        Font fb = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
        AttributedString as = new AttributedString(text);
        as.addAttribute(TextAttribute.FONT, f);
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            if (!f.canDisplay(cp)) as.addAttribute(TextAttribute.FONT, fb, i, i + n);
            i += n;
        }
        return new TextLayout(as.getIterator(), FRC);
    }

    public static float measure(String text, float size) {
        return layout(text, size).getAdvance();
    }

    public static BufferedImage text(String text, float size, int fill, int top, float outline, boolean shadow, Sprite meta) {
        TextLayout tl = layout(text, size);
        float asc = tl.getAscent(), desc = tl.getDescent();
        float adv = tl.getAdvance();
        int pad = (int) Math.ceil(outline + (shadow ? size * 0.12f : 0) + 2);
        int w = (int) Math.ceil(adv + pad * 2), h = (int) Math.ceil(asc + desc + pad * 2);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        Shape shape = tl.getOutline(AffineTransform.getTranslateInstance(pad, pad + asc));
        if (shadow) {
            BufferedImage sh = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = gfx(sh);
            sg.translate(0, Math.max(1, size * 0.05f));
            sg.setColor(new Color(0, 0, 0, 150));
            sg.fill(shape);
            sg.setStroke(new BasicStroke(outline * 2f + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            sg.draw(shape);
            sg.dispose();
            blur(sh, Math.max(1, Math.round(size * 0.05f)));
            g.drawImage(sh, 0, 0, null);
        }
        if (outline > 0) {
            g.setStroke(new BasicStroke(outline * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(14, 16, 22, 235));
            g.draw(shape);
        }
        if (top != 0) {
            g.setPaint(new GradientPaint(0, pad, new Color(top, true), 0, pad + asc, new Color(fill, true)));
        } else {
            g.setColor(new Color(fill, true));
        }
        g.fill(shape);
        g.dispose();
        if (meta != null) {
            meta.padX = pad;
            meta.padY = pad;
            meta.advance = adv;
        }
        return img;
    }

    public static BufferedImage panel(int w, int h, float radius, int topArgb, int bottomArgb, int borderArgb) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius * 2, radius * 2);
        g.setPaint(new GradientPaint(0, 0, new Color(topArgb, true), 0, h, new Color(bottomArgb, true)));
        g.fill(r);
        if ((borderArgb >>> 24) != 0) {
            g.setStroke(new BasicStroke(Math.max(1f, h / 60f)));
            g.setColor(new Color(borderArgb, true));
            g.draw(r);
        }
        g.dispose();
        return img;
    }

    public static BufferedImage icon(BufferedImage src, int size) {
        BufferedImage cur = src;
        int w = src.getWidth(), h = src.getHeight();
        float scale = Math.min(size / (float) w, size / (float) h);
        int tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = gfx(half);
            g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            cur = half;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(out);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(cur, (size - tw) / 2, (size - th) / 2, tw, th, null);
        g.dispose();
        return out;
    }

    static void blur(BufferedImage img, int r) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 2; pass++) {
            boxH(px, tmp, w, h, r);
            boxV(tmp, px, w, h, r);
        }
        img.setRGB(0, 0, w, h, px, 0, w);
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int a = 0;
            for (int x = -r; x <= r; x++) a += in[y * w + Math.max(0, Math.min(w - 1, x))] >>> 24;
            for (int x = 0; x < w; x++) {
                out[y * w + x] = ((a / div) << 24);
                int xa = Math.min(w - 1, x + r + 1), xs = Math.max(0, x - r);
                a += (in[y * w + xa] >>> 24) - (in[y * w + xs] >>> 24);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0;
            for (int y = -r; y <= r; y++) a += in[Math.max(0, Math.min(h - 1, y)) * w + x] >>> 24;
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((a / div) << 24);
                int ya = Math.min(h - 1, y + r + 1), ys = Math.max(0, y - r);
                a += (in[ya * w + x] >>> 24) - (in[ys * w + x] >>> 24);
            }
        }
    }

    public static BufferedImage trophy(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        float s = size / 24f;
        g.scale(s, s);
        g.setPaint(new GradientPaint(0, 2, new Color(0xFFF3A6), 0, 16, new Color(0xF2A900)));
        java.awt.geom.Path2D cup = new java.awt.geom.Path2D.Float();
        cup.moveTo(6, 3);
        cup.lineTo(18, 3);
        cup.curveTo(18, 11, 15.5, 14, 12, 14.5f);
        cup.curveTo(8.5f, 14, 6, 11, 6, 3);
        cup.closePath();
        g.fill(cup);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xF2A900));
        g.draw(new java.awt.geom.Arc2D.Float(1.5f, 4, 6, 6.5f, 90, 180, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Float(16.5f, 4, 6, 6.5f, 270, 180, java.awt.geom.Arc2D.OPEN));
        g.fillRect(11, 14, 2, 4);
        g.fill(new RoundRectangle2D.Float(7.5f, 18, 9, 3.2f, 1.5f, 1.5f));
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(new RoundRectangle2D.Float(8.5f, 4.5f, 1.8f, 6, 1.5f, 1.5f));
        g.dispose();
        return img;
    }
}
