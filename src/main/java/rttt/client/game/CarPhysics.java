package rttt.client.game;

import rttt.world.Track;

public class CarPhysics {
    public static final double WHEEL_R = 0.425;
    public static final double[] WHEEL_X = {1.42, -1.38};
    public static final double ANCHOR_Y = 0.02;
    public static double L_MAX = 0.80, L_MIN = 0.26;
    static final double MASS = 1.0, INERTIA = 1.9, G = 24.0;
    public static double K = 40, C = 14.0, C_REBOUND = 20.0, DOWNFORCE = 0.03;
    public static double BUMP_K = 800, BUMP_CAP = 260, BUMP_DAMP = 45, DF_CAP = 6, STICK = 14;
    static final double MU = 1.25;
    static final int SUBSTEPS = 12;

    static final double[][] HULL = {
            {2.32, -0.40}, {-2.30, -0.40}, {2.36, 0.08}, {-2.34, 0.20},
            {0.95, 0.62}, {-0.25, 0.98}, {-1.25, 0.95}, {0.0, -0.55}, {1.6, 0.1}, {-1.6, 0.25}
    };

    public double x, y, vx, vy, ang, w;
    public double px, py, pang;
    public final double[] susL = {0.53, 0.53};
    public final double[] susLPrev = {0.53, 0.53};
    public final boolean[] grounded = new boolean[2];
    public final double[] spin = {0, 0}, spinPrev = {0, 0}, spinRate = {0, 0};
    public final double[] wheelPrevL = {0.53, 0.53};
    public double cruise = 11.5;
    public double boost = 0, boostTime = 0;
    public double slow = 0, slowTime = 0;
    public double noAirControl = 0;
    public double levitate = 0;
    public boolean frozen = false;

    public double landImpact = 0;
    public double scrapeImpact = 0;
    public double slip = 0;
    public double braking = 0;
    public double rpm = 0;
    public int airTicks = 0;
    public int recoveries = 0;
    private double upsideTime = 0, stuckTime = 0, recoverTime = 0;
    private double recoilTime = 0, recoilSpeed = 0;
    public boolean recoveringNow = false;

    public void reset(double startX) {
        x = startX;
        y = Math.max(Track.wheelSupport(startX + WHEEL_X[0], WHEEL_R), Track.wheelSupport(startX + WHEEL_X[1], WHEEL_R)) + 0.50;
        for (int i = 0; i < 2; i++) { susL[i] = susLPrev[i] = wheelPrevL[i] = 0.53; }
        vx = cruise * 0.6;
        vy = 0;
        ang = 0;
        w = 0;
        px = x; py = y; pang = ang;
        upsideTime = stuckTime = recoverTime = 0;
        recoilTime = recoilSpeed = 0;
        recoveringNow = false;
        boost = slow = 0;
    }

    public double nitroTime = 0, nitroSpeed = 0;

    public double targetSpeed() {
        if (recoilTime > 0) return recoilSpeed;
        double t = cruise + boost - slow;
        if (nitroTime > 0) t = Math.max(t, nitroSpeed);
        return t;
    }

