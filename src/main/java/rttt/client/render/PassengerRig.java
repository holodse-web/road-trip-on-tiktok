package rttt.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

/**
 * Procedural animation for the driver (Steve) and the back-seat passenger (Alex).
 *
 * Every frame the rig picks a target pose from what is happening to the car, then eases each joint
 * towards it, so any two poses blend without hand-written transitions. Body lean and seat bounce are
 * small springs driven by the car's real acceleration, which makes the pair react to bumps, jumps,
 * nitro and knock-backs without a dedicated animation for each.
 */
public final class PassengerRig {
    public static final float SCALE = 0.9375f;
    /** Hip to shoulder joint and shoulder joint to palm, in blocks. */
    private static final float SHOULDER_UP = 10f / 16f * SCALE, ARM_REACH = 9.2f / 16f * SCALE;

    /** What the rig needs to know about the current frame. */
    public static final class In {
        public double time;
        public float dt;
        /** Acceleration along the car's own forward and up axes, blocks/s². */
        public float accelForward, accelUp;
        public float speed;
        public float carAngle, carSpin;
        public boolean airborne, nitro, thrownBack, freezing, victory, defeat;
        /** 0..1, decays after a landing. */
        public float landing;
        /** Strength of the latest good / bad event, 0 when it is old. 1 is an ordinary gift, 2+ a huge one. */
        public float cheer, scare;
        /** Distance to the nearest mob ahead or behind, 99 when there is none. */
        public float mobAhead = 99, helperBehind = 99;
        public boolean steveTalks, alexTalks;
    }

    public static final class Pose {
        public float rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY, leftArmZ;
        public float headX, headY, headZ, rightLeg, leftLeg;
        /** Whole-body lean about the hips (positive leans back) and seat offset. */
        public float lean, lift;
        final float[] joints = new float[11], targets = new float[11];
    }

    private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, HX = 6, HY = 7, HZ = 8, LEG_R = 9, LEG_L = 10;

    public final Pose steve = new Pose(), alex = new Pose();
    /** Rotation of the steering wheel about its column, radians. */
    public float steer;

