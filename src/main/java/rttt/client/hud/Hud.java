package rttt.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import rttt.RTTT;
import rttt.client.game.Config;
import rttt.client.game.Game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Hud {
    private static final char NNBSP = ' ';
    private static final char MINUS = '−';

    private static double shown = Double.NaN;
    private static long lastNanos = 0;
    private static long seenChangeTime = 0;
    private static long changeShownAt = 0;
    private static int pulseColor = 0xFFFFFF;
    private static long lastError = 0;
    private static boolean infoCardVisible;
    private static long infoCardStartedAt;
    private static final HudBatch UPPER = new HudBatch();
    private static HudBatch recording;

    private Hud() {}

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
        lastNanos = 0;
        infoCardVisible = false;
        infoCardStartedAt = 0;
        Sprites.clear();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        try {
            renderInner(g, delta);
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

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int centered(GuiGraphicsExtractor g, Sprites.Sprite s, int cx, int y, int argb) {
        if (s == null) return 0;
        draw(g, s, Math.round(cx - s.advance / 2f) - s.padX, y - s.padY, argb);
        return s.h - 2 * s.padY;
    }

    private interface HudCommand {
        void draw(GuiGraphicsExtractor g);
    }

    private static final class HudBatch {
        final List<HudCommand> commands = new ArrayList<>();
        float left, top, right, bottom, visibleBottom;

        void reset() {
            commands.clear();
            left = top = Float.POSITIVE_INFINITY;
            right = bottom = visibleBottom = Float.NEGATIVE_INFINITY;
        }

        void include(float x, float y, float w, float h) {
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + w);
            bottom = Math.max(bottom, y + h);
        }

        void render(GuiGraphicsExtractor g, Config cfg, int width, int height) {
            if (commands.isEmpty()) return;
            float margin = Math.max(2, Math.min(width, height) * 0.006f);
            float requested = Double.isFinite(cfg.hudScale) ? (float) Math.max(0.5, Math.min(2, cfg.hudScale)) : 1;
            float scale = Math.min(requested, Math.min((width - 2 * margin) / Math.max(1, right - left),
                    (height - 2 * margin) / Math.max(1, bottom - top)));
            float pivotX = width / 2f;
            float tx = pivotX * (1 - scale) + offset(cfg.hudOffsetX, width);
            float ty = top * (1 - scale) + offset(cfg.hudOffsetY, height);
            tx = Math.max(margin - left * scale, Math.min(width - margin - right * scale, tx));
            ty = Math.max(margin - top * scale, Math.min(height - margin - bottom * scale, ty));
            lastUpperBottom = ty + bottom * scale;
            lastUpperVisible = ty + (Float.isFinite(visibleBottom) ? visibleBottom : bottom) * scale;
            lastUpperLeft = tx + left * scale;
            lastUpperRight = tx + right * scale;
            lastUpperTop = ty + top * scale;
            g.pose().pushMatrix();
            try {
                g.pose().translate(tx, ty);
                g.pose().scale(scale, scale);
                for (HudCommand command : commands) command.draw(g);
            } finally {
                g.pose().popMatrix();
                commands.clear();
            }
        }

        private static float offset(double percent, int pixels) {
            return Double.isFinite(percent) ? (float) (Math.max(-100, Math.min(100, percent)) * pixels / 100) : 0;
        }
    }

    private static void draw(GuiGraphicsExtractor g, Sprites.Sprite s, int x, int y, int argb) {
        if (s == null) return;
        if (recording == null) {
            Sprites.draw(g, s, x, y, argb);
            return;
        }
        recording.include(x, y, s.w, s.h);
        recording.commands.add(target -> Sprites.draw(target, s, x, y, argb));
    }

    private static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
        if (recording == null) {
            g.fill(x1, y1, x2, y2, argb);
            return;
        }
        recording.include(x1, y1, x2 - x1, y2 - y1);
        recording.commands.add(target -> target.fill(x1, y1, x2, y2, argb));
    }

    private static void renderInner(GuiGraphicsExtractor g, DeltaTracker delta) {
        Game game = Game.I;
        if (game == null || !game.active) return;
        Minecraft mc = Minecraft.getInstance();
        int W = mc.getWindow().getWidth(), H = mc.getWindow().getHeight();
        int gs = Math.max(1, mc.getWindow().getGuiScale());
        if (W <= 0 || H <= 0) return;
        float u = Math.min(H, W * 16f / 9f) / 100f;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        long nowMs = System.currentTimeMillis();
        if (game.cfg.showInfoCard && !infoCardVisible) infoCardStartedAt = nowMs;
        infoCardVisible = game.cfg.showInfoCard;

        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            float flash = game.fx.flashPrev + (game.fx.flash - game.fx.flashPrev) * delta.getGameTimeDeltaPartialTick(false);
            if (flash > 0.01f) {
                int a = (int) (Math.min(1, flash) * 0.55f * 255);
                g.fill(0, 0, W, H, (a << 24) | (game.fx.flashColor & 0xFFFFFF));
            }
            if (game.entering) {
                centered(g, text("Загрузка дороги…", Math.round(3.4f * u), 0xFFFFFFFF, 0, 0.18f * u, true), W / 2, Math.round(H * 0.42f), -1);
                centered(g, text("Loading the road…", Math.round(2.2f * u), 0xFFD7DEEA, 0, 0.14f * u, true), W / 2, Math.round(H * 0.42f + 4.6f * u), -1);
                return;
            }
            UPPER.reset();
            recording = UPPER;
            try {
                int y = drawCounter(g, game, W, H, u, dt, nowMs);
                y = drawChange(g, game, W, u, y + Math.round(1.2f * u), nowMs);
                int rowY = Math.round(Math.max(y + 1.2f * u, 21.5f * u));
                drawStatusRow(g, game, W, u, rowY);
                UPPER.visibleBottom = Math.max(UPPER.bottom, rowY + 6.0f * u);
                if (game.cfg.showInfoCard && game.victoryTicks == 0) {
                    int cardY = Math.round(rowY + 6.6f * u);
                    // Reserve the rotating card even during its pause so edge clamping stays still.
                    float cardW = Math.min(W - 6f * u, 52f * u);
                    UPPER.include((W - cardW) / 2f, cardY, cardW, 21.5f * u);
                    if (drawInfoCard(g, game, W, u, cardY, nowMs)) UPPER.visibleBottom = cardY + 20f * u;
                }
            } finally {
                recording = null;
            }
            lastUpperBottom = 0;
            lastUpperVisible = 0;
            lastUpperLeft = lastUpperRight = W / 2f;
            lastUpperTop = 0;
            UPPER.render(g, game.cfg, W, H);
            try {
                drawBottom(g, game, W, H, u, delta.getGameTimeDeltaPartialTick(false), nowMs);
            } catch (Throwable t) {
                if (System.currentTimeMillis() - lastBottomError > 10000) {
                    lastBottomError = System.currentTimeMillis();
                    RTTT.LOG.warn("RTTT bottom HUD failed: {}", t.toString());
                }
            }
            if (game.victoryTicks > 0) drawVictory(g, game, W, H, u, nowMs);
        } finally {
            g.pose().popMatrix();
        }
    }

    private static float lastUpperBottom = 0, lastUpperLeft = 0, lastUpperRight = 0, lastUpperTop = 0, lastUpperVisible = 0;
    private static long lastBottomError = 0;
    private static float top3Alpha = 0;

    private static float[] project(Game game, double wx, double wy, double wz, float pt, int W, int H) {
        double cx = game.cam.ix(pt), cy = game.cam.iy(pt), cz = game.cam.iz(pt);
        double p = Math.toRadians(game.cam.pitch);
        double rx = wx - cx, ry = wy - cy, rz = wz - cz;
        double fwd = -ry * Math.sin(p) - rz * Math.cos(p);
        double up = ry * Math.cos(p) - rz * Math.sin(p);
        if (fwd < 0.1) return null;
        double tanV = Math.tan(Math.toRadians(rttt.client.CameraHook.fov()) / 2.0);
        double tanH = tanV * W / (double) H;
        return new float[]{(float) (W / 2.0 + rx / (fwd * tanH) * W / 2.0), (float) (H / 2.0 - up / (fwd * tanV) * H / 2.0)};
    }

    private static Sprites.Sprite bubbleSprite(rttt.client.game.Host.Line line, float u, int maxW) {
        boolean steve = line.who == rttt.client.game.Host.Who.STEVE;
        String key = "bub|" + line.who + "|" + line.ru + "|" + line.en + "|" + Math.round(u * 10) + "|" + maxW;
        return Sprites.get(key, () -> BottomCards.bubble(steve ? "Стив" : "Алекс", steve ? BottomCards.STEVE : BottomCards.ALEX, line.ru, line.en, u, maxW));
    }

    private static float smGround = Float.NaN, bubX = Float.NaN, bubY = Float.NaN, upperHold = 0;
    private static rttt.client.game.Host.Line bubLine = null;
    private static long lastBottomNs = 0;

    private static void drawBottom(GuiGraphicsExtractor g, Game game, int W, int H, float u, float pt, long nowMs) {
        double carX = game.car.ix(pt), carY = game.car.iy(pt), ang = game.car.iang(pt);
        float[] ground = project(game, carX, rttt.world.Track.groundAt(carX), rttt.world.Track.FRONT_Z + 0.5, pt, W, H);
        if (ground == null) return;
        long ns = System.nanoTime();
        float dt = lastBottomNs == 0 ? 0.016f : Math.min(0.1f, (ns - lastBottomNs) / 1e9f);
        lastBottomNs = ns;
        if (Float.isNaN(smGround) || Math.abs(smGround - ground[1]) > H * 0.4f) smGround = ground[1];
        else smGround += (ground[1] - smGround) * (1f - (float) Math.exp(-dt / 0.8f));
        if (lastUpperVisible > upperHold) upperHold += (lastUpperVisible - upperHold) * (1f - (float) Math.exp(-dt / 0.15f));
        else upperHold += (lastUpperVisible - upperHold) * (1f - (float) Math.exp(-dt / 1.5f));
        float margin = Math.max(4, 1.2f * u);
        float zoneTop = smGround + 1.0f * u;
        float zoneBottom = H - margin;

        rttt.client.game.Host.Line line = game.cfg.hostEnabled ? game.host.current(nowMs) : null;
        if (line != null) {
            int maxW = Math.round(Math.min(W - 2 * margin, 46f * u));
            Sprites.Sprite b = bubbleSprite(line, u, maxW);
            if (b != null) {
                boolean steve = line.who == rttt.client.game.Host.Who.STEVE;
                double seat = steve ? -0.06 : -1.19;
                double c = Math.cos(ang), s = Math.sin(ang);
                double hx = carX + c * seat - s * 1.75, hy = carY + s * seat + c * 1.75;
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
                    long left = line.until + rttt.client.game.Host.FADE_MS - nowMs;
                    float a = Math.max(0f, Math.min(1f, Math.min(age / 180f, left / (float) rttt.client.game.Host.FADE_MS)));
                    int pop = Math.round((1f - Math.min(1f, age / 180f)) * 0.5f * u);
                    int bx = Math.round(bubX), by = Math.round(bubY) + pop;
                    float tip = Math.max(bx + 1.4f * u, Math.min(bx + b.w - 1.4f * u, head[0]));
                    int th = Math.round(tailH);
                    for (int t = 0; t < th; t++) {
                        int half = Math.max(1, Math.round((th - t) * 0.85f));
                        int yy = by + b.h + t;
                        g.fill(Math.round(tip) - half, yy, Math.round(tip) + half, yy + 1, tint(0xFFFFFF, Math.round(244 * a)));
                    }
                    Sprites.draw(g, b, bx, by, alpha(Math.round(255 * a)));
                }
            }
        } else {
            bubLine = null;
        }
        float bubblesBottom = zoneTop;

        if (!game.cfg.topHelpersEnabled) return;
        java.util.List<java.util.Map.Entry<String, Long>> top = game.topHelpers(3);
        if (top.isEmpty()) return;
        java.util.List<String> names = new ArrayList<>();
        java.util.List<Long> meters = new ArrayList<>();
        StringBuilder key = new StringBuilder("top3|").append(Math.round(u * 10)).append('|').append(W);
        for (java.util.Map.Entry<String, Long> e : top) {
            String n = e.getKey().length() > 18 ? e.getKey().substring(0, 17) + "…" : e.getKey();
            names.add(n);
            meters.add(e.getValue());
            key.append('|').append(n).append('=').append(e.getValue() / 10);
        }
        // 1) a full table in a free top corner (landscape windows), 2) one line at the bottom, 3) hidden.
        int tableW = Math.round(Math.min(W - 2 * margin, 40f * u));
        Sprites.Sprite table = null;
        float tx = 0, ty = 0;
        boolean fits = false;
        float leftRoom = lastUpperLeft - 2 * margin, rightRoom = W - lastUpperRight - 2 * margin;
        if (Math.max(leftRoom, rightRoom) >= Math.min(tableW, 30f * u)) {
            int tw = Math.round(Math.min(tableW, Math.max(leftRoom, rightRoom)));
            table = Sprites.get(key + "|t|" + tw, () -> BottomCards.top3(names, meters, u, tw));
            if (table != null) {
                tx = leftRoom >= rightRoom ? margin : W - margin - table.w;
                ty = Math.max(margin, lastUpperTop);
                fits = ty + table.h <= H - margin;
            }
        }
        if (!fits) {
            int rw = Math.round(W - 2 * margin);
            table = Sprites.get(key + "|r|" + rw, () -> BottomCards.top3Row(names, meters, u, rw));
            if (table == null) return;
            tx = (W - table.w) / 2f;
            ty = zoneBottom - table.h;
            fits = ty >= zoneTop + 0.3f * u && ty >= bubblesBottom + 0.6f * u;
        }
        float target = fits ? 1f : 0f;
        top3Alpha += (target - top3Alpha) * 0.18f;
        if (top3Alpha < 0.02f || table == null) return;
        Sprites.draw(g, table, Math.round(tx), Math.round(ty), alpha(Math.round(255 * top3Alpha)));
    }

    private static int drawCounter(GuiGraphicsExtractor g, Game game, int W, int H, float u, double dt, long nowMs) {
        double target = game.state.distance;
        if (Double.isNaN(shown)) shown = target;
        double k = 1 - Math.exp(-dt / 0.16);
        shown += (target - shown) * k;
        if (Math.abs(target - shown) < 0.5) shown = target;
        long value = Math.round(shown);

        if (game.lastChangeTime != seenChangeTime) {
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

    private static int drawChange(GuiGraphicsExtractor g, Game game, int W, float u, int y, long nowMs) {
        if (game.lastChangeTime == 0) return y;
        String line;
        long amt = game.lastChangeAmount;
        if (amt == 0) {
            line = game.lastChangeLabel;
        } else {
            String sign = amt > 0 ? "+" : String.valueOf(MINUS);
            String user = game.lastChangeUser == null ? "" : game.lastChangeUser;
            if (user.length() > 22) user = user.substring(0, 21) + "…";
            String label = game.lastChangeLabel == null ? "" : game.lastChangeLabel;
            line = sign + format(Math.abs(amt)) + NNBSP + "м · " + user + (label.contains("×") ? " · " + label : "");
        }
        if (line == null || line.isEmpty()) return y;
        int size = Math.round(2.5f * u);
        float maxText = W - 4f * u - (game.lastChangeGift != null ? 4.2f * u : 0) - 2.8f * u;
        float adv0 = Sprites.measure(line, size);
        if (adv0 > maxText) size = Math.max(8, (int) Math.floor(size * maxText / adv0));
        Sprites.Sprite t = text(line, size, 0xFFFFFFFF, 0, Math.max(1f, size * 0.05f), true);
        if (t == null) return y;
        int h = Math.round(4.4f * u);
        int iconSize = Math.round(3.6f * u);
        Sprites.Sprite icon = null;
        if (game.lastChangeGift != null) {
            BufferedImage img = GiftImages.get(game.lastChangeGift);
            if (img != null) icon = Sprites.get("gi|" + GiftImages.key(game.lastChangeGift) + "|" + iconSize, () -> Sprites.icon(img, iconSize));
        }
        int padX = Math.round(1.4f * u);
        int w = Math.round(t.advance) + padX * 2 + (icon != null ? iconSize + Math.round(0.6f * u) : 0);
        w = Math.min(w, W - Math.round(2 * u));
        int bgTop = amt > 0 ? 0xE61F9E4A : (amt < 0 ? 0xE6C8322B : 0xE63A4356);
        int bgBot = amt > 0 ? 0xE6157A38 : (amt < 0 ? 0xE6992620 : 0xE62A3242);
        long age = nowMs - changeShownAt;
        float appear = Math.min(1f, age / 180f);
        int a = (int) (255 * appear * (nowMs - game.lastChangeTime > 9000 ? 0.75f : 1f));
        int slide = Math.round((1f - appear) * 1.2f * u);
        Sprites.Sprite bg = panel(w, h, h / 2, bgTop, bgBot, 0x40FFFFFF);
        int x0 = W / 2 - w / 2;
        draw(g, bg, x0, y + slide, alpha(a));
        int cx = x0 + padX;
        if (icon != null) {
            draw(g, icon, cx - Math.round(0.4f * u), y + slide + (h - iconSize) / 2, alpha(a));
            cx += iconSize + Math.round(0.2f * u);
        }
        draw(g, t, cx - t.padX, y + slide + (h - size) / 2 - t.padY - Math.round(size * 0.08f), alpha(a));
        return y + h;
    }

    private static void drawStatusRow(GuiGraphicsExtractor g, Game game, int W, float u, int y) {
        int h = Math.round(3.6f * u);
        int margin = Math.round(4f * u);
        int size = Math.round(2.0f * u);
        Sprites.Sprite wt = text(Long.toString(game.state.wins), Math.round(2.4f * u), 0xFFFFE27A, 0xFFFFF6C8, Math.max(1f, u * 0.12f), true);
        Sprites.Sprite wl = text("побед · wins", Math.round(1.5f * u), 0xFFE9EDF5, 0, Math.max(1f, u * 0.08f), false);
        int trophy = Math.round(2.8f * u);
        Sprites.Sprite tr = Sprites.get("trophy|" + trophy, () -> Sprites.trophy(trophy));
        int w = Math.round(trophy + wt.advance + wl.advance + 3.2f * u);
        int x = game.testMode() ? W - margin - w : (W - w) / 2;
        draw(g, panel(w, h, h / 2, 0xB0141A26, 0xC00D111A, 0x40FFD86A), x, y, -1);
        draw(g, tr, x + Math.round(0.8f * u), y + (h - trophy) / 2, -1);
        int tx = x + Math.round(0.8f * u) + trophy + Math.round(0.5f * u);
        draw(g, wt, tx - wt.padX, y + (h - Math.round(2.4f * u)) / 2 - wt.padY - Math.round(0.2f * u), -1);
        draw(g, wl, Math.round(tx + wt.advance + 0.6f * u) - wl.padX, y + (h - Math.round(1.5f * u)) / 2 - wl.padY - Math.round(0.1f * u), -1);

        if (game.testMode()) {
            float room = W - 2 * margin - (trophy + wt.advance + wl.advance + 3.2f * u) - 2.4f * u - u;
            float ba = Sprites.measure("ТЕСТОВЫЙ РЕЖИМ · TEST", size);
            if (ba > room) size = Math.max(8, (int) (size * room / ba));
            Sprites.Sprite bt = text("ТЕСТОВЫЙ РЕЖИМ · TEST", size, 0xFF1A1205, 0, 0, false);
            int bw = Math.round(bt.advance + 2.4f * u);
            draw(g, panel(bw, h, h / 2, 0xF0FFB02E, 0xF0F08A12, 0x60FFFFFF), margin, y, -1);
            draw(g, bt, margin + Math.round(1.2f * u) - bt.padX, y + (h - size) / 2 - bt.padY - Math.round(size * 0.08f), -1);
        }
        int q = game.mobQueueSize(), gq = game.giftQueueSize();
        if (q > 0 || gq > 0) {
            String txt = "Очередь · queue:" + (gq > 0 ? "  подарки/gifts " + gq : "") + (q > 0 ? "  мобы/mobs " + q : "");
            int qs = Math.round(1.6f * u);
            float qa = Sprites.measure(txt, qs);
            if (qa > W - 4 * u) qs = Math.max(8, (int) (qs * (W - 4 * u) / qa));
            Sprites.Sprite qt = text(txt, qs, 0xFFE6EAF2, 0, Math.max(1f, u * 0.1f), true);
            centered(g, qt, W / 2, y + h + Math.round(0.5f * u), -1);
        }
    }

    private static final int PAGE_MS = 8000;
    private static final int PER_PAGE = 6;

    private static boolean drawInfoCard(GuiGraphicsExtractor g, Game game, int W, float u, int y, long nowMs) {
        int w = Math.round(Math.min(W - 6f * u, 52f * u));
        int h = Math.round(20f * u);
        int x0 = (W - w) / 2;
        List<Config.GiftRule> gifts = new ArrayList<>();
        for (Config.GiftRule r : game.cfg.gifts) {
            if (r == null || r.name == null || !r.showInOverlay) continue;
            gifts.add(r);
            if (r.image != null && !r.image.isBlank()) GiftImages.request(r.name, r.image);
        }
        gifts.sort(Comparator.comparingInt((Config.GiftRule r) -> r.coins).thenComparingLong(r -> r.meters));
        int giftPages = (gifts.size() + PER_PAGE - 1) / PER_PAGE;
        int pages = 1 + giftPages + 1;
        long show = (long) pages * PAGE_MS;
        long cycle = show + Math.max(0, game.cfg.infoCardPauseSeconds) * 1000L;
        long tc = Math.max(0, nowMs - infoCardStartedAt) % cycle;
        if (tc >= show) return tc >= cycle - 1200;
        float cardFade = Math.min(1f, Math.min(tc, show - tc) / 600f);
        long t = tc;
        int page = (int) (t / PAGE_MS);
        long inPage = t % PAGE_MS;
        float fade = Math.min(1f, Math.min(inPage, PAGE_MS - inPage) / 450f);
        int a = Math.round(255 * fade * cardFade);

        Sprites.Sprite bg = panel(w, h, Math.round(1.8f * u), 0xB8121826, 0xCC0B0F18, 0x38FFFFFF);
        draw(g, bg, x0, y, alpha(Math.round(255 * cardFade)));
        int ready = 0;
        for (Config.GiftRule r : gifts) if (GiftImages.get(r.name) != null) ready++;
        final int pg = page, rd = ready;
        String key = "card2|" + page + "|" + w + "|" + h + "|" + rd + "|" + game.cfg.likesPerTrigger + "|" + game.cfg.metersPerLikeTrigger
                + "|" + gifts.size() + "|" + gifts.hashCode() + "|" + game.cfg.colorOnFollow + "|" + game.cfg.ultraPlusGift + game.cfg.ultraMinusGift;
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

    private static BufferedImage howToPage(int w, int h, float u, Config cfg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Sprites.gfx(img);
        float pad = 1.6f * u;
        float y = header(g, w, u, "Пиши в чат имя моба — он появится", "Type a mob name in chat to spawn it", 0xFFFFC94A);
        float rowH = 3.1f * u;
        y = row(g, w, u, y, 0xFF35C76A, "ПОМОГУТ", "HELP", "корова, лошадь, волк, голем, пчела, дельфин");
        y = row(g, w, u, y + 0.5f * u, 0xFFE5483B, "МЕШАЮТ", "HURT", "крипер, зомби, скелет, паук, варден, дракон");
        y += 0.8f * u;
        g.setColor(new Color(0x22FFFFFF, true));
        g.fill(new java.awt.geom.Rectangle2D.Float(pad, y, w - 2 * pad, Math.max(1f, u * 0.12f)));
        y += 0.7f * u;
        float maxW = w - 2 * pad;
        put(g, "♥ " + cfg.likesPerTrigger + " лайков = +" + format(cfg.metersPerLikeTrigger) + " м  ·  " + cfg.likesPerTrigger + " likes = +" + format(cfg.metersPerLikeTrigger) + " m",
                1.5f * u, 0xFFFF8FB1, pad, y, maxW, false);
        if (cfg.colorOnFollow) {
            put(g, "Подписка — новый цвет машины  ·  Follow = new car colour", 1.5f * u, 0xFF8FD3FF, pad, y + 2.3f * u, maxW, false);
        }
        g.dispose();
        return img;
    }

    private static float row(Graphics2D g, int w, float u, float y, int color, String ru, String en, String list) {
        float pad = 1.6f * u, h = 3.0f * u;
        java.awt.font.TextLayout l1 = Sprites.layout(ru, 1.35f * u), l2 = Sprites.layout(en, 1.05f * u);
        float tagW = Math.max(l1.getAdvance(), l2.getAdvance()) + 1.4f * u;
        g.setColor(new Color(color, true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(pad, y, tagW, h, 0.9f * u, 0.9f * u));
        g.setColor(Color.WHITE);
        l1.draw(g, pad + (tagW - l1.getAdvance()) / 2f, y + 0.35f * u + l1.getAscent());
        g.setColor(new Color(0xE6FFFFFF, true));
        l2.draw(g, pad + (tagW - l2.getAdvance()) / 2f, y + 1.75f * u + l2.getAscent());
        float tx = pad + tagW + 1.0f * u;
        java.awt.font.TextLayout tl = fit(list, 1.6f * u, w - tx - pad);
        g.setColor(new Color(0xFFE9EDF5, true));
        tl.draw(g, tx, y + (h - (tl.getAscent() + tl.getDescent())) / 2f + tl.getAscent());
        return y + h;
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

    private static void drawVictory(GuiGraphicsExtractor g, Game game, int W, int H, float u, long nowMs) {
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
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            g.pose().scale(scale, scale);
            draw(g, card, 0, 0, alpha(a));
        } finally {
            g.pose().popMatrix();
        }
    }

}