    public void tick() {
        px = x; py = y; pang = ang;
        for (int i = 0; i < 2; i++) {
            spinPrev[i] = spin[i];
            susLPrev[i] = susL[i];
        }
        landImpact = 0;
        scrapeImpact = 0;
        slip = 0;
        braking = 0;
        double dt = 0.05 / SUBSTEPS;
        for (int s = 0; s < SUBSTEPS; s++) step(dt);

        if (boostTime > 0) boostTime -= 0.05; else boost *= 0.94;
        if (nitroTime > 0) nitroTime -= 0.05;
        if (slowTime > 0) slowTime -= 0.05; else slow *= 0.9;
        if (noAirControl > 0) noAirControl -= 0.05;
        if (levitate > 0) levitate = inBigMotion() ? 0 : levitate - 0.05;
        if (recoilTime > 0) recoilTime = Math.max(0, recoilTime - 0.05);
        else if (vx >= -32) recoilSpeed = 0;

        boolean anyGround = grounded[0] || grounded[1];
        airTicks = anyGround ? 0 : airTicks + 1;
        rpm = Math.min(1.4, Math.abs(spinRate[1]) * WHEEL_R / 14.0 + (anyGround ? 0 : 0.15) + slip * 0.3);

        double c = Math.cos(ang);
        double support = Track.wheelSupport(x, 0.6);
        // Airborne flips and a successful upright landing are not a rescue condition.
        boolean strandedUpside = c < 0.25 && y < support + 1.7 && Math.abs(vx) < 3
                && Math.abs(vy) < 2 && Math.abs(w) < 1.5 && recoilTime <= 0;
        if (strandedUpside) upsideTime += 0.05; else upsideTime = 0;
        if (recoverTime > 0) recoverTime -= 0.05;
        recoveringNow = false;
        if (upsideTime > 1.3 && recoverTime <= 0) {
            recoverTime = 1.2;
            upsideTime = 0;
            recoveries++;
            recoveringNow = true;
            vy = Math.max(vy, 7.5);
            double a = normAngle(ang);
            w = -a / 0.55;
            noAirControl = 0;
        }
        if (nitroTime > 0 && vx < Math.max(4, nitroSpeed * 0.35)) blockedTime += 0.05; else blockedTime = 0;
        if (blockedTime > 0.2) {
            blockedTime = 0;
            vy = Math.max(vy, 9);
            vx = Math.max(vx, 10);
            w *= 0.3;
        }
        boolean resting = anyGround || Math.abs(vy) < 0.8;
        if (resting && c > 0.6 && Math.abs(vx) < 0.7 && !frozen && targetSpeed() > 2 && recoilTime <= 0) stuckTime += 0.05; else stuckTime = 0;
        if (stuckTime > 2.4) {
            stuckTime = 0;
            vx += 5;
            vy += 3.5;
            recoveringNow = true;
            recoveries++;
        }
        double sup = Track.wheelSupport(x, 0.6);
        if (y < sup - 2.5 || Double.isNaN(y) || Double.isNaN(x)) {
            if (Double.isNaN(x)) x = px;
            y = sup + 0.8;
            vy = 2;
            ang = 0;
            w = 0;
            if (Double.isNaN(vx)) vx = 0;
        }
        ang = normAngle(ang);
    }