    private float leanS, leanSVel, leanA, leanAVel, liftS, liftSVel, liftA, liftAVel, sway, swayVel;
    private float wCheerS, wCheerA, wAir, wVictory, wFlail, wPoint, wWave, wTalkS, wTalkA, wShiver, wSlump, wBrace, wLookBack;
    private boolean init;

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    private static float ease(float cur, float target, float dt, float tau) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt / tau));
    }

    public void reset() {
        init = false;
    }

    public void update(In in) {
        float dt = clamp(in.dt, 0f, 0.1f);
        double t = in.time;

        // ---- Springs: lean with acceleration, sink into the seat on a hard landing.
        float fwd = clamp(in.accelForward, -60, 60), up = clamp(in.accelUp, -80, 80);
        float leanTarget = clamp(fwd * 0.011f, -0.34f, 0.30f);
        float liftTarget = clamp(-up * 0.0022f, -0.10f, 0.08f);
        boolean snap = !init;
        if (snap) {
            init = true;
            leanS = leanA = leanTarget;
            leanSVel = leanAVel = liftS = liftA = liftSVel = liftAVel = sway = swayVel = 0;
        }
        for (int i = 0; i < 2; i++) {
            float h = dt * 0.5f;
            leanSVel += (70f * (leanTarget - leanS) - 11f * leanSVel) * h;
            leanS += leanSVel * h;
            leanAVel += (46f * (leanTarget * 1.25f - leanA) - 7.5f * leanAVel) * h;
            leanA += leanAVel * h;
            liftSVel += (160f * (liftTarget - liftS) - 16f * liftSVel) * h;
            liftS += liftSVel * h;
            liftAVel += (120f * (liftTarget * 1.3f - liftA) - 11f * liftAVel) * h;
            liftA += liftAVel * h;
            swayVel += (38f * (clamp(-fwd * 0.02f, -0.7f, 0.7f) - sway) - 5f * swayVel) * h;
            sway += swayVel * h;
        }
        leanS = clamp(leanS, -0.5f, 0.45f);
        leanA = clamp(leanA, -0.6f, 0.5f);

        // ---- Which poses are active.
        boolean upside = Math.cos(in.carAngle) < 0.2;
        boolean wild = in.thrownBack || upside || Math.abs(in.carSpin) > 3.2f;
        boolean calm = !wild && !in.airborne && in.scare <= 0 && !in.victory && !in.defeat;
        wFlail = ease(wFlail, wild ? 1 : 0, dt, wild ? 0.07f : 0.25f);
        wAir = ease(wAir, in.airborne && !wild ? 1 : 0, dt, 0.12f);
        wVictory = ease(wVictory, in.victory ? 1 : 0, dt, 0.2f);
        wSlump = ease(wSlump, in.defeat ? 1 : 0, dt, 0.35f);
        wCheerS = ease(wCheerS, !wild && in.cheer >= 1.5f ? 1 : 0, dt, 0.14f);
        wCheerA = ease(wCheerA, !wild && in.cheer > 0 ? clamp(0.6f + in.cheer * 0.25f, 0, 1) : 0, dt, 0.14f);
        wBrace = ease(wBrace, in.scare > 0 || (in.nitro && !in.airborne) ? 1 : 0, dt, 0.1f);
        wShiver = ease(wShiver, in.freezing ? 1 : 0, dt, 0.2f);
        wPoint = ease(wPoint, calm && in.mobAhead < 13 && in.cheer <= 0 ? 1 : 0, dt, 0.18f);
        wLookBack = ease(wLookBack, calm && in.helperBehind < 9 ? 1 : 0, dt, 0.22f);
        wTalkS = ease(wTalkS, in.steveTalks && calm ? 1 : 0, dt, 0.16f);
        wTalkA = ease(wTalkA, in.alexTalks && calm ? 1 : 0, dt, 0.16f);
        boolean waveNow = calm && in.cheer <= 0 && in.mobAhead > 13 && !in.alexTalks && Math.sin(t * 0.37 + 1.1) > 0.86;
        wWave = ease(wWave, waveNow ? 1 : 0, dt, 0.16f);

        // ---- Driver. Hands follow the wheel; the head keeps level while the body leans.
        float steerTarget = (float) (Math.sin(t * 0.83) * 0.16 + Math.sin(t * 2.1 + 0.7) * 0.07) * clamp(in.speed / 8f, 0.15f, 1f)
                + clamp(in.carSpin * 0.12f, -0.5f, 0.5f);
        steer = ease(steer, steerTarget, dt, 0.12f);
        float shoulderX = CarModel.DRIVER_HIP_X - SHOULDER_UP * (float) Math.sin(leanS);
        float shoulderY = CarModel.DRIVER_HIP_Y + liftS + SHOULDER_UP * (float) Math.cos(leanS);
        float gripX = CarModel.STEER_X - 0.035f, gripY = CarModel.STEER_Y + 0.055f;
        float dx = gripX - shoulderX, dy = gripY - shoulderY;
        float reach = (float) Math.sqrt(dx * dx + dy * dy);
        float aim = (float) Math.atan2(dx, -dy);
        // If the wheel is closer than the arm is long, drop the elbow line a little so the palm still lands on the rim.
        float slack = reach < ARM_REACH ? (float) Math.acos(clamp(reach / ARM_REACH, 0.2f, 1f)) * 0.55f : 0f;
        float wheelArm = -(aim - slack - leanS);
        float[] s = steve.targets;
        s[RX] = wheelArm - steer * 0.32f;
        s[LX] = wheelArm + steer * 0.32f;
        s[RY] = -0.34f;
        s[LY] = 0.34f;
        s[RZ] = s[LZ] = 0;
        s[HX] = -leanS * 0.75f + in.landing * 0.35f;
        s[HY] = (float) (Math.sin(t * 0.41) * 0.14 + Math.sin(t * 1.3 + 2) * 0.05);
        s[HZ] = 0;
        s[LEG_R] = -1.30f + leanS;
        s[LEG_L] = -1.36f + leanS;
        float osc9 = (float) Math.sin(t * 9.0), osc12 = (float) Math.sin(t * 12.0), osc7 = (float) Math.sin(t * 7.0 + 1.3);
        if (wPoint > 0.01f) s[HX] += 0.12f * wPoint;
        mix(s, RX, -1.75f, wTalkS * 0.85f);
        mix(s, RY, -0.9f, wTalkS * 0.85f);
        mix(s, HY, 0.85f, wTalkS);
        mix(s, RX, -3.5f, wCheerS);
        mix(s, RY, 0f, wCheerS);
        mix(s, RX, -3.5f, wVictory);
        mix(s, LX, -3.3f, wVictory);
        mix(s, RY, 0f, wVictory);
        mix(s, LY, 0f, wVictory);
        mix(s, HX, -0.25f, wVictory);
        mix(s, HX, 0.22f - leanS * 0.75f, wBrace * 0.8f);
        mix(s, HX, 0.7f, wSlump);
        mix(s, HX, -0.18f - leanS * 0.75f, wAir);
        mix(s, RX, -2.5f + sway, wFlail);
        mix(s, LX, -2.1f - sway, wFlail);
        mix(s, RY, 0f, wFlail);
        mix(s, LY, 0f, wFlail);
        smooth(steve, dt, snap);
        steve.rightArmX += wCheerS * osc9 * 0.22f + wVictory * osc9 * 0.2f + wFlail * osc12 * 0.5f + wTalkS * osc7 * 0.16f;
        steve.leftArmX += -wVictory * osc9 * 0.2f - wFlail * osc12 * 0.45f;
        steve.rightArmZ += wFlail * osc7 * 0.3f + wShiver * osc12 * 0.05f;
        steve.leftArmZ += -wFlail * osc7 * 0.3f - wShiver * osc12 * 0.05f;
        steve.headX += wTalkS * (float) Math.sin(t * 8.0) * 0.05f + wFlail * osc7 * 0.12f;
        steve.headZ += wShiver * (float) Math.sin(t * 31.0) * 0.04f;
        steve.lean = leanS + wVictory * 0.05f * osc9;
        steve.lift = liftS + wVictory * 0.03f * Math.abs(osc9);

        // ---- Passenger. Hands rest on the knees unless something is going on.
        float[] a = alex.targets;
        a[RX] = -0.62f - leanA * 0.5f + sway * 0.35f;
        a[LX] = -0.62f - leanA * 0.5f + sway * 0.35f;
        a[RY] = -0.12f;
        a[LY] = 0.12f;
        a[RZ] = a[LZ] = 0;
        a[HX] = -leanA * 0.75f + in.landing * 0.4f;
        a[HY] = (float) (Math.sin(t * 0.29 + 1) * 0.55 + Math.sin(t * 0.9) * 0.1);
        a[HZ] = 0;
        a[LEG_R] = -1.42f + leanA;
        a[LEG_L] = -1.48f + leanA;
        mix(a, RX, -1.55f - leanA, wPoint);
        mix(a, RY, -0.05f, wPoint);
        mix(a, HY, 0f, Math.max(wPoint, wBrace));
        mix(a, HY, 1.25f, wLookBack);
        mix(a, HX, 0.1f, wLookBack);
        mix(a, HY, 0.95f, wTalkA);
        mix(a, RX, -1.5f, wTalkA * 0.8f);
        mix(a, RY, -0.85f, wTalkA * 0.8f);
        mix(a, RX, -3.3f, wWave);
        mix(a, RY, -0.5f, wWave);
        mix(a, HY, 0.8f, wWave);
        mix(a, RX, -3.55f, wCheerA);
        mix(a, LX, -3.3f, wCheerA);
        mix(a, HX, -0.2f, wCheerA);
        mix(a, HY, 0.25f, wCheerA);
        mix(a, RX, -2.25f, wBrace);
        mix(a, LX, -2.25f, wBrace);
        mix(a, RZ, -0.45f, wBrace);
        mix(a, LZ, 0.45f, wBrace);
        mix(a, HX, 0.3f - leanA * 0.75f, wBrace);
        mix(a, RX, -3.6f, wAir);
        mix(a, LX, -3.35f, wAir);
        mix(a, HX, -0.25f - leanA * 0.75f, wAir);
        mix(a, LEG_R, -1.15f + leanA, wAir);
        mix(a, LEG_L, -1.7f + leanA, wAir);
        mix(a, RX, -3.55f, wVictory);
        mix(a, LX, -3.3f, wVictory);
        mix(a, HX, -0.3f, wVictory);
        mix(a, RX, -0.2f, wSlump);
        mix(a, LX, -0.2f, wSlump);
        mix(a, HX, 0.75f, wSlump);
        mix(a, HY, 0f, wSlump);
        mix(a, RX, -2.4f + sway, wFlail);
        mix(a, LX, -2.6f - sway, wFlail);
        smooth(alex, dt, snap);
        float osc10 = (float) Math.sin(t * 10.0);
        alex.rightArmX += wCheerA * osc10 * 0.3f + wVictory * osc10 * 0.25f + wAir * osc7 * 0.12f + wFlail * osc12 * 0.55f + wTalkA * osc7 * 0.18f;
        alex.leftArmX += -wCheerA * osc10 * 0.3f - wVictory * osc10 * 0.25f - wAir * osc7 * 0.12f - wFlail * osc9 * 0.55f;
        alex.rightArmZ += wWave * osc12 * 0.4f + wFlail * osc7 * 0.35f + wShiver * osc12 * 0.06f;
        alex.leftArmZ += -wFlail * osc7 * 0.35f - wShiver * osc12 * 0.06f;
        alex.headX += wTalkA * (float) Math.sin(t * 8.5) * 0.05f + wFlail * osc9 * 0.14f;
        alex.headZ += wShiver * (float) Math.sin(t * 29.0) * 0.045f + wCheerA * osc10 * 0.05f;
        alex.rightLeg += wAir * osc9 * 0.18f;
        alex.leftLeg -= wAir * osc9 * 0.18f;
        alex.lean = leanA + (wCheerA * 0.04f + wVictory * 0.05f) * osc10;
        alex.lift = liftA + (wCheerA * 0.035f + wVictory * 0.05f) * Math.abs(osc10);
    }

    private static void mix(float[] target, int joint, float value, float weight) {
        if (weight <= 0.001f) return;
        target[joint] += (value - target[joint]) * Math.min(1f, weight);
    }

    private static void smooth(Pose p, float dt, boolean snap) {
        float[] j = p.joints, t = p.targets;
        if (snap) System.arraycopy(t, 0, j, 0, j.length);
        for (int i = 0; i < j.length; i++) {
            float tau = i >= HX && i <= HZ ? 0.13f : 0.09f;
            j[i] = ease(j[i], t[i], dt, tau);
        }
        p.rightArmX = j[RX];
        p.rightArmY = j[RY];
        p.rightArmZ = j[RZ];
        p.leftArmX = j[LX];
        p.leftArmY = j[LY];
        p.leftArmZ = j[LZ];
        p.headX = j[HX];
        p.headY = j[HY];
        p.headZ = j[HZ];
        p.rightLeg = j[LEG_R];
        p.leftLeg = j[LEG_L];
    }

    /** Writes a pose into a vanilla player model. */
    public static void apply(PlayerModel m, Pose p) {
        for (ModelPart part : m.root().getAllParts()) part.resetPose();
        m.rightArm.xRot = p.rightArmX;
        m.rightArm.yRot = p.rightArmY;
        m.rightArm.zRot = p.rightArmZ;
        m.leftArm.xRot = p.leftArmX;
        m.leftArm.yRot = p.leftArmY;
        m.leftArm.zRot = p.leftArmZ;
        m.head.xRot = clamp(p.headX, -0.9f, 0.9f);
        m.head.yRot = clamp(p.headY, -1.4f, 1.4f);
        m.head.zRot = p.headZ;
        m.rightLeg.xRot = p.rightLeg;
        m.rightLeg.yRot = 0.16f;
        m.leftLeg.xRot = p.leftLeg;
        m.leftLeg.yRot = -0.16f;
    }
}
