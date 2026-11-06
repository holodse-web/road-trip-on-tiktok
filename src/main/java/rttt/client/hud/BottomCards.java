package rttt.client.hud;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Speech bubbles and the top-3 table, painted with AWT so they can be checked without Minecraft. */
public final class BottomCards {
    private BottomCards() {}

    public static final int STEVE = 0xFF2FA4C8, ALEX = 0xFFF08A24;

    private static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static Font font(float size) {
        return Sprites.inter().deriveFont(Math.max(6f, size));
    }

    private static int width(Font f, String s) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(tmp);
        g.setFont(f);
        int w = g.getFontMetrics().stringWidth(s);
        g.dispose();
        return w;
    }

    private static List<String> wrap(Font f, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (width(f, text) <= maxW) {
            out.add(text);
            return out;
        }
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String next = line.length() == 0 ? w : line + " " + w;
            if (width(f, next) <= maxW || line.length() == 0) {
                line.setLength(0);
                line.append(next);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String ellipsize(Font f, String s, int maxW) {
        if (width(f, s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(f, t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** A rounded bubble: coloured speaker name, the Russian line (wrapped to 2 lines at most) and a small English line. */
    public static BufferedImage bubble(String name, int accent, String ru, String en, float u, int maxW) {
        int pad = Math.max(4, Math.round(1.2f * u));
        int inner = Math.max(40, maxW - pad * 2);
        float ruSize = 1.9f * u;
        Font fName = font(ruSize), fRu = font(ruSize), fEn = font(1.35f * u);
        String head = name + ": ";
        List<String> lines;
        while (true) {
            lines = wrap(fRu, head + ru, inner);
            if (lines.size() <= 2 || ruSize <= 1.3f * u) break;
            ruSize -= 0.1f * u;
            fName = font(ruSize);
            fRu = font(ruSize);
        }
        if (lines.size() > 2) {
            String second = String.join(" ", lines.subList(1, lines.size()));
            lines = new ArrayList<>(lines.subList(0, 1));
            lines.add(ellipsize(fRu, second, inner));
        }
        String enLine = en == null || en.isEmpty() ? null : ellipsize(fEn, en, inner);
        FontMetrics mRu = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(fRu);
        int lineH = Math.round(ruSize * 1.22f);
        int enH = enLine == null ? 0 : Math.round(1.35f * u * 1.3f);
        int textW = 0;
        for (String l : lines) textW = Math.max(textW, width(fRu, l));
        if (enLine != null) textW = Math.max(textW, width(fEn, enLine));
        int stripe = Math.max(3, Math.round(0.5f * u));
        int w = Math.min(maxW, textW + pad * 2 + stripe);
        int h = pad * 2 + lineH * lines.size() + enH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        float r = Math.min(h / 2f, 1.6f * u);
        g.setColor(new Color(0xF4FFFFFF, true));
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
        g.setColor(new Color(accent, true));
        g.setClip(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
        g.fillRect(0, 0, stripe, h);
        g.setClip(null);
        int x = stripe + pad;
        int y = pad + mRu.getAscent() - Math.round(ruSize * 0.04f);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (i == 0 && l.startsWith(head)) {
                g.setFont(fName);
                g.setColor(darker(accent));
                g.drawString(head, x, y);
                int hx = width(fName, head);
                g.setFont(fRu);
                g.setColor(new Color(0xFF171B26, true));
                g.drawString(l.substring(head.length()), x + hx, y);
            } else {
                g.setFont(fRu);
                g.setColor(new Color(0xFF171B26, true));
                g.drawString(l, x, y);
            }
            y += lineH;
        }
        if (enLine != null) {
            g.setFont(fEn);
            g.setColor(new Color(0xFF5A6478, true));
            g.drawString(enLine, x, y - lineH + lineH + Math.round(1.45f * u * 0.05f));
        }
        g.dispose();
        return img;
    }

    private static Color darker(int argb) {
        int r = (argb >> 16) & 255, gr = (argb >> 8) & 255, b = argb & 255;
        return new Color((int) (r * 0.8), (int) (gr * 0.8), (int) (b * 0.8));
    }

    /** "Top helpers" table: up to three rows of nick and meters. */
    public static BufferedImage top3(List<String> names, List<Long> meters, float u, int maxW) {
        int pad = Math.max(4, Math.round(1.1f * u));
        Font fHead = font(1.45f * u), fName = font(1.9f * u), fNum = font(1.9f * u);
        String header = "ПОМОГЛИ БОЛЬШЕ ВСЕХ · TOP HELPERS";
        int rowH = Math.round(2.7f * u);
        int medal = Math.round(2.0f * u);
        int numW = 0;
        List<String> nums = new ArrayList<>();
        for (Long m : meters) {
            String s = "+" + group(m) + " м";
            nums.add(s);
            numW = Math.max(numW, width(fNum, s));
        }
        int nameMax = 0;
        for (String n : names) nameMax = Math.max(nameMax, width(fName, n));
        int headW = width(fHead, header);
        int w = Math.min(maxW, Math.max(headW, medal + Math.round(0.8f * u) + nameMax + Math.round(1.6f * u) + numW) + pad * 2);
        int nameRoom = w - pad * 2 - medal - Math.round(0.8f * u) - Math.round(1.6f * u) - numW;
        if (headW > w - pad * 2) {
            header = "TOP HELPERS";
            headW = width(fHead, header);
        }
        int headH = Math.round(1.45f * u * 1.5f);
        int h = pad * 2 + headH + rowH * names.size();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        float r = 1.4f * u;
        g.setColor(new Color(0xC8101622, true));
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
        g.setColor(new Color(0x40FFD86A, true));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, r * 2, r * 2));
        g.setFont(fHead);
        g.setColor(new Color(0xFFFFD86A, true));
        FontMetrics mh = g.getFontMetrics();
        g.drawString(header, (w - headW) / 2f, pad + mh.getAscent());
        int[] medals = {0xFFFFC83A, 0xFFC9D2DC, 0xFFD08A4E};
        int y = pad + headH;
        for (int i = 0; i < names.size(); i++) {
            int cy = y + rowH / 2;
            g.setColor(new Color(medals[Math.min(i, 2)], true));
            g.fill(new Ellipse2D.Float(pad, cy - medal / 2f, medal, medal));
            g.setFont(font(1.3f * u));
            g.setColor(new Color(0xFF1A1205, true));
            FontMetrics fm = g.getFontMetrics();
            String num = Integer.toString(i + 1);
            g.drawString(num, pad + (medal - fm.stringWidth(num)) / 2f, cy + fm.getAscent() / 2f - fm.getDescent() / 2f);
            g.setFont(fName);
            FontMetrics fn = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(ellipsize(fName, names.get(i), Math.max(20, nameRoom)), pad + medal + Math.round(0.8f * u), cy + fn.getAscent() / 2f - fn.getDescent() / 2f);
            g.setFont(fNum);
            g.setColor(new Color(0xFF5CFF8A, true));
            String s = nums.get(i);
            g.drawString(s, w - pad - width(fNum, s), cy + fn.getAscent() / 2f - fn.getDescent() / 2f);
            y += rowH;
        }
        g.dispose();
        return img;
    }

    /** One-line variant for narrow screens: medal, nick and meters for each of the three places. */
    public static BufferedImage top3Row(List<String> names, List<Long> meters, float u, int maxW) {
        int pad = Math.max(4, Math.round(0.9f * u));
        int medal = Math.round(1.8f * u);
        float size = 1.65f * u;
        int gap = Math.round(1.2f * u);
        Font fName, fNum;
        int[] nameW = new int[names.size()];
        String[] nums = new String[names.size()];
        String[] shown = new String[names.size()];
        while (true) {
            fName = font(size);
            fNum = font(size);
            int total = pad * 2;
            int n = names.size();
            int[] need = new int[n];
            int fixed = pad * 2 + gap * (n - 1);
            for (int i = 0; i < n; i++) {
                need[i] = width(fName, names.get(i));
                fixed += medal + Math.round(1.0f * u) + width(fNum, "+" + shortMeters(meters.get(i)));
            }
            int[] give = new int[n];
            int budget = Math.max(0, maxW - fixed);
            boolean[] done = new boolean[n];
            for (int pass = 0; pass < n; pass++) {
                int open = 0;
                for (int i = 0; i < n; i++) if (!done[i]) open++;
                if (open == 0) break;
                int share = budget / open;
                boolean changed = false;
                for (int i = 0; i < n; i++) {
                    if (!done[i] && need[i] <= share) {
                        give[i] = need[i];
                        budget -= need[i];
                        done[i] = true;
                        changed = true;
                    }
                }
                if (!changed) {
                    for (int i = 0; i < n; i++) if (!done[i]) give[i] = share;
                    break;
                }
            }
            for (int i = 0; i < names.size(); i++) {
                nums[i] = "+" + shortMeters(meters.get(i));
                int nameRoom = Math.max(Math.round(3f * u), give[i]);
                shown[i] = ellipsize(fName, names.get(i), nameRoom);
                nameW[i] = width(fName, shown[i]);
                total += medal + Math.round(0.5f * u) + nameW[i] + Math.round(0.5f * u) + width(fNum, nums[i]);
            }
            total += gap * (names.size() - 1);
            if (total <= maxW || size <= 1.2f * u) {
                int w = Math.min(maxW, total);
                int h = Math.round(size * 1.9f);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = gfx(img);
                g.setColor(new Color(0xC8101622, true));
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                g.setColor(new Color(0x40FFD86A, true));
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h - 1, h - 1));
                int[] medals = {0xFFFFC83A, 0xFFC9D2DC, 0xFFD08A4E};
                int x = pad;
                int cy = h / 2;
                for (int i = 0; i < names.size(); i++) {
                    g.setColor(new Color(medals[Math.min(i, 2)], true));
                    g.fill(new Ellipse2D.Float(x, cy - medal / 2f, medal, medal));
                    g.setFont(font(1.2f * u));
                    FontMetrics fm = g.getFontMetrics();
                    g.setColor(new Color(0xFF1A1205, true));
                    String num = Integer.toString(i + 1);
                    g.drawString(num, x + (medal - fm.stringWidth(num)) / 2f, cy + fm.getAscent() / 2f - fm.getDescent() / 2f);
                    x += medal + Math.round(0.5f * u);
                    g.setFont(fName);
                    FontMetrics fn = g.getFontMetrics();
                    float base = cy + fn.getAscent() / 2f - fn.getDescent() / 2f;
                    g.setColor(Color.WHITE);
                    g.drawString(shown[i], x, base);
                    x += nameW[i] + Math.round(0.5f * u);
                    g.setFont(fNum);
                    g.setColor(new Color(0xFF5CFF8A, true));
                    g.drawString(nums[i], x, base);
                    x += width(fNum, nums[i]) + gap;
                }
                g.dispose();
                return img;
            }
            size -= 0.08f * u;
        }
    }

    static String shortMeters(long m) {
        if (m >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", m / 1e6).replace(".0M", "M");
        if (m >= 10_000) return (m / 1000) + "k";
        return group(m);
    }

    static String group(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