    static double normAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    private void step(double dt) {
        double fx = 0, fy = -G * MASS, tq = 0;
        double c = Math.cos(ang), s = Math.sin(ang);
        double dnx = s, dny = -c;
        double target = targetSpeed();

        for (int i = 0; i < 2; i++) {
            double lx = WHEEL_X[i], ly = ANCHOR_Y;
            double ax = x + c * lx - s * ly;
            double ay = y + s * lx + c * ly;
            if (c < 0.55) {
                grounded[i] = false;
                susL[i] = L_MAX;
                spinRate[i] *= 0.995;
                spin[i] += spinRate[i] * dt;
                continue;
            }
            double L = susL[i];
            for (int it = 0; it < 3; it++) {
                double cx = ax + dnx * L;
                double sup = Track.wheelSupport(cx, WHEEL_R);
                L = (ay - sup) / c;
            }
            if (L >= L_MAX) {
                grounded[i] = false;
                susL[i] = L_MAX;
                double tgt = target / WHEEL_R * 1.1;
                spinRate[i] += (tgt - spinRate[i]) * Math.min(1, dt * 2.5);
                spin[i] += spinRate[i] * dt;
                continue;
            }
            grounded[i] = true;
            double Lc = Math.max(L, L_MIN);
            double compVel = (Lc - wheelPrevL[i]) / dt;
            wheelPrevL[i] = Lc;
            double F = K * (L_MAX - Lc) - (compVel > 0 ? C_REBOUND : C) * compVel;
            if (L < L_MIN) {
                double pen = L_MIN - L;
                F += Math.min(BUMP_CAP, BUMP_K * pen + BUMP_DAMP * Math.max(0, -compVel));
                landImpact = Math.max(landImpact, Math.min(1.5, -compVel * 0.10 + pen * 3));
            } else if (compVel < -3.0) {
                landImpact = Math.max(landImpact, Math.min(1.2, (-compVel - 3.0) * 0.08));
            }
            if (F < 0) F = 0;
            susL[i] = Lc;
            double sfx = -dnx * F, sfy = -dny * F;
            fx += sfx;
            fy += sfy;
            tq += (ax - x) * sfy - (ay - y) * sfx;

            double wcx = ax + dnx * Lc, wcy = ay + dny * Lc;
            double slope = (Track.wheelSupport(wcx + 0.2, WHEEL_R) - Track.wheelSupport(wcx - 0.2, WHEEL_R)) / 0.4;
            double tl = Math.sqrt(1 + slope * slope);
            double tx = 1 / tl, ty = slope / tl;
            double cpx = wcx - tx * 0 + ty * WHEEL_R * 0, cpy = wcy - WHEEL_R;
            double vpx = vx - w * (cpy - y), vpy = vy + w * (cpx - x);
            double vt = vpx * tx + vpy * ty;
            double share = 0.5;
            double want = (frozen ? -vt * 3.0 : (target - vt) * 4.0);
            double maxDrive = nitroTime > 0 ? 85 : (boost > 2 ? 60 : 42), maxBrake = (nitroTime > 0 || vt - target > 4) ? 7 : 26;
            double Fd = Math.max(-maxBrake, Math.min(maxDrive, want)) * share;
            double N = F * Math.max(0.2, c);
            double Fmax = MU * N;
            boolean sl = Math.abs(Fd) > Fmax;
            if (sl) {
                slip = Math.max(slip, Math.min(1, (Math.abs(Fd) - Fmax) / Math.max(1, Fmax)));
                Fd = Math.signum(Fd) * Fmax;
            }
            if (Fd < -4) braking = Math.max(braking, Math.min(1, -Fd / 15));
            Fd -= vt * 0.08;
            double dfx = tx * Fd, dfy = ty * Fd;
            fx += dfx;
            fy += dfy;
            tq += (cpx - x) * dfy - (cpy - y) * dfx;
            double rate = vt / WHEEL_R;
            if (sl && Fd > 0) rate += 9;
            if (sl && Fd < 0 && Math.abs(vt) > 2) rate *= 0.2;
            spinRate[i] = rate;
            spin[i] += rate * dt;
        }

        for (double[] h : HULL) {
            double hx = x + c * h[0] - s * h[1];
            double hy = y + s * h[0] + c * h[1];
            double g = Track.groundAt(hx);
            double pen = g - hy;
            if (pen <= 0) continue;
            double vpx = vx - w * (hy - y), vpy = vy + w * (hx - x);
            double Fn = 1100 * pen - 35 * vpy;
            if (Fn < 0) Fn = 0;
            double Ff = Math.max(-0.7 * Fn, Math.min(0.7 * Fn, -vpx * 25));
            fx += Ff;
            fy += Fn;
            tq += (hx - x) * Fn - (hy - y) * Ff;
            scrapeImpact = Math.max(scrapeImpact, Math.min(1.5, pen * 3 + Math.abs(vpy) * 0.05));
        }

        boolean anyGround = grounded[0] || grounded[1];
        boolean bothGround = grounded[0] && grounded[1];
        if (!bothGround && noAirControl <= 0 && c > 0) {
            double slopeAng = Math.atan2(Track.smoothHeight(x + 2.0) - Track.smoothHeight(x - 2.0), 4.0);
            slopeAng = Math.max(-0.26, Math.min(0.26, slopeAng));
            double k = (anyGround ? 9.0 : 6.5) * (nitroTime > 0 ? 1.6 : 1.0);
            double damp = anyGround ? 2.6 : 2.2;
            tq += (-k * normAngle(ang - slopeAng) - damp * w) * INERTIA;
        }
        if (recoverTime > 0) {
            tq += (-14 * normAngle(ang) - 3.5 * w) * INERTIA;
        }
        if (levitate > 0) fy += G * MASS * 1.08;
        if (nitroTime > 0 && vx < nitroSpeed) {
            double thrust = Math.min(60, 18 + nitroSpeed * 0.9);
            fx += thrust * MASS;
        }
        if (recoilTime > 0 && vx > recoilSpeed) {
            fx -= Math.min(75, 18 + Math.abs(recoilSpeed) * 0.8) * MASS;
        }

        if (anyGround && c > 0.7 && levitate <= 0) {
            double df = Math.min(DF_CAP, vx * vx * DOWNFORCE);
            fy -= df;
            if (STICK > 0 && bothGround && recoilTime <= 0 && nitroTime <= 0) {
                double slope = (Track.smoothHeight(x + 1.5) - Track.smoothHeight(x - 1.5)) / 3.0;
                double rise = vy - vx * slope;
                if (rise > 0) fy -= Math.min(30, STICK * rise) * MASS;
            }
        }
        fx -= vx * Math.abs(vx) * 0.006;
        fy -= vy * Math.abs(vy) * 0.004;

        if (fx > 400) fx = 400;
        if (fx < -400) fx = -400;
        if (fy > 600) fy = 600;
        vx += fx / MASS * dt;
        vy += fy / MASS * dt;
        w += tq / INERTIA * dt;
        w *= (1 - 0.25 * dt);
        if (vx > 32) vx = 32;
        double reverseLimit = Math.max(32, -recoilSpeed);
        if (vx < -reverseLimit) vx = -reverseLimit;
        if (w > 18) w = 18;
        if (w < -18) w = -18;
        x += vx * dt;
        y += vy * dt;
        ang += w * dt;
    }

