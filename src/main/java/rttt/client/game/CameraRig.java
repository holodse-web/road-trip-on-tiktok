package rttt.client.game;

import rttt.world.Track;

public final class CameraRig {
    public double x, y, z, px, py, pz;
    public float pitch = 7.5f;
    private double trauma = 0;
    private double shakeT = 0;
    public double shakeX, shakeY, pShakeX, pShakeY;
    private boolean init = false;

    public void snap(CarPhysics car, Config cfg) {
        init = false;
        tick(car, cfg);
    }

    public void tick(CarPhysics car, Config cfg) {
        px = x; py = y; pz = z;
        pShakeX = shakeX; pShakeY = shakeY;
        double lead = 2.6 + Math.max(-2.0, Math.min(3.5, car.vx * 0.09));
        double tx = car.x + lead;
        double road = 0;
        for (int i = -6; i <= 6; i++) road += Track.top((int) Math.floor(car.x + i * 1.5));
        road /= 13.0;
        double ty = Math.max(road, Math.min(car.y - 0.6, road + 6)) + 3.4 + Math.max(-6, Math.min(8, cfg.cameraHeight));
        double tz = Track.CAR_Z + cfg.cameraDistance;
        if (!init) {
            x = tx; y = ty; z = tz;
            px = x; py = y; pz = z;
            init = true;
        } else {
            x += car.vx * 0.05 * 0.85 + (tx - x) * 0.18;
            double dy = ty - y;
            y += dy * (Math.abs(dy) > 4 ? 0.2 : 0.08);
            z += (tz - z) * 0.1;
        }
        double half = cfg.viewWidth * 0.5;
        if (car.x < x - half + 1.5) x = car.x + half - 1.5;
        if (car.x > x + half - 2.0) x = car.x - half + 2.0;

        trauma = Math.max(0, trauma - 0.045);
        shakeT += 1;
        double amt = trauma * trauma;
        shakeX = amt * 0.45 * (Track.noise1(shakeT * 0.9, 501));
        shakeY = amt * 0.35 * (Track.noise1(shakeT * 0.9, 502));
    }

    public void addTrauma(double t) {
        trauma = Math.min(1.0, trauma + t);
    }

    public double ix(float pt) { return px + (x - px) * pt + pShakeX + (shakeX - pShakeX) * pt; }
    public double iy(float pt) { return py + (y - py) * pt + pShakeY + (shakeY - pShakeY) * pt; }
    public double iz(float pt) { return pz + (z - pz) * pt; }
}
