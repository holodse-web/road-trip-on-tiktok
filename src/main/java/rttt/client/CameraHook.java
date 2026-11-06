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
        Game g = Game.I;
        Minecraft mc = Minecraft.getInstance();
        double w = Math.max(1, mc.getWindow().getWidth()), h = Math.max(1, mc.getWindow().getHeight());
        double aspect = w / h;
        double halfW = g.cfg.viewWidth * 0.5;
        double dist = Math.max(4, g.cfg.cameraDistance);
        double tanH = halfW / dist;
        double tanV = tanH / aspect;
        double fov = Math.toDegrees(2 * Math.atan(tanV));
        return (float) Math.max(30, Math.min(110, fov));
    }
}
