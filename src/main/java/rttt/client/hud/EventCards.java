package rttt.client.hud;

import rttt.client.game.Feed;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Cards that tell viewers who did what and what it changed. Each one answers the same three
 * questions left to right: a picture of the thing, "who · what", and the metres it was worth.
 * Painted with AWT so they can be checked without starting Minecraft.
 */
public final class EventCards {
    private EventCards() {}

    private static final int GREEN = 0xFF5CFF8A, RED = 0xFFFF6B6B;
    private static final BufferedImage SCRATCH = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private static Font font(float size) {
        return Sprites.inter().deriveFont(Math.max(6f, size));
    }

    private static FontMetrics metrics(Font f) {
        Graphics2D g = SCRATCH.createGraphics();
        FontMetrics m = g.getFontMetrics(f);
        g.dispose();
        return m;
    }

    private static int width(Font f, String s) {
        return metrics(f).stringWidth(s);
    }

    private static String ellipsize(Font f, String s, int maxW) {
        if (maxW <= 0) return "";
        if (width(f, s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(f, t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    public static String amount(long meters) {
        return (meters > 0 ? "+" : "−") + BottomCards.group(meters) + " м";
    }

    /** True once every picture the card needs has been loaded; the card is repainted when this flips. */
    public static boolean iconReady(Feed.Item item) {
        return item.gift == null || GiftImages.get(item.gift) != null;
    }

    /** Draws the picture for an event into a square, returns false when there is nothing to draw. */
    private static boolean icon(Graphics2D g, Feed.Item item, float x, float y, int size) {
        if (item.gift != null) {
            BufferedImage img = GiftImages.get(item.gift);
            if (img != null) {
                g.drawImage(Sprites.icon(img, size), Math.round(x), Math.round(y), null);
                return true;
            }
        }
        if (item.mob != null) {
            BufferedImage egg = Icons.mob(item.mob);
            if (egg != null) {
                g.drawImage(Icons.pixel(egg, size), Math.round(x), Math.round(y), null);
                return true;
            }
        }
        switch (item.kind) {
            case LIKE -> Icons.heart(g, x, y, size, 0xFFFF5C8A);
            case SOCIAL -> Icons.star(g, x, y, size, 0xFFFFD86A);
            case GIFT, OTHER, MOB_HELP, MOB_HURT -> {
                if (item.amount == 0) return false;
                Icons.arrow(g, x + size * 0.08f, y + size * 0.08f, size * 0.84f, item.amount > 0, 0xFFFFFFFF);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int[] background(Feed.Item item) {
        if (item.amount > 0) return new int[]{0xF022A552, 0xF0157A38};
        if (item.amount < 0) return new int[]{0xF0D03A31, 0xF0992620};
        return switch (item.kind) {
            case SOCIAL -> new int[]{0xF03D6FD9, 0xF02A4FA6};
            case HORDE -> new int[]{0xF0D9822B, 0xF0A85E14};
            default -> new int[]{0xF03A4356, 0xF02A3242};
        };
    }

    /**
     * The newest event, large and coloured by direction: green pushed the car forward, red sent it back.
     * Reads "user · what" with the metres in their own capsule on the right.
     */
    public static BufferedImage toast(Feed.Item item, float u, int maxW) {
        float scale = 1f;
        while (true) {
            float size = 2.35f * u * scale;
            int h = Math.round(4.6f * u * scale);
            int pad = Math.round(1.2f * u * scale), gap = Math.round(0.8f * u * scale);
            int iconSize = Math.round(3.5f * u * scale);
            Font fUser = font(size), fWhat = font(size * 0.94f), fAmount = font(size * 1.06f);
            boolean hasAmount = item.amount != 0;
            String user = item.user.length() > 20 ? item.user.substring(0, 19) + "…" : item.user;
            String what = item.title;
            String sep = !user.isEmpty() && !what.isEmpty() ? " · " : "";
            String amount = hasAmount ? amount(item.amount) : "";
            int amountW = hasAmount ? width(fAmount, amount) + pad * 2 : 0;
            boolean hasIcon = item.gift != null || item.mob != null || item.kind == Feed.Kind.LIKE || item.kind == Feed.Kind.SOCIAL || hasAmount;
            int textW = width(fUser, user) + width(fWhat, sep + what);
            int w = pad + (hasIcon ? iconSize + gap : 0) + textW + (hasAmount ? gap + amountW : 0) + pad;
            if (w > maxW && scale > 0.62f) {
                scale = Math.max(0.62f, scale * Math.min(0.94f, maxW / (float) w));
                continue;
            }
            if (w > maxW) {
                int over = w - maxW;
                what = ellipsize(fWhat, what, Math.max(0, width(fWhat, what) - over));
                w = maxW;
            }
            BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = gfx(img);
            int[] bg = background(item);
            Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h);
            g.setPaint(new GradientPaint(0, 0, new Color(bg[0], true), 0, h, new Color(bg[1], true)));
            g.fill(body);
            g.setColor(new Color(0x55FFFFFF, true));
            g.draw(body);
            int x = pad;
            if (hasIcon && icon(g, item, x, (h - iconSize) / 2f, iconSize)) x += iconSize + gap;
            FontMetrics mu = metrics(fUser);
            float base = (h + mu.getAscent() - mu.getDescent()) / 2f;
            g.setFont(fUser);
            g.setColor(Color.WHITE);
            g.drawString(user, x, base);
            x += width(fUser, user);
            g.setFont(fWhat);
            g.setColor(new Color(0xF2FFFFFF, true));
            g.drawString(sep + what, x, base);
            if (hasAmount) {
                int ah = Math.round(h * 0.74f), ax = w - pad / 2 - amountW, ay = (h - ah) / 2;
                g.setColor(new Color(0x59000000, true));
                g.fill(new RoundRectangle2D.Float(ax, ay, amountW, ah, ah, ah));
                g.setFont(fAmount);
                g.setColor(Color.WHITE);
                FontMetrics ma = metrics(fAmount);
                g.drawString(amount, ax + pad, (h + ma.getAscent() - ma.getDescent()) / 2f);
            }
            g.dispose();
            return img;
        }
    }

    /** One line of the history under the car: picture, who, what, and the metres on the right. */
    public static BufferedImage row(Feed.Item item, float u, int w) {
        int h = Math.round(3.3f * u);
        int pad = Math.round(0.9f * u), gap = Math.round(0.6f * u);
        int iconSize = Math.round(2.5f * u);
        Font fUser = font(1.75f * u), fWhat = font(1.65f * u), fAmount = font(1.9f * u);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 1.4f * u, 1.4f * u);
        g.setColor(new Color(0xC40F141E, true));
        g.fill(body);
        int accent = item.amount > 0 ? GREEN : item.amount < 0 ? RED : background(item)[0] | 0xFF000000;
        g.setClip(body);
        g.setColor(new Color(accent, true));
        g.fillRect(0, 0, Math.max(3, Math.round(0.5f * u)), h);
        g.setClip(null);
        int x = pad + Math.round(0.5f * u);
        if (icon(g, item, x, (h - iconSize) / 2f, iconSize)) x += iconSize + gap;
        String amount = item.amount != 0 ? amount(item.amount) : "";
        int amountW = amount.isEmpty() ? 0 : width(fAmount, amount) + gap;
        int room = w - x - pad - amountW;
        String user = ellipsize(fUser, item.user, Math.round(room * 0.48f));
        FontMetrics mu = metrics(fUser);
        float base = (h + mu.getAscent() - mu.getDescent()) / 2f;
        g.setFont(fUser);
        g.setColor(Color.WHITE);
        g.drawString(user, x, base);
        int used = width(fUser, user);
        String what = ellipsize(fWhat, (user.isEmpty() ? "" : "  ") + item.title, room - used);
        g.setFont(fWhat);
        g.setColor(new Color(0xFFBFC8D8, true));
        g.drawString(what, x + used, base);
        if (!amount.isEmpty()) {
            g.setFont(fAmount);
            g.setColor(new Color(item.amount > 0 ? GREEN : RED, true));
            g.drawString(amount, w - pad - width(fAmount, amount), base);
        }
        g.dispose();
        return img;
    }

    /** A large card for an expensive gift: big picture, sender, gift name and the result in huge digits. */
    public static BufferedImage banner(Feed.Item item, float u, int w) {
        int h = Math.round(13f * u);
        int pad = Math.round(1.5f * u);
        boolean good = item.amount >= 0;
        boolean epic = Math.abs(item.amount) >= 50_000;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        float r = 2.2f * u;
        Shape body = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, r * 2, r * 2);
        g.setPaint(new GradientPaint(0, 0, new Color(good ? 0xF2143B2A : 0xF2441518, true), 0, h, new Color(good ? 0xF20B2119 : 0xF2260B0D, true)));
        g.fill(body);
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, 0.28f * u)));
        g.setColor(new Color(epic ? 0xFFFFD84A : good ? 0xFF45D97A : 0xFFFF6B6B, true));
        g.draw(body);
        int iconSize = Math.round(9.4f * u);
        int ix = pad, iy = (h - iconSize) / 2;
        g.setColor(new Color(0x26FFFFFF, true));
        g.fill(new java.awt.geom.Ellipse2D.Float(ix - 0.2f * u, iy - 0.2f * u, iconSize + 0.4f * u, iconSize + 0.4f * u));
        if (!icon(g, item, ix + iconSize * 0.08f, iy + iconSize * 0.08f, Math.round(iconSize * 0.84f))) {
            Icons.star(g, ix + iconSize * 0.2f, iy + iconSize * 0.2f, iconSize * 0.6f, 0xFFFFD86A);
        }
        int tx = ix + iconSize + pad, room = w - tx - pad;
        Font fUser = font(2.7f * u), fWhat = font(1.85f * u), fNote = font(1.55f * u);
        g.setFont(fUser);
        g.setColor(Color.WHITE);
        float y = pad * 0.75f + metrics(fUser).getAscent();
        g.drawString(ellipsize(fUser, item.user, room), tx, y);
        g.setFont(fWhat);
        g.setColor(new Color(0xFFD5DEEE, true));
        y += 2.5f * u;
        g.drawString(ellipsize(fWhat, "подарок · gift: " + item.title, room), tx, y);
        String amount = amount(item.amount);
        float size = 4.4f * u;
        Font fAmount = font(size);
        String noteText = item.note.isEmpty() ? "" : ellipsize(fNote, item.note, Math.round(room * 0.5f));
        int noteW = noteText.isEmpty() ? 0 : width(fNote, noteText) + Math.round(1.4f * u);
        int amountRoom = room - (noteW > 0 ? noteW + Math.round(0.8f * u) : 0);
        if (width(fAmount, amount) > amountRoom) fAmount = font(size * amountRoom / width(fAmount, amount));
        g.setFont(fAmount);
        g.setColor(new Color(epic && good ? 0xFFFFD84A : good ? GREEN : RED, true));
        y += 0.9f * u + metrics(fAmount).getAscent() * 0.82f;
        g.drawString(amount, tx, y);
        if (!item.note.isEmpty()) {
            g.setFont(fNote);
            g.setColor(new Color(0xFFFFFFFF, true));
            String note = noteText;
            int nw = noteW, nh = Math.round(2.3f * u);
            float ny = h - pad * 0.7f - nh;
            g.setColor(new Color(good ? 0x4045D97A : 0x40FF6B6B, true));
            g.fill(new RoundRectangle2D.Float(w - pad - nw, ny, nw, nh, nh, nh));
            g.setColor(Color.WHITE);
            FontMetrics mn = metrics(fNote);
            g.drawString(note, w - pad - nw + 0.7f * u, ny + (nh + mn.getAscent() - mn.getDescent()) / 2f);
        }
        g.dispose();
        return img;
    }

    /** Progress towards the next like reward, so viewers can see that tapping the screen does something. */
    public static BufferedImage likes(float u, long have, long need, long reward) {
        int h = Math.round(3.6f * u);
        int pad = Math.round(1.0f * u);
        Font fMain = font(1.95f * u), fSmall = font(1.35f * u);
        String digits = Long.toString(Math.max(1, need));
        String main = have + " / " + need;
        String widest = digits.replaceAll("\\d", "8") + " / " + need;
        String bonus = (reward >= 0 ? "+" : "−") + BottomCards.shortMeters(Math.abs(reward)) + " м";
        int heart = Math.round(2.2f * u);
        int w = pad + heart + Math.round(0.6f * u) + width(fMain, widest) + Math.round(0.9f * u) + width(fSmall, bonus) + pad;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        Shape body = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h);
        g.setColor(new Color(0xC0141A26, true));
        g.fill(body);
        float p = need <= 0 ? 0 : Math.max(0f, Math.min(1f, have / (float) need));
        g.setClip(body);
        g.setPaint(new GradientPaint(0, 0, new Color(0xB8FF4F84, true), w, 0, new Color(0xB8FF8A5C, true)));
        g.fillRect(0, 0, Math.round(w * p), h);
        g.setClip(null);
        g.setColor(new Color(0x59FF8FB1, true));
        g.draw(body);
        Icons.heart(g, pad, (h - heart) / 2f, heart, 0xFFFFFFFF);
        FontMetrics mm = metrics(fMain);
        float base = (h + mm.getAscent() - mm.getDescent()) / 2f;
        g.setFont(fMain);
        g.setColor(Color.WHITE);
        int tx = pad + heart + Math.round(0.6f * u);
        g.drawString(main, tx + width(fMain, widest) - width(fMain, main), base);
        g.setFont(fSmall);
        g.setColor(new Color(0xFFFFE3EC, true));
        g.drawString(bonus, w - pad - width(fSmall, bonus), base - 0.1f * u);
        g.dispose();
        return img;
    }
}
