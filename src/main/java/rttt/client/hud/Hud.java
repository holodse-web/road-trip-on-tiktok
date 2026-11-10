package rttt.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import rttt.RTTT;
import rttt.client.game.Config;
import rttt.client.game.Feed;
import rttt.client.game.Game;
import rttt.client.game.Host;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stream overlay. Every block on screen is an "element": it is laid out at its default place,
 * then moved, scaled, faded or hidden according to {@link Config#hudLayout}, which the control
 * panel edits by dragging. Drawing goes through {@link Surface} so a frame can also be painted offline.
 */
public final class Hud {
    private static final char NNBSP = ' ';
    private static final char MINUS = '−';

    /** The few drawing operations the overlay needs. */
    public interface Surface {
        void push();

        void pop();

        void translate(float x, float y);

        void scale(float s);

        void fill(int x1, int y1, int x2, int y2, int argb);

        void blit(Sprites.Sprite sprite, int x, int y, int argb);
    }

    private static final class GuiSurface implements Surface {
        GuiGraphicsExtractor g;

        @Override
        public void push() {
            g.pose().pushMatrix();
        }

        @Override
        public void pop() {
            g.pose().popMatrix();
        }

        @Override
        public void translate(float x, float y) {
            g.pose().translate(x, y);
        }

        @Override
        public void scale(float s) {
            g.pose().scale(s, s);
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            g.fill(x1, y1, x2, y2, argb);
        }

        @Override
        public void blit(Sprites.Sprite sprite, int x, int y, int argb) {
            Sprites.draw(g, sprite, x, y, argb);
        }
    }

    private static final GuiSurface GUI = new GuiSurface();

    private static double shown = Double.NaN;
    private static long lastNanos = 0;
    private static long seenChangeTime = 0;
    private static long changeShownAt = 0;
    private static int pulseColor = 0xFFFFFF;
    private static long lastError = 0;
    private static boolean infoCardVisible;
    private static long infoCardStartedAt;

    /** While the panel's layout editor is open the game shows every element with sample content. */
    private static volatile long editUntil = 0;
    private static volatile String editSelected = "";

    private Hud() {}

    public static void editing(String selected) {
        editUntil = System.currentTimeMillis() + 2500;
        editSelected = selected == null ? "" : selected;
    }

    public static String format(long v) {
        boolean neg = v < 0;
        String digits = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            b.append(digits.charAt(i));
            int left = n - 1 - i;
            if (left > 0 && left % 3 == 0) b.append(NNBSP);
        }
        return (neg ? String.valueOf(MINUS) : "") + b;
    }

    public static void reset() {
        shown = Double.NaN;
        upperHold = 0;
        lastNanos = 0;
        infoCardVisible = false;
        infoCardStartedAt = 0;
        Sprites.clear();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        try {
            Game game = Game.I;
            if (game == null || !game.active) return;
            Minecraft mc = Minecraft.getInstance();
            GUI.g = g;
            frame(GUI, game, mc.getWindow().getWidth(), mc.getWindow().getHeight(), Math.max(1, mc.getWindow().getGuiScale()),
                    delta.getGameTimeDeltaPartialTick(false));
        } catch (Throwable t) {
            if (System.currentTimeMillis() - lastError > 10000) {
                lastError = System.currentTimeMillis();
                RTTT.LOG.error("RTTT HUD failed", t);
            }
        }
    }

    private static Sprites.Sprite text(String text, int size, int fill, int top, float outline, boolean shadow) {
        String key = "t|" + text + "|" + size + "|" + fill + "|" + top + "|" + outline + "|" + shadow;
        Sprites.Sprite hit = Sprites.peek(key);
        if (hit != null) return hit;
        Sprites.Sprite meta = new Sprites.Sprite();
        Sprites.Sprite s = Sprites.get(key, () -> Sprites.text(text, size, fill, top, outline, shadow, meta));
        if (s != null && s.advance == 0 && meta.advance != 0) {
            s.padX = meta.padX;
            s.padY = meta.padY;
            s.advance = meta.advance;
        }
        return s;
    }

    private static Sprites.Sprite panel(int w, int h, int r, int top, int bottom, int border) {
        return Sprites.get("p|" + w + "|" + h + "|" + r + "|" + top + "|" + bottom + "|" + border,
                () -> Sprites.panel(w, h, r, top, bottom, border));
    }

    private static int alpha(int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF;
    }

    private static int tint(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int fade(int argb, float k) {
        if (k >= 0.999f) return argb;
        return (Math.round((argb >>> 24) * Math.max(0f, k)) << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int centered(Surface g, Sprites.Sprite s, int cx, int y, int argb) {
        if (s == null) return 0;
        draw(g, s, Math.round(cx - s.advance / 2f) - s.padX, y - s.padY, argb);
        return s.h - 2 * s.padY;
    }

    // ------------------------------------------------------------------ elements

    private interface Command {
        void draw(Surface s, float opacity);
    }

    /** One movable block: the commands that draw it at its default place, and where it finally landed. */
    private static final class Element {
        final String id;
        final boolean upper;
        final List<Command> commands = new ArrayList<>();
        float left, top, right, bottom;
        /** Final rectangle on screen in pixels. */
        float x, y, w, h;
        boolean placed, drawn;

        Element(String id, boolean upper) {
            this.id = id;
            this.upper = upper;
        }

        void reset() {
            commands.clear();
            left = top = Float.POSITIVE_INFINITY;
            right = bottom = Float.NEGATIVE_INFINITY;
            placed = drawn = false;
        }

        void include(float px, float py, float pw, float ph) {
            left = Math.min(left, px);
            top = Math.min(top, py);
            right = Math.max(right, px + pw);
            bottom = Math.max(bottom, py + ph);
        }

        boolean empty() {
            return !(right > left && bottom > top);
        }
    }

    private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();

    static {
        for (String id : Config.HUD_ELEMENTS) {
            boolean upper = !(id.equals("feed") || id.equals("top") || id.equals("bubble"));
            ELEMENTS.put(id, new Element(id, upper));
        }
    }

    private static Element recording;

    private static Element begin(String id) {
        Element e = ELEMENTS.get(id);
        e.reset();
        recording = e;
        return e;
    }

    private static void draw(Surface g, Sprites.Sprite s, int x, int y, int argb) {
        if (s == null) return;
        Element e = recording;
        if (e == null) {
            g.blit(s, x, y, argb);
            return;
        }
        e.include(x, y, s.w, s.h);
        if (measuring) return;
        e.commands.add((target, opacity) -> target.blit(s, x, y, fade(argb, opacity)));
    }

    private static void fill(Surface g, int x1, int y1, int x2, int y2, int argb) {
        Element e = recording;
        if (e == null) {
            g.fill(x1, y1, x2, y2, argb);
            return;
        }
        e.include(x1, y1, x2 - x1, y2 - y1);
        if (measuring) return;
        e.commands.add((target, opacity) -> target.fill(x1, y1, x2, y2, fade(argb, opacity)));
    }

    /** Paints an element's content at a given size and returns the next free Y of the default layout. */
    private interface Painter {
        int paint(float u);
    }

    /** True during the sizing pass of a resized element: nothing is drawn, only bounds are collected. */
    private static boolean measuring;

    /**
     * Lays out and draws one element. A resized element is never stretched: it is measured at its
     * normal size to find its default place, then painted again at the final size so every sprite is
     * rasterised at exactly the pixels it covers. {@code anchored} elements (bubbles, the leader table)
     * follow something on screen and are simply painted at the final size where they belong.
     */
    private static int element(Surface s, String id, Config cfg, int W, int H, boolean edit, float u, boolean anchored, Painter painter) {
        Config.HudElement layout = cfg.hud(id);
        float want = (float) Math.max(0.4, Math.min(2.5, layout.scale));
        if (anchored || Math.abs(want - 1f) < 0.005f) {
            Element e = begin(id);
            int next = painter.paint(u * want);
            place(s, e, cfg, W, H, edit, Float.NaN, Float.NaN);
            return next;
        }
        Element e = begin(id);
        int next;
        measuring = true;
        Sprites.measureOnly = true;
        try {
            next = painter.paint(u);
        } finally {
            measuring = false;
            Sprites.measureOnly = false;
        }
        if (e.empty()) {
            recording = null;
            return next;
        }
        float margin = Math.max(2, Math.min(W, H) * 0.006f);
        float bw = e.right - e.left, bh = e.bottom - e.top;
        float scale = Math.min(want, Math.min((W - 2 * margin) / bw, (H - 2 * margin) / bh));
        float cx = (e.left + e.right) / 2f, cy = (e.top + e.bottom) / 2f;
        e = begin(id);
        painter.paint(u * scale);
        place(s, e, cfg, W, H, edit, cx, cy);
        return next;
    }

    /** Moves the recorded element to its place (default centre plus the user's offset) and draws it 1:1. */
    private static void place(Surface s, Element e, Config cfg, int W, int H, boolean edit, float centreX, float centreY) {
        recording = null;
        if (e.empty()) return;
        Config.HudElement layout = cfg.hud(e.id);
        float margin = Math.max(2, Math.min(W, H) * 0.006f);
        float w = e.right - e.left, h = e.bottom - e.top;
        float cx = Float.isNaN(centreX) ? (e.left + e.right) / 2f : centreX;
        float cy = Float.isNaN(centreY) ? (e.top + e.bottom) / 2f : centreY;
        float x = cx - w / 2f + (float) layout.x * W / 100f;
        float y = cy - h / 2f + (float) layout.y * H / 100f;
        x = Math.max(margin, Math.min(W - margin - w, x));
        y = Math.max(margin, Math.min(H - margin - h, y));
        // Whole-pixel offsets keep every sprite on the pixel grid, so text stays sharp.
        int dx = Math.round(x - e.left), dy = Math.round(y - e.top);
        e.x = e.left + dx;
        e.y = e.top + dy;
        e.w = w;
        e.h = h;
        e.placed = true;
        e.drawn = layout.visible;
        if (layout.visible) {
            float opacity = (float) Math.max(0.15, Math.min(1, layout.opacity));
            s.push();
            try {
                s.translate(dx, dy);
                for (int i = 0, n = e.commands.size(); i < n; i++) e.commands.get(i).draw(s, opacity);
            } finally {
                s.pop();
            }
        }
        if (edit) outline(s, e, e.id.equals(editSelected), layout.visible);
    }

    private static void outline(Surface s, Element e, boolean selected, boolean visible) {
        int c = selected ? 0xFFFFD84A : visible ? 0x99FFFFFF : 0x66FF6B6B;
        int t = selected ? 3 : 2;
        int x0 = Math.round(e.x) - t, y0 = Math.round(e.y) - t, x1 = Math.round(e.x + e.w) + t, y1 = Math.round(e.y + e.h) + t;
        s.fill(x0, y0, x1, y0 + t, c);
        s.fill(x0, y1 - t, x1, y1, c);
        s.fill(x0, y0 + t, x0 + t, y1 - t, c);
        s.fill(x1 - t, y0 + t, x1, y1 - t, c);
    }

    // The panel reads the rectangles from another thread.
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final float[] SNAPSHOT = new float[Config.HUD_ELEMENTS.length * 5];
    private static int snapshotW, snapshotH;
    private static long snapshotAt;

    private static void publish(int W, int H) {
        synchronized (SNAPSHOT_LOCK) {
            int i = 0;
            for (Element e : ELEMENTS.values()) {
                SNAPSHOT[i++] = e.placed ? e.x / W : -1;
                SNAPSHOT[i++] = e.placed ? e.y / H : -1;
                SNAPSHOT[i++] = e.placed ? e.w / W : 0;
                SNAPSHOT[i++] = e.placed ? e.h / H : 0;
                SNAPSHOT[i++] = e.drawn ? 1 : 0;
            }
            snapshotW = W;
            snapshotH = H;
            snapshotAt = System.currentTimeMillis();
        }
    }

    /** Where every element currently is, as fractions of the frame, for the panel's layout editor. */
    public static JsonObject layoutStatus() {
        JsonObject o = new JsonObject();
        JsonArray items = new JsonArray();
        synchronized (SNAPSHOT_LOCK) {
            o.addProperty("width", snapshotW);
            o.addProperty("height", snapshotH);
            o.addProperty("fresh", System.currentTimeMillis() - snapshotAt < 1500);
            int i = 0;
            for (String id : Config.HUD_ELEMENTS) {
                JsonObject it = new JsonObject();
                it.addProperty("id", id);
                it.addProperty("x", SNAPSHOT[i++]);
                it.addProperty("y", SNAPSHOT[i++]);
                it.addProperty("w", SNAPSHOT[i++]);
                it.addProperty("h", SNAPSHOT[i++]);
                it.addProperty("drawn", SNAPSHOT[i++] > 0.5f);
                items.add(it);
            }
        }
        o.add("items", items);
        return o;
    }

    // ------------------------------------------------------------------ frame

    private static final Feed.Item SAMPLE_TOAST = Feed.sample(1, Feed.Kind.GIFT, 450, "зритель", "Роза ×3", "", "Rose", null);
    private static final Feed.Item SAMPLE_BANNER = Feed.sample(2, Feed.Kind.GIFT, 250_000, "щедрый_зритель", "Галактика", "НИТРО · NITRO", "Galaxy", null);
    private static final Feed.Item[] SAMPLE_ROWS = {
            Feed.sample(3, Feed.Kind.MOB_HURT, -200, "anna", "Крипер", "", null, "creeper"),
            Feed.sample(4, Feed.Kind.LIKE, 500, "max", "Лайки", "", null, null),
            Feed.sample(5, Feed.Kind.MOB_HELP, 200, "kirill", "Корова", "", null, "cow"),
            Feed.sample(6, Feed.Kind.GIFT, -1500, "olga", "Ожерелье дружбы", "", "Friendship Necklace", null),
            Feed.sample(7, Feed.Kind.SOCIAL, 0, "newbie", "подписка — новый цвет машины", "", null, null)};

    private static float upperBottom, upperLeft, upperRight, upperTop;
    private static long lastBottomError = 0;
    private static float top3Alpha = 0;

    /** Paints one frame of the overlay. {@code W}×{@code H} are framebuffer pixels. */
    public static void frame(Surface s, Game game, int W, int H, int guiScale, float pt) {
        if (W <= 0 || H <= 0) return;
        float u = Math.min(H, W * 16f / 9f) / 100f;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        long nowMs = System.currentTimeMillis();
        Config cfg = game.cfg;
        requestUltraIcons(cfg, nowMs);
        if (cfg.showInfoCard && !infoCardVisible) infoCardStartedAt = nowMs;
        infoCardVisible = cfg.showInfoCard;
        boolean edit = nowMs < editUntil;

        s.push();
        s.scale(1f / Math.max(1, guiScale));
        try {
            recording = null;
            float flash = game.fx.flashPrev + (game.fx.flash - game.fx.flashPrev) * pt;
            if (flash > 0.01f) {
                int a = (int) (Math.min(1, flash) * 0.55f * 255);
                s.fill(0, 0, W, H, (a << 24) | (game.fx.flashColor & 0xFFFFFF));
            }
            if (game.entering) {
                centered(s, text("Загрузка дороги…", Math.round(3.4f * u), 0xFFFFFFFF, 0, 0.18f * u, true), W / 2, Math.round(H * 0.42f), -1);
                centered(s, text("Loading the road…", Math.round(2.2f * u), 0xFFD7DEEA, 0, 0.14f * u, true), W / 2, Math.round(H * 0.42f + 4.6f * u), -1);
                return;
            }
            for (Element e : ELEMENTS.values()) e.placed = e.drawn = false;
            boolean ending = game.victoryTicks > 0;

            int y = element(s, "counter", cfg, W, H, edit, u, false, uu -> drawCounter(s, game, W, H, uu, dt, nowMs));

            Feed.Item last = game.feed.last();
            if (last == null && edit) last = SAMPLE_TOAST;
            final Feed.Item toast = last;
            final int toastY = y + Math.round(1.2f * u);
            y = element(s, "toast", cfg, W, H, edit, u, false, uu -> drawToast(s, toast, W, uu, toastY, nowMs));

            int rowY = Math.round(Math.max(y + 1.2f * u, 21.5f * u));
            drawStatusRow(s, game, W, H, u, rowY, edit);

            final int cardY = Math.round(rowY + 6.6f * u);
            Feed.Item banner = cfg.giftBanner ? game.feed.banner(nowMs) : null;
            long bannerUntil = game.feed.bannerUntil();
            if (banner == null && edit) {
                banner = SAMPLE_BANNER;
                bannerUntil = nowMs + 60_000;
            }
            if ((cfg.showInfoCard || edit) && !ending && (banner == null || edit)) {
                element(s, "card", cfg, W, H, edit, u, false, uu -> {
                    float cardW = Math.min(W - 6f * uu, 52f * uu);
                    // Reserve the card's area even while it is between pages so the editor box stays put.
                    recording.include((W - cardW) / 2f, cardY, cardW, 21.5f * uu);
                    drawInfoCard(s, game, W, uu, cardY, nowMs, edit);
                    return 0;
                });
            }
            if (banner != null && !ending) {
                final Feed.Item b = banner;
                final long until = bannerUntil;
                element(s, "banner", cfg, W, H, edit, u, false, uu -> {
                    drawBanner(s, b, until, W, uu, cardY, nowMs);
                    return 0;
                });
            }
            if (!ending) element(s, "feed", cfg, W, H, edit, u, false, uu -> {
                drawFeed(s, game, W, H, uu, nowMs, edit);
                return 0;
            });

            upperBottom = 0;
            upperTop = H;
            upperLeft = W;
            upperRight = 0;
            for (Element el : ELEMENTS.values()) {
                if (!el.upper || !el.placed || !el.drawn || el.y + el.h / 2f > H * 0.5f) continue;
                upperBottom = Math.max(upperBottom, el.y + el.h);
                upperTop = Math.min(upperTop, el.y);
                upperLeft = Math.min(upperLeft, el.x);
                upperRight = Math.max(upperRight, el.x + el.w);
            }
            if (upperRight <= upperLeft) {
                upperLeft = upperRight = W / 2f;
                upperTop = 0;
            }
            try {
                drawBottom(s, game, W, H, u, pt, nowMs, edit);
            } catch (Throwable t) {
                recording = null;
                if (System.currentTimeMillis() - lastBottomError > 10000) {
                    lastBottomError = System.currentTimeMillis();
                    RTTT.LOG.warn("RTTT bottom HUD failed: {}", t.toString());
                }
            }
            if (ending) drawVictory(s, game, W, H, u, nowMs);
        } finally {
            recording = null;
            s.pop();
            publish(W, H);
        }
    }

    private static long ultraAskedAt;

    /** Ultra gifts are not in the gift table, so their pictures are fetched here from their own URLs. */
    private static void requestUltraIcons(Config cfg, long nowMs) {
        if (nowMs - ultraAskedAt < 1000) return;
        ultraAskedAt = nowMs;
        GiftImages.request(firstName(cfg.ultraPlusGift), cfg.ultraPlusImage);
        GiftImages.request(firstName(cfg.ultraMinusGift), cfg.ultraMinusImage);
    }

    static String firstName(String list) {
        return list == null ? "" : list.split(",")[0].trim();
    }

    private static float[] project(Game game, double wx, double wy, double wz, float pt, int W, int H) {
        double cx = game.cam.ix(pt), cy = game.cam.iy(pt), cz = game.cam.iz(pt);
        double p = Math.toRadians(game.cam.pitch);
        double rx = wx - cx, ry = wy - cy, rz = wz - cz;
        double fwd = -ry * Math.sin(p) - rz * Math.cos(p);
        double up = ry * Math.cos(p) - rz * Math.sin(p);
        if (fwd < 0.1) return null;
        double tanV = Math.tan(Math.toRadians(rttt.client.CameraHook.fov(game.cfg, W, H)) / 2.0);
        double tanH = tanV * W / (double) H;
        return new float[]{(float) (W / 2.0 + rx / (fwd * tanH) * W / 2.0), (float) (H / 2.0 - up / (fwd * tanV) * H / 2.0)};
    }

    private static Sprites.Sprite bubbleSprite(Host.Line line, float u, int maxW) {
        boolean steve = line.who == Host.Who.STEVE;
        String key = "bub|" + line.who + "|" + line.ru + "|" + line.en + "|" + Math.round(u * 10) + "|" + maxW;
        return Sprites.get(key, () -> BottomCards.bubble(steve ? "Стив" : "Алекс", steve ? BottomCards.STEVE : BottomCards.ALEX, line.ru, line.en, u, maxW));
    }

    private static float smGround = Float.NaN, bubX = Float.NaN, bubY = Float.NaN, upperHold = 0;
    private static Host.Line bubLine = null;
    private static Host.Line sampleLine = null;
    private static long lastBottomNs = 0;
    private static final List<String> TOP_NAMES = new ArrayList<>();
    private static final List<Long> TOP_METERS = new ArrayList<>();

    private static void drawBottom(Surface g, Game game, int W, int H, float u, float pt, long nowMs, boolean edit) {
        Config cfg = game.cfg;
        double carX = game.car.ix(pt), carY = game.car.iy(pt), ang = game.car.iang(pt);
        float[] ground = project(game, carX, rttt.world.Track.groundAt(carX), rttt.world.Track.FRONT_Z + 0.5, pt, W, H);
        if (ground == null) return;
        long ns = System.nanoTime();
        float dt = lastBottomNs == 0 ? 0.016f : Math.min(0.1f, (ns - lastBottomNs) / 1e9f);
        lastBottomNs = ns;
        if (Float.isNaN(smGround) || Math.abs(smGround - ground[1]) > H * 0.4f) smGround = ground[1];
        else smGround += (ground[1] - smGround) * (1f - (float) Math.exp(-dt / 0.8f));
        if (upperHold == 0) upperHold = upperBottom;
        if (upperBottom > upperHold) upperHold += (upperBottom - upperHold) * (1f - (float) Math.exp(-dt / 0.15f));
        else upperHold += (upperBottom - upperHold) * (1f - (float) Math.exp(-dt / 1.5f));
        float margin = Math.max(4, 1.2f * u);
        float zoneTop = smGround + 1.0f * u;
        float zoneBottom = H - margin;

        Host.Line line = cfg.hostEnabled ? game.host.current(nowMs) : null;
        if (line == null && edit) {
            if (sampleLine == null) sampleLine = game.host.sample(Host.Who.ALEX, "Спасибо за подарок!", "Thanks for the gift!");
            line = sampleLine;
            line.start = nowMs - 1000;
            line.until = nowMs + 5000;
        }
        if (line == null) {
            bubLine = null;
        } else {
            final Host.Line said = line;
            element(g, "bubble", cfg, W, H, edit, u, true, uu -> {
                drawBubble(g, game, said, W, H, uu, pt, nowMs, dt, margin);
                return 0;
            });
        }
        if (cfg.topHelpersEnabled || edit) element(g, "top", cfg, W, H, edit, u, true, uu -> {
            drawTop(g, game, W, H, uu, margin, zoneTop, zoneBottom, edit);
            return 0;
        });
    }

    private static void drawBubble(Surface g, Game game, Host.Line line, int W, int H, float u, float pt, long nowMs, float dt, float margin) {
        double carX = game.car.ix(pt), carY = game.car.iy(pt), ang = game.car.iang(pt);
        int maxW = Math.round(Math.min(W - 2 * margin, 46f * u));
        Sprites.Sprite b = bubbleSprite(line, u, maxW);
        if (b != null) {
            boolean steve = line.who == Host.Who.STEVE;
            double seat = steve ? rttt.client.render.CarModel.DRIVER_HIP_X : rttt.client.render.CarModel.REAR_HIP_X;
            double c = Math.cos(ang), sn = Math.sin(ang);
            double hx = carX + c * seat - sn * 1.85, hy = carY + sn * seat + c * 1.85;
            float[] head = project(game, hx, hy, rttt.world.Track.CAR_Z, pt, W, H);
            if (head != null) {
                float tailH = 1.0f * u;
                float tx = head[0] - b.w / 2f;
                float ty = head[1] - tailH - b.h;
                tx = Math.max(margin, Math.min(W - margin - b.w, tx));
                ty = Math.max(Math.max(margin, upperHold + 0.5f * u), Math.min(H - margin - b.h, ty));
                if (line != bubLine || Float.isNaN(bubX)) {
                    bubLine = line;
                    bubX = tx;
                    bubY = ty;
                } else {
                    float k = 1f - (float) Math.exp(-dt / 0.18f);
                    bubX += (tx - bubX) * k;
                    bubY += (ty - bubY) * k;
                }
                long age = nowMs - line.start;
                long left = line.until + Host.FADE_MS - nowMs;
                float a = Math.max(0f, Math.min(1f, Math.min(age / 180f, left / (float) Host.FADE_MS)));
                int pop = Math.round((1f - Math.min(1f, age / 180f)) * 0.5f * u);
                int bx = Math.round(bubX), by = Math.round(bubY) + pop;
                float tip = Math.max(bx + 1.4f * u, Math.min(bx + b.w - 1.4f * u, head[0]));
                int th = Math.round(tailH);
                for (int t = 0; t < th; t++) {
                    int half = Math.max(1, Math.round((th - t) * 0.85f));
                    int yy = by + b.h + t;
                    fill(g, Math.round(tip) - half, yy, Math.round(tip) + half, yy + 1, tint(0xFFFFFF, Math.round(244 * a)));
                }
                draw(g, b, bx, by, alpha(Math.round(255 * a)));
            }
        }
    }

    private static void drawTop(Surface g, Game game, int W, int H, float u, float margin, float zoneTop, float zoneBottom, boolean edit) {
        List<Map.Entry<String, Long>> top = game.topHelpers(3);
        TOP_NAMES.clear();
        TOP_METERS.clear();
        StringBuilder key = new StringBuilder("top3|").append(Math.round(u * 10)).append('|').append(W);
        if (top.isEmpty() && edit) top = List.of(Map.entry("anna", 15_000L), Map.entry("max", 4_500L), Map.entry("kirill", 700L));
        if (top.isEmpty()) return;
        for (Map.Entry<String, Long> en : top) {
            String n = en.getKey().length() > 18 ? en.getKey().substring(0, 17) + "…" : en.getKey();
            TOP_NAMES.add(n);
            TOP_METERS.add(en.getValue());
            key.append('|').append(n).append('=').append(en.getValue() / 10);
        }
        final List<String> names = new ArrayList<>(TOP_NAMES);
        final List<Long> meters = new ArrayList<>(TOP_METERS);
        // 1) a full table in a free top corner (landscape windows), 2) one line at the bottom, 3) hidden.
        int tableW = Math.round(Math.min(W - 2 * margin, 40f * u));
        Sprites.Sprite table = null;
        float tx = 0, ty = 0;
        boolean fits = false;
        float leftRoom = upperLeft - 2 * margin, rightRoom = W - upperRight - 2 * margin;
        if (Math.max(leftRoom, rightRoom) >= Math.min(tableW, 30f * u)) {
            int tw = Math.round(Math.min(tableW, Math.max(leftRoom, rightRoom)));
            table = Sprites.get(key + "|t|" + tw, () -> BottomCards.top3(names, meters, u, tw));
            if (table != null) {
                tx = leftRoom >= rightRoom ? margin : W - margin - table.w;
                ty = Math.max(margin, upperTop);
                fits = ty + table.h <= H - margin;
            }
        }
        if (!fits) {
            int rw = Math.round(W - 2 * margin);
            table = Sprites.get(key + "|r|" + rw, () -> BottomCards.top3Row(names, meters, u, rw));
            if (table == null) return;
            tx = (W - table.w) / 2f;
            ty = zoneBottom - table.h;
            fits = ty >= zoneTop + 0.3f * u;
        }
        float target = fits || edit ? 1f : 0f;
        top3Alpha += (target - top3Alpha) * 0.18f;
        if (top3Alpha < 0.02f || table == null) return;
        draw(g, table, Math.round(tx), Math.round(ty), alpha(Math.round(255 * top3Alpha)));
    }

    private static int drawCounter(Surface g, Game game, int W, int H, float u, double dt, long nowMs) {
        double target = game.state.distance;
        if (Double.isNaN(shown)) shown = target;
        if (!measuring) {
            double k = 1 - Math.exp(-dt / 0.16);
            shown += (target - shown) * k;
            if (Math.abs(target - shown) < 0.5) shown = target;
        }
        long value = Math.round(shown);

        if (!measuring && game.lastChangeTime != seenChangeTime) {
            seenChangeTime = game.lastChangeTime;
            changeShownAt = nowMs;
            pulseColor = game.lastChangeAmount > 0 ? 0x5CFF8A : (game.lastChangeAmount < 0 ? 0xFF5A5A : 0xFFFFFF);
        }
        float pulse = 1f - Math.min(1f, (nowMs - changeShownAt) / 900f);
        int color = lerpColor(0xFFFFFF, pulseColor, pulse * 0.9f);
        if (game.victoryTicks > 0) color = lerpColor(game.defeat ? 0xFF5A4A : 0xFFD84A, 0xFFFFFF, (float) (0.5 + 0.5 * Math.sin(nowMs / 120.0)) * 0.4f);

        String str = format(value) + NNBSP + "м";
        int size = Math.round(7.2f * u);
        float adv = Sprites.measure(str, size);
        float maxW = W * 0.9f;
        if (adv > maxW) {
            size = Math.max(8, (int) Math.floor(size * maxW / adv));
            adv = Sprites.measure(str, size);
        }
        int top = Math.round(6.2f * u);
        int plateH = Math.round(size * 1.22f);
        int plateW = Math.round(Math.min(W - 2 * u, adv + 3.4f * u));
        Sprites.Sprite plate = panel(plateW, plateH, Math.round(plateH * 0.32f), 0x66101622, 0x880A0E16, 0x33FFFFFF);
        draw(g, plate, W / 2 - plateW / 2, top, -1);
        float outline = Math.max(1.5f, size * 0.045f);
        int x = Math.round(W / 2f - adv / 2f);
        int ty = top + Math.round((plateH - size * 1.18f) / 2f);
        float pen = x;
        int bounce = Math.round(-pulse * pulse * 0.6f * u);
        for (int i = 0; i < str.length(); i++) {
            String ch = String.valueOf(str.charAt(i));
            Sprites.Sprite s = text(ch, size, 0xFFFFFFFF, 0, outline, true);
            if (s == null) continue;
            draw(g, s, Math.round(pen) - s.padX, ty - s.padY + bounce, tint(color, 255));
            pen += s.advance;
        }
        return top + plateH;
    }

    /** The newest event: who did what, and what it changed. */
    private static int drawToast(Surface g, Feed.Item item, int W, float u, int y, long nowMs) {
        if (item == null) return y;
        int maxW = Math.round(W - 3f * u);
        boolean ready = EventCards.iconReady(item);
        Sprites.Sprite card = Sprites.get("toast|" + item.id + "|" + Math.round(u * 10) + "|" + maxW + "|" + ready, () -> EventCards.toast(item, u, maxW));
        if (card == null) return y;
        long age = Math.max(0, nowMs - item.time);
        float appear = Math.min(1f, age / 180f);
        int a = (int) (255 * appear * (age > 9000 ? 0.75f : 1f));
        int slide = Math.round((1f - appear) * 1.2f * u);
        draw(g, card, (W - card.w) / 2, y + slide, alpha(a));
        return y + Math.round(4.6f * u);
    }

    /** Likes, wins, the TEST badge and the queue line: laid out together, then placed one by one. */
    private static void drawStatusRow(Surface g, Game game, int W, int H, float u, int y, boolean edit) {
        Config cfg = game.cfg;
        final long have = game.likeProgress(), need = Math.max(1, cfg.likesPerTrigger), reward = cfg.metersPerLikeTrigger;
        final String wins = Long.toString(game.state.wins);
        final boolean test = game.testMode() || edit;
        int margin = Math.round(4f * u), gap = Math.round(1.0f * u);
        // Default positions come from the normal size; measuring creates no textures.
        int likesW, winsW, badgeW;
        boolean was = Sprites.measureOnly;
        Sprites.measureOnly = true;
        try {
            Sprites.Sprite likes = likesSprite(u, have, need, reward);
            likesW = likes == null ? 0 : likes.w;
            winsW = winsWidth(u, wins);
            badgeW = Math.round(text("TEST", Math.round(1.8f * u), 0xFF1A1205, 0, 0, false).advance + 2.0f * u);
        } finally {
            Sprites.measureOnly = was;
        }
        int total = likesW + gap + winsW;
        int x = (W - total) / 2;
        if (test && margin + badgeW + gap > x) x = Math.max(margin, Math.min(W - margin - total, margin + badgeW + gap));
        final int likesX = x, winsX = x + likesW + gap;

        element(g, "likes", cfg, W, H, edit, u, false, uu -> {
            draw(g, likesSprite(uu, have, need, reward), likesX, y, -1);
            return 0;
        });
        element(g, "wins", cfg, W, H, edit, u, false, uu -> {
            drawWins(g, uu, wins, winsX, y);
            return 0;
        });
        if (test) element(g, "test", cfg, W, H, edit, u, false, uu -> {
            int h = Math.round(3.6f * uu), size = Math.round(1.8f * uu);
            Sprites.Sprite bt = text("TEST", size, 0xFF1A1205, 0, 0, false);
            int bw = Math.round(bt.advance + 2.0f * uu);
            draw(g, panel(bw, h, h / 2, 0xF0FFB02E, 0xF0F08A12, 0x60FFFFFF), margin, y, -1);
            draw(g, bt, margin + Math.round(1.0f * uu) - bt.padX, y + (h - size) / 2 - bt.padY - Math.round(size * 0.08f), -1);
            return 0;
        });
        int q = game.mobQueueSize(), gq = game.giftQueueSize();
        if (q > 0 || gq > 0 || edit) {
            if (edit && q == 0 && gq == 0) gq = 3;
            final String txt = "Очередь · queue:" + (gq > 0 ? "  подарки/gifts " + gq : "") + (q > 0 ? "  мобы/mobs " + q : "");
            final int qy = y + Math.round(3.6f * u) + Math.round(0.5f * u);
            element(g, "queue", cfg, W, H, edit, u, false, uu -> {
                int qs = Math.round(1.6f * uu);
                float qa = Sprites.measure(txt, qs);
                if (qa > W - 4 * uu) qs = Math.max(8, (int) (qs * (W - 4 * uu) / qa));
                centered(g, text(txt, qs, 0xFFE6EAF2, 0, Math.max(1f, uu * 0.1f), true), W / 2, qy, -1);
                return 0;
            });
        }
    }

    private static Sprites.Sprite likesSprite(float u, long have, long need, long reward) {
        return Sprites.get("likes|" + Math.round(u * 10) + "|" + have + "|" + need + "|" + reward, () -> EventCards.likes(u, have, need, reward));
    }

    private static int winsWidth(float u, String wins) {
        Sprites.Sprite wt = text(wins, Math.round(2.4f * u), 0xFFFFE27A, 0xFFFFF6C8, Math.max(1f, u * 0.12f), true);
        Sprites.Sprite wl = text("побед · wins", Math.round(1.5f * u), 0xFFE9EDF5, 0, Math.max(1f, u * 0.08f), false);
        return Math.round(Math.round(2.8f * u) + wt.advance + wl.advance + 3.2f * u);
    }

    private static void drawWins(Surface g, float u, String wins, int x, int y) {
        int h = Math.round(3.6f * u);
        Sprites.Sprite wt = text(wins, Math.round(2.4f * u), 0xFFFFE27A, 0xFFFFF6C8, Math.max(1f, u * 0.12f), true);
        Sprites.Sprite wl = text("побед · wins", Math.round(1.5f * u), 0xFFE9EDF5, 0, Math.max(1f, u * 0.08f), false);
        int trophy = Math.round(2.8f * u);
        Sprites.Sprite tr = Sprites.get("trophy|" + trophy, () -> Sprites.trophy(trophy));
        int w = winsWidth(u, wins);
        draw(g, panel(w, h, h / 2, 0xB0141A26, 0xC00D111A, 0x40FFD86A), x, y, -1);
        draw(g, tr, x + Math.round(0.8f * u), y + (h - trophy) / 2, -1);
        int tx = x + Math.round(0.8f * u) + trophy + Math.round(0.5f * u);
        draw(g, wt, tx - wt.padX, y + (h - Math.round(2.4f * u)) / 2 - wt.padY - Math.round(0.2f * u), -1);
        draw(g, wl, Math.round(tx + wt.advance + 0.6f * u) - wl.padX, y + (h - Math.round(1.5f * u)) / 2 - wl.padY - Math.round(0.1f * u), -1);
    }

    private static void drawBanner(Surface g, Feed.Item item, long until, int W, float u, int y, long nowMs) {
        int w = Math.round(Math.min(W - 3f * u, 52f * u));
        boolean ready = EventCards.iconReady(item);
        Sprites.Sprite card = Sprites.get("banner|" + item.id + "|" + Math.round(u * 10) + "|" + w + "|" + ready, () -> EventCards.banner(item, u, w));
        if (card == null) return;
        long age = Math.max(0, nowMs - item.time);
        float in = item.id < 0 ? 1f : Math.min(1f, age / 160f);
        float out = Math.max(0f, Math.min(1f, (until - nowMs) / 320f));
        float a = Math.min(in, out);
        int drop = Math.round((1f - in) * (1f - in) * -2.2f * u);
        recording.include((W - w) / 2f, y, w, card.h);
        draw(g, card, (W - w) / 2, y + drop, alpha(Math.round(255 * a)));
    }

    private static void drawFeed(Surface g, Game game, int W, int H, float u, long nowMs, boolean edit) {
        Config cfg = game.cfg;
        int rows = cfg.feedRows;
        if (rows <= 0) return;
        boolean toastShown = cfg.hud("toast").visible;
        long life = cfg.feedSeconds * 1000L;
        List<Feed.Item> items = game.feed.recent(toastShown ? 1 : 0, rows, life, nowMs);
        if (edit && items.size() < rows) {
            items = new ArrayList<>(items);
            for (int i = 0; items.size() < rows; i++) items.add(SAMPLE_ROWS[i % SAMPLE_ROWS.length]);
        }
        if (items.isEmpty()) return;
        int w = Math.round(Math.min(W - 3f * u, 46f * u));
        int rowH = Math.round(3.3f * u), step = rowH + Math.round(0.45f * u);
        int x0 = (W - w) / 2, y0 = Math.round(H * 0.5f + 11f * u);
        recording.include(x0, y0, w, rows * step - (step - rowH));
        Feed.Item newest = game.feed.last();
        float settle = newest == null ? 1f : Math.min(1f, Math.max(0, nowMs - newest.time) / 220f);
        float shift = (1f - settle) * (1f - settle);
        for (int i = 0; i < items.size(); i++) {
            final Feed.Item item = items.get(i);
            boolean ready = EventCards.iconReady(item);
            Sprites.Sprite row = Sprites.get("row|" + item.id + "|" + Math.round(u * 10) + "|" + w + "|" + ready, () -> EventCards.row(item, u, w));
            if (row == null) continue;
            long age = Math.max(0, nowMs - item.time);
            float a = item.id < 0 ? 1f : Math.max(0f, Math.min(1f, (life - age) / 1200f));
            if (i == 0) a *= settle;
            a *= 1f - i * 0.12f;
            int yy = y0 + i * step - Math.round(shift * step);
            draw(g, row, x0, Math.max(y0, yy), alpha(Math.round(255 * a)));
        }
    }

    private static List<Config.GiftRule> cardGifts = List.of();
    private static long cardGiftsAt = 0;
    private static Config cardGiftsCfg;
    private static String cardKeyBase = "";
    private static int cardReady = -1;

    /** Gifts shown on the hint card, cheapest first. Rebuilt twice a second instead of every frame. */
    private static List<Config.GiftRule> overlayGifts(Config cfg, long nowMs) {
        if (cfg == cardGiftsCfg && nowMs - cardGiftsAt < 500) return cardGifts;
        List<Config.GiftRule> gifts = new ArrayList<>();
        for (Config.GiftRule r : cfg.gifts) {
            if (r == null || r.name == null || !r.showInOverlay) continue;
            gifts.add(r);
            if (r.image != null && !r.image.isBlank()) GiftImages.request(r.name, r.image);
        }
        gifts.sort(Comparator.comparingInt((Config.GiftRule r) -> r.coins).thenComparingLong(r -> r.meters));
        int ready = 0;
        StringBuilder sig = new StringBuilder();
        for (Config.GiftRule r : gifts) {
            if (GiftImages.get(r.name) != null) ready++;
            sig.append(r.name).append(':').append(r.ru).append(':').append(r.meters).append(';');
        }
        cardReady = ready;
        cardKeyBase = ready + "|" + cfg.likesPerTrigger + "|" + cfg.metersPerLikeTrigger + "|" + sig.toString().hashCode() + "|" + cfg.colorOnFollow
                + "|" + cfg.ultraPlusGift + cfg.ultraMinusGift + "|" + cfg.ultraAmount + "|" + cfg.goal;
        cardGifts = gifts;
        cardGiftsCfg = cfg;
        cardGiftsAt = nowMs;
        return gifts;
    }

    private static String cardKey(Config cfg, List<Config.GiftRule> gifts, int page, int w, int h, long nowMs) {
        return "card2|" + page + "|" + w + "|" + h + "|" + cardKeyBase;
    }

    private static final int PAGE_MS = 8000;
    private static final int PER_PAGE = 6;

    private static boolean drawInfoCard(Surface g, Game game, int W, float u, int y, long nowMs, boolean force) {
        int w = Math.round(Math.min(W - 6f * u, 52f * u));
        int h = Math.round(20f * u);
        int x0 = (W - w) / 2;
        List<Config.GiftRule> gifts = overlayGifts(game.cfg, nowMs);
        int giftPages = (gifts.size() + PER_PAGE - 1) / PER_PAGE;
        int pages = 1 + giftPages + 1;
        long show = (long) pages * PAGE_MS;
        long cycle = show + Math.max(0, game.cfg.infoCardPauseSeconds) * 1000L;
        long tc = Math.max(0, nowMs - infoCardStartedAt) % (force ? show : cycle);
        if (tc >= show) return tc >= cycle - 1200;
        float cardFade = force ? 1f : Math.min(1f, Math.min(tc, show - tc) / 600f);
        long t = tc;
        int page = (int) (t / PAGE_MS);
        long inPage = t % PAGE_MS;
        float fade = Math.min(1f, Math.min(inPage, PAGE_MS - inPage) / 450f);
        int a = Math.round(255 * fade * cardFade);

        Sprites.Sprite bg = panel(w, h, Math.round(1.8f * u), 0xB8121826, 0xCC0B0F18, 0x38FFFFFF);
        draw(g, bg, x0, y, alpha(Math.round(255 * cardFade)));
        final int pg = page;
        String key = cardKey(game.cfg, gifts, page, w, h, nowMs);
        Sprites.Sprite content = Sprites.get(key, () -> {
            if (pg == 0) return howToPage(w, h, u, game.cfg);
            if (pg <= giftPages) {
                int from = (pg - 1) * PER_PAGE;
                return giftsPage(w, h, u, gifts.subList(from, Math.min(gifts.size(), from + PER_PAGE)), pg, giftPages);
            }
            return ultraPage(w, h, u, game.cfg);
        });
        draw(g, content, x0, y, alpha(a));
        int dot = Math.max(3, Math.round(0.7f * u)), gap = Math.round(1.1f * u);
        int dx = W / 2 - (pages * gap) / 2;
        for (int i = 0; i < pages; i++) {
            int col = ((Math.round((i == page ? 0xE6 : 0x50) * cardFade)) << 24) | 0xFFFFFF;
            fill(g, dx + i * gap, y + h + Math.round(0.6f * u), dx + i * gap + dot, y + h + Math.round(0.6f * u) + dot, col);
        }
        return true;
    }

    private static java.awt.font.TextLayout fit(String text, float size, float maxW) {
        java.awt.font.TextLayout tl = Sprites.layout(text, size);
        if (maxW > 0 && tl.getAdvance() > maxW) tl = Sprites.layout(text, size * maxW / tl.getAdvance());
        return tl;
    }

    private static float put(Graphics2D g, String text, float size, int argb, float x, float y, float maxW, boolean center) {
        java.awt.font.TextLayout tl = fit(text, size, maxW);
        float tx = center ? x - tl.getAdvance() / 2f : x;
        g.setColor(new Color(argb, true));
        tl.draw(g, tx, y + tl.getAscent());
        return tl.getAdvance();
    }

    private static float header(Graphics2D g, int w, float u, String ru, String en, int accent) {
        float pad = 1.6f * u;
        g.setColor(new Color(accent, true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(pad, pad + 0.3f * u, 0.5f * u, 3.8f * u, 0.5f * u, 0.5f * u));
        float tx = pad + 1.4f * u, maxW = w - tx - pad;
        put(g, ru, 2.05f * u, 0xFFFFFFFF, tx, pad, maxW, false);
        put(g, en, 1.35f * u, 0xFFAEB8CC, tx, pad + 2.45f * u, maxW, false);
        return pad + 4.9f * u;
    }

    /**
     * The first hint page: what to type in chat and what happens. Short lines, one idea each,
     * readable at a glance on a phone.
     */
    private static BufferedImage howToPage(int w, int h, float u, Config cfg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Sprites.gfx(img);
        float pad = 1.6f * u;
        float y = header(g, w, u, "Пиши в чат — и это случится", "Type in chat to make it happen", 0xFFFFC94A);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"mob", "корова, крипер, дракон…", "моб выбежит на дорогу", "a mob runs onto the road"});
        rows.add(new String[]{"dice", "рандом", "случайный моб", "a random mob"});
        rows.add(new String[]{"heart", cfg.likesPerTrigger + " лайков", "+" + format(cfg.metersPerLikeTrigger) + " м вперёд",
                cfg.likesPerTrigger + " likes = +" + format(cfg.metersPerLikeTrigger) + " m"});
        if (cfg.colorOnFollow) rows.add(new String[]{"star", "подписка", "новый цвет машины", "new car colour"});
        float rowH = Math.min(3.8f * u, (h - y - pad) / rows.size());
        float icon = Math.min(2.7f * u, rowH * 0.78f);
        float colA = pad + icon + 1.1f * u;
        float colB = colA + (w - colA - pad) * 0.5f;
        // One font size for every row, so the list reads as a table.
        float keySize = 2.0f * u, resSize = 1.75f * u;
        for (String[] r : rows) {
            keySize = Math.min(keySize, 2.0f * u * Math.min(1f, (colB - colA - 0.8f * u) / Sprites.measure(r[1], 2.0f * u)));
            resSize = Math.min(resSize, 1.75f * u * Math.min(1f, (w - colB - pad) / Sprites.measure("→ " + r[2], 1.75f * u)));
        }
        for (int n = 0; n < rows.size(); n++) {
            String[] r = rows.get(n);
            float iy = y + (rowH - icon) / 2f;
            switch (r[0]) {
                case "mob" -> {
                    BufferedImage egg = Icons.mob("creeper");
                    if (egg != null) g.drawImage(Icons.pixel(egg, Math.round(icon)), Math.round(pad), Math.round(iy), null);
                }
                case "dice" -> Icons.dice(g, pad, iy, icon, 0xFFFFD86A);
                case "heart" -> Icons.heart(g, pad, iy, icon, 0xFFFF5C8A);
                default -> Icons.star(g, pad, iy, icon, 0xFF8FD3FF);
            }
            java.awt.font.TextLayout key = Sprites.layout(r[1], keySize);
            g.setColor(Color.WHITE);
            key.draw(g, colA, y + (rowH + key.getAscent() - key.getDescent()) / 2f);
            java.awt.font.TextLayout res = Sprites.layout("→ " + r[2], resSize);
            g.setColor(new Color(0xFFD5DCEA, true));
            res.draw(g, colB, y + (rowH + res.getAscent() - res.getDescent()) / 2f);
            y += rowH;
            if (n < rows.size() - 1) {
                float t = Math.max(1f, u * 0.06f);
                g.setColor(new Color(0x1AFFFFFF, true));
                g.fill(new java.awt.geom.Rectangle2D.Float(colA, y - t / 2f, w - colA - pad, t));
            }
        }
        g.dispose();
        return img;
    }

    private static void giftIcon(Graphics2D g, String name, boolean positive, float x, float y, int size) {
        BufferedImage icon = GiftImages.get(name);
        if (icon != null) {
            g.drawImage(Sprites.icon(icon, size), Math.round(x), Math.round(y), null);
            return;
        }
        float s = size;
        g.setColor(new Color(positive ? 0xFF3B7BE0 : 0xFFC0473A, true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(x + s * 0.14f, y + s * 0.36f, s * 0.72f, s * 0.52f, s * 0.12f, s * 0.12f));
        g.setColor(new Color(positive ? 0xFF5B9BFF : 0xFFE0675A, true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(x + s * 0.08f, y + s * 0.24f, s * 0.84f, s * 0.18f, s * 0.08f, s * 0.08f));
        g.setColor(new Color(0xFFFFD84A, true));
        g.fill(new java.awt.geom.Rectangle2D.Float(x + s * 0.45f, y + s * 0.24f, s * 0.1f, s * 0.64f));
    }

    private static BufferedImage giftsPage(int w, int h, float u, List<Config.GiftRule> gifts, int page, int pages) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Sprites.gfx(img);
        float pad = 1.6f * u;
        String suffix = pages > 1 ? "  " + page + "/" + pages : "";
        float top = header(g, w, u, "Подарки двигают машину" + suffix, "Gifts move the car — green forward, red back", 0xFF4FA3FF);
        int cols = 2, rows = (PER_PAGE + cols - 1) / cols;
        float gapX = 1.0f * u;
        float cellW = (w - 2 * pad - gapX) / cols, cellH = (h - top - pad) / rows;
        for (int i = 0; i < gifts.size(); i++) {
            Config.GiftRule r = gifts.get(i);
            float cx = pad + (i % cols) * (cellW + gapX), cy = top + (i / cols) * cellH;
            int is = Math.round(Math.min(cellH * 0.85f, 3.5f * u));
            giftIcon(g, r.name, r.meters >= 0, cx, cy + (cellH - is) / 2f, is);
            float tx = cx + is + 0.6f * u, maxW = cellW - is - 0.8f * u;
            String meters = (r.meters >= 0 ? "+" : "−") + format(Math.abs(r.meters)) + " м";
            String name = r.ru != null && !r.ru.isBlank() ? r.ru : r.name;
            float ty = cy + (cellH - 3.6f * u) / 2f;
            put(g, meters, 1.8f * u, r.meters >= 0 ? 0xFF5CFF8A : 0xFFFF6B6B, tx, ty, maxW, false);
            put(g, name + (r.ru != null && !r.ru.isBlank() && !r.ru.equalsIgnoreCase(r.name) ? " · " + r.name : ""), 1.2f * u, 0xFFC4CCDA, tx, ty + 2.25f * u, maxW, false);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage ultraPage(int w, int h, float u, Config cfg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Sprites.gfx(img);
        float pad = 1.6f * u;
        float top = header(g, w, u, "Ультраподарки", "Ultra gifts", 0xFFFFD84A);
        String plus = cfg.ultraPlusGift.split(",")[0].trim();
        String minus = cfg.ultraMinusGift.split(",")[0].trim();
        float colW = (w - 2 * pad - 1.0f * u) / 2f;
        float bodyH = h - top - pad - 2.4f * u;
        ultraCol(g, plus, "+" + format(cfg.ultraAmount) + " м", 0xFF5CFF8A, "Ракета вперёд:", "Rocket forward!", pad, top, colW, bodyH, u, true);
        ultraCol(g, minus, "−" + format(cfg.ultraAmount) + " м", 0xFFFF6B6B, "Улёт назад:", "Blasted back!", pad + colW + 1.0f * u, top, colW, bodyH, u, false);
        put(g, "Цель — " + format(cfg.goal) + " м  ·  Goal — " + format(cfg.goal) + " m", 1.5f * u, 0xFFB9C3D6, w / 2f, h - pad - 1.9f * u, w - 2 * pad, true);
        g.dispose();
        return img;
    }

    private static void ultraCol(Graphics2D g, String gift, String amount, int color, String ru, String en, float x, float y, float colW, float colH, float u, boolean positive) {
        float textH = 5.9f * u;
        int is = Math.round(Math.max(2f * u, Math.min(colH - textH - 0.3f * u, 5.0f * u)));
        giftIcon(g, gift, positive, x + (colW - is) / 2f, y, is);
        float cx = x + colW / 2f, ty = y + is + 0.3f * u;
        put(g, amount, 1.9f * u, color, cx, ty, colW, true);
        put(g, ru + " " + gift, 1.25f * u, 0xFFE9EDF5, cx, ty + 2.4f * u, colW, true);
        put(g, en, 1.15f * u, 0xFFAEB8CC, cx, ty + 4.1f * u, colW, true);
    }

    private static void drawVictory(Surface g, Game game, int W, int H, float u, long nowMs) {
        float t = 1f - game.victoryTicks / 160f;
        float appear = Math.min(1f, Math.min(t * 6f, game.victoryTicks / 15f));
        int a = Math.round(255 * appear);
        final boolean defeat = game.defeat;
        Sprites.Sprite card = Sprites.get("victory|" + W + "|" + u + "|" + game.victoryNumber + "|" + defeat,
                () -> VictoryCard.create(W, u, game.victoryNumber, defeat));
        if (card == null) return;
        float margin = Math.max(2, Math.min(W, H) * 0.006f);
        float scale = Math.min(1, Math.min((W - 2 * margin) / card.w, (H - 2 * margin) / card.h));
        float x = (W - card.w * scale) / 2;
        float y = Math.max(margin, Math.min(H - margin - card.h * scale, H * 0.30f - 1.6f * u));
        g.push();
        try {
            g.translate(x, y);
            g.scale(scale);
            g.blit(card, 0, 0, alpha(a));
        } finally {
            g.pop();
        }
    }
}