    public void knockBack(double power, double up, boolean allowFlip) {
        if (inBigMotion()) {
            vy += 0.4 * Math.max(0, up);
            return;
        }
        double p = Math.max(0, Math.min(2.5, power));
        double back = -(3 + 6.5 * p);
        recoilSpeed = Math.min(recoilTime > 0 ? recoilSpeed : 0, back);
        recoilTime = Math.max(recoilTime, 0.3 + 0.4 * p);
        vx = Math.min(vx, back);
        vy += up * (2.5 + 2.2 * p);
        w += (allowFlip ? 1.6 : 0.8) * p * (Math.random() < 0.5 ? 1 : 0.6);
        if (allowFlip) noAirControl = 0.6 + p * 0.4;
        boost = 0;
        nitroTime = 0;
        recoverTime = upsideTime = stuckTime = 0;
        slow = Math.min(cruise * 0.7, slow + 1.5 * p);
        slowTime = 0.6 + p * 0.4;
    }

    public boolean inBigMotion() {
        return nitroTime > 0.3 || (recoilTime > 0 && recoilSpeed <= -25);
    }

    public void push(double strength, double seconds) {
        if (recoilTime > 0 && recoilSpeed <= -25) return;
        recoilTime = recoilSpeed = 0;
        double st = Math.max(0, strength);
        boost = Math.max(boost, 6 + 9 * st);
        boostTime = Math.max(boostTime, seconds);
        vx = Math.max(vx, cruise) + 4 + 6 * st;
        if (grounded[0] || grounded[1]) vy += 1.0 + 0.5 * st;
        slow = 0;
    }

    private double blockedTime = 0;

    public void launch(double power) {
        recoilTime = recoilSpeed = 0;
        double p = Math.max(0, Math.min(2.5, power));
        vx = Math.max(vx, 14 + 8 * p);
        vy = Math.max(vy, 5 + 4 * p);
        w *= 0.3;
        noAirControl = 0;
        slow = 0;
        frozen = false;
    }

    public void nitro(double speed, double seconds) {
        recoilTime = recoilSpeed = 0;
        nitroSpeed = Math.max(nitroTime > 0 ? nitroSpeed : 0, Math.min(40, speed));
        nitroTime = Math.max(nitroTime, seconds);
        vx = Math.max(vx, cruise + 4);
        slow = 0;
        noAirControl = 0;
    }

    public double blast(double power) {
        double from = x;
        double p = Math.max(1, Math.min(2.4, power));
        recoilSpeed = -(30 + 14 * p);
        recoilTime = 1.4 + 1.1 * p;
        vx = recoilSpeed;
        vy = 9 + 4 * p;
        w = (Math.random() < 0.5 ? 1 : -1) * (2.5 + 2.0 * p);
        noAirControl = 0.8 + p * 0.4;
        recoverTime = upsideTime = stuckTime = 0;
        recoveringNow = false;
        boost = 0;
        nitroTime = 0;
        slow = cruise * 0.6;
        slowTime = 1.2;
        return from;
    }

    public void warpTo(double nx, double ny) {
        recoilTime = recoilSpeed = 0;
        x = px = nx;
        y = py = ny;
        vx = cruise * 0.7;
        vy = -1;
        ang = pang = 0;
        w = 0;
        noAirControl = 0;
        for (int i = 0; i < 2; i++) { susL[i] = susLPrev[i] = wheelPrevL[i] = L_MAX; }
    }

    public void hop(double vyAdd, double spinAdd) {
        vy += vyAdd;
        w += spinAdd;
    }

    public double ix(float pt) { return px + (x - px) * pt; }
    public double iy(float pt) { return py + (y - py) * pt; }
    public double iang(float pt) { return pang + normAngle(ang - pang) * pt; }
    public double ispin(int i, float pt) { return spinPrev[i] + (spin[i] - spinPrev[i]) * pt; }
    public double isus(int i, float pt) { return susLPrev[i] + (susL[i] - susLPrev[i]) * pt; }

    public double frontX() { return x + Math.cos(ang) * 2.35; }
    public double rearX() { return x - Math.cos(ang) * 2.35; }
}
