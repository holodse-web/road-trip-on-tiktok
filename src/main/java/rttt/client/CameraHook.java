package rttt.client;

import net.minecraft.client.Minecraft;
import rttt.client.game.Game;

public final class CameraHook {
    private CameraHook() {}

    public static boolean active() {
        Game g = Game.I;
        return g != null && g.active && Minecraft.getInstance().level != null;
    }

    public static float pitch() {
        return Game.I.cam.pitch;
    }

    public static double x(float pt) { return Game.I.cam.ix(pt); }
    public static double y(float pt) { return Game.I.cam.iy(pt); }
    public static double z(float pt) { return Game.I.cam.iz(pt); }

    public static float fov() {
        Minecraft mc = Minecraft.getInstance();
        return fov(Game.I.cfg, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    /** Vertical field of view that shows {@code viewWidth} blocks across the frame at the car's distance. */
    public static float fov(rttt.client.game.Config cfg, int width, int height) {
        double w = Math.max(1, width), h = Math.max(1, height);
        double aspect = w / h;
        double halfW = cfg.viewWidth * 0.5;
        double dist = Math.max(4, cfg.cameraDistance);
        double tanH = halfW / dist;
        double tanV = tanH / aspect;
        double fov = Math.toDegrees(2 * Math.atan(tanV));
        return (float) Math.max(30, Math.min(110, fov));
    }
}
