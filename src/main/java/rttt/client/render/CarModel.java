package rttt.client.render;

/**
 * The convertible, built from layered voxel slabs: a narrow tub between four separate mudguards,
 * a hollow cabin with two rows of seats, and wheels with real depth. Everything is in car-local
 * blocks: +X forward, +Y up, Z across the car. One pixel is 1/16 of a block.
 */
public final class CarModel {
    public static final int W = 76, H = 36;
    public static final float OX = -38f / 16f;
    public static final float OY = -16f / 16f;
    public static final int REAR_WHEEL_PX = 16, FRONT_WHEEL_PX = 61;

    public static final float BODY_Z = 0.70f, NARROW_Z = 0.50f, FENDER_Z = 0.93f, WHEEL_Z = 0.74f;
    public static final double ARCH_R_PX = 10.0, ARCH_CY_PX = 8.0, WHEEL_R_PX = 6.8;
    public static final float WHEEL_SCALE = (float) (WHEEL_R_PX / 7.6);
    public static final double WHEEL_MAX_Y = OY + (ARCH_CY_PX + ARCH_R_PX - WHEEL_R_PX - 0.6) / 16.0;

    /** Hip joints of the two seats and the steering wheel, shared with the passenger rig. */
    public static final float DRIVER_HIP_X = -0.125f, DRIVER_HIP_Y = -0.07f;
    public static final float REAR_HIP_X = -1.245f, REAR_HIP_Y = 0.055f;
    public static final float STEER_X = 0.40f, STEER_Y = 0.30f, STEER_R = 0.17f, STEER_TILT = 0.50f;
    /** Lamp centres, used by the glow sprites. */
    public static final float LAMP_X = 2.26f, LAMP_Y = -0.125f, LAMP_Z = 0.71f;

    public final VoxelMesh body = new VoxelMesh();
    public final VoxelMesh wheel = new VoxelMesh();
    /** Steering wheel, drawn separately so it can turn. Local origin is the wheel hub. */
    public final VoxelMesh steering = new VoxelMesh();
    public final int paint;

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    static int mul(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static float x(float px) {
        return OX + px / 16f;
    }

    private static float y(float py) {
        return OY + py / 16f;
    }

    private static int bodyTop(int px) {
        if (px < 3 || px > 73) return -1;
        if (px <= 4) return 13 + (px - 3) * 2;
        if (px <= 26) return 17;
        if (px <= 47) return 16;
        if (px <= 52) return 17;
        if (px <= 70) return 17 - Math.round((px - 52) * 3f / 18f);
        return 13;
    }

    private static int bodyBottom(int px) {
        return px <= 4 || px >= 71 ? 8 : 6;
    }

    private static double archDist(int px, int py, int wheelPx) {
        double dx = px + 0.5 - wheelPx, dy = py + 0.5 - ARCH_CY_PX;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean inArch(int px, int py) {
        return archDist(px, py, REAR_WHEEL_PX) < ARCH_R_PX || archDist(px, py, FRONT_WHEEL_PX) < ARCH_R_PX;
    }

    /** Columns that pass between two wheels are narrower so the wheels have room to move. */
    private static boolean narrow(int px) {
        return Math.abs(px + 0.5 - REAR_WHEEL_PX) < ARCH_R_PX + 0.5 || Math.abs(px + 0.5 - FRONT_WHEEL_PX) < ARCH_R_PX + 0.5;
    }

    private static boolean cabin(int px) {
        return px >= 15 && px <= 49;
    }

    private static int cabinFloor(int px) {
        return px <= 26 ? 12 : 9;
    }

    public CarModel(int paintRgb) {
        this.paint = paintRgb;
        final int P = paintRgb & 0xFFFFFF;
        final int P_DARK = mul(P, 0.66f), P_LIGHT = mul(P, 1.26f), P_MID = mul(P, 0.84f), P_DEEP = mul(P, 0.5f);
        final int CHROME = 0xDDE3EA, CHROME_D = 0x8C96A3, STEEL = 0x5B626C, BLACK = 0x1B1C20, RUBBER = 0x25262B;
        final int CREAM = 0xF5F2EA, WELL = 0x15161A, CARPET = 0x2B2B30, DASH = 0x232429;
        final int LEATHER = 0x9A5F36, LEATHER_L = 0xB57545, LEATHER_D = 0x6A3F22;

        // ---- Tub: wide between the axles, narrow where it passes the wheels.
        int[][] wide = new int[W][H], slim = new int[W][H], wallWide = new int[W][H], wallSlim = new int[W][H];
        for (int px = 0; px < W; px++) {
            int top = bodyTop(px);
            if (top < 0) continue;
            int bottom = bodyBottom(px);
            boolean nar = narrow(px);
            for (int py = bottom; py <= top; py++) {
                boolean arch = inArch(px, py);
                int c;
                if (arch) c = WELL;
                else if (py == top) c = P_LIGHT;
                else if (py <= bottom + 1) c = P_DEEP;
                else if (py <= bottom + 3) c = P_DARK;
                else if (py == 12) c = CREAM;
                else c = P;
                if (!arch) {
                    if ((px == 29 || px == 47) && py > bottom + 1 && py < top && py != 12) c = P_MID;
                    if ((px == 43 || px == 44) && py == 14) c = CHROME;
                    if (px >= 71) c = py % 2 == 0 ? CHROME : STEEL;
                }
                boolean hollow = cabin(px) && py > cabinFloor(px);
                if (hollow) {
                    if (arch) continue;
                    (nar ? wallSlim : wallWide)[px][py] = argb(c);
                } else {
                    if (cabin(px) && py == cabinFloor(px)) c = CARPET;
                    (nar ? slim : wide)[px][py] = argb(c);
                }
            }
        }
        body.extrude(wide, OX, OY, -BODY_Z, BODY_Z);
        body.extrude(slim, OX, OY, -NARROW_Z, NARROW_Z);
        body.extrude(wallWide, OX, OY, BODY_Z - 0.10f, BODY_Z);
        body.extrude(wallWide, OX, OY, -BODY_Z, -BODY_Z + 0.10f);
        body.extrude(wallSlim, OX, OY, NARROW_Z - 0.06f, NARROW_Z);
        body.extrude(wallSlim, OX, OY, -NARROW_Z, -NARROW_Z + 0.06f);
        // Door cards: a leather strip on the inside of the cabin walls.
        int[][] trim = new int[W][H];
        for (int px = 27; px <= 49; px++) for (int py = cabinFloor(px) + 1; py <= bodyTop(px) - 1; py++) trim[px][py] = argb(py >= 14 ? LEATHER : LEATHER_D);
        body.extrude(trim, OX, OY, BODY_Z - 0.13f, BODY_Z - 0.10f, 1f / 16f, VoxelMesh.BACK);
        body.extrude(trim, OX, OY, -BODY_Z + 0.10f, -BODY_Z + 0.13f, 1f / 16f, VoxelMesh.FRONT);

        // ---- Mudguards: a band around each wheel arch, standing proud of the tub.
        int[][] guard = new int[W][H], guardEdge = new int[W][H];
        for (int wheelPx : new int[]{REAR_WHEEL_PX, FRONT_WHEEL_PX}) {
            boolean front = wheelPx == FRONT_WHEEL_PX;
            for (int px = 0; px < W; px++) {
                for (int py = 5; py < H; py++) {
                    double d = archDist(px, py, wheelPx);
                    if (d < ARCH_R_PX || d >= ARCH_R_PX + 3.0) continue;
                    double dx = px + 0.5 - wheelPx;
                    // The outer ends stop above the bumper; the inner ends run down into the running board.
                    boolean outerEnd = front ? dx > 0 : dx < 0;
                    if (outerEnd && py < 9) continue;
                    if (!outerEnd && py < 5) continue;
                    int c = d >= ARCH_R_PX + 2.0 ? P_LIGHT : (d < ARCH_R_PX + 1.0 ? P_DARK : P);
                    guard[px][py] = argb(c);
                    if (d >= ARCH_R_PX + 0.9 && d < ARCH_R_PX + 2.1) guardEdge[px][py] = argb(P);
                }
            }
        }
        body.extrude(guard, OX, OY, NARROW_Z, FENDER_Z);
        body.extrude(guard, OX, OY, -FENDER_Z, -NARROW_Z);
        body.extrude(guardEdge, OX, OY, FENDER_Z, FENDER_Z + 0.035f, 1f / 16f, VoxelMesh.FRONT | VoxelMesh.SIDES);
        body.extrude(guardEdge, OX, OY, -FENDER_Z - 0.035f, -FENDER_Z, 1f / 16f, VoxelMesh.BACK | VoxelMesh.SIDES);

        // ---- Running boards and bumpers.
        for (int side = -1; side <= 1; side += 2) {
            float za = side < 0 ? -FENDER_Z : BODY_Z, zb = side < 0 ? -BODY_Z : FENDER_Z;
            body.box(x(27), y(5), za, x(50), y(6), zb, argb(RUBBER));
            body.box(x(27), y(6), za, x(50), y(6.4f), zb, argb(CHROME_D));
        }
        body.box(x(73), y(8), -0.82f, x(75), y(10.5f), 0.82f, argb(CHROME));
        body.box(x(74.2f), y(7.4f), -0.5f, x(75.4f), y(11.2f), -0.38f, argb(CHROME_D));
        body.box(x(74.2f), y(7.4f), 0.38f, x(75.4f), y(11.2f), 0.5f, argb(CHROME_D));
        body.box(x(1), y(8), -0.82f, x(3), y(10.5f), 0.82f, argb(CHROME));
        body.box(x(0.6f), y(10.6f), -0.26f, x(1.2f), y(12.8f), 0.26f, argb(0xF5C518));
        for (int side = -1; side <= 1; side += 2) {
            body.box(x(0.4f), y(6.4f), side * 0.34f - 0.07f, x(3.2f), y(7.7f), side * 0.34f + 0.07f, argb(CHROME_D));
            body.box(x(0.3f), y(6.7f), side * 0.34f - 0.04f, x(0.5f), y(7.4f), side * 0.34f + 0.04f, argb(BLACK));
        }

        // ---- Lamps. The lens colours carry the brake / headlamp material tags.
        for (int side = -1; side <= 1; side += 2) {
            float za = side * LAMP_Z - 0.17f, zb = side * LAMP_Z + 0.17f;
            body.box(x(71.2f), y(12.3f), za, x(73.7f), y(15.7f), zb, argb(CHROME));
            body.box(x(73.7f), y(12.7f), za + 0.03f, x(74.1f), y(15.3f), zb - 0.03f, (VoxelMesh.A_LAMP << 24) | 0xFFF4C2);
            body.box(x(3.0f), y(12.4f), za, x(4.1f), y(15.6f), zb, (VoxelMesh.A_BRAKE << 24) | 0xFF3B30);
            body.box(x(3.3f), y(11.9f), za - 0.02f, x(4.1f), y(12.4f), zb + 0.02f, argb(CHROME_D));
        }

        // ---- Engine: a polished blower poking through the bonnet.
        body.box(x(56.5f), y(16), -0.24f, x(63.5f), y(20.2f), 0.24f, argb(CHROME));
        body.box(x(57.5f), y(20.2f), -0.19f, x(62.5f), y(21.6f), 0.19f, argb(STEEL));
        body.box(x(58f), y(21.6f), -0.26f, x(62f), y(22.6f), 0.26f, argb(BLACK));
        for (int i = 0; i < 3; i++) body.box(x(57.6f + i * 2f), y(17), 0.24f, x(58.4f + i * 2f), y(19.4f), 0.27f, argb(STEEL));
        for (int i = 0; i < 3; i++) body.box(x(57.6f + i * 2f), y(17), -0.27f, x(58.4f + i * 2f), y(19.4f), -0.24f, argb(STEEL));

        // ---- Windscreen: two posts, a top rail and a pane of glass, raked back.
        for (int py = 18; py <= 27; py++) {
            float fx = 49.6f - (py - 18) * 0.36f;
            for (int side = -1; side <= 1; side += 2) {
                float za = side < 0 ? -BODY_Z : BODY_Z - 0.08f;
                body.box(x(fx), y(py), za, x(fx + 1f), y(py + 1), za + 0.08f, argb(py == 27 ? CHROME : CHROME_D));
            }
            if (py < 27) body.box(x(fx + 0.3f), y(py), -BODY_Z + 0.08f, x(fx + 0.6f), y(py + 1), BODY_Z - 0.08f, 0x4CBFE4F5);
            else body.box(x(fx), y(py), -BODY_Z + 0.08f, x(fx + 1f), y(py + 1), BODY_Z - 0.08f, argb(CHROME));
        }
        for (int side = -1; side <= 1; side += 2) {
            float za = side < 0 ? -BODY_Z - 0.2f : BODY_Z, zb = za + 0.2f;
            body.box(x(48.2f), y(19.2f), za, x(48.8f), y(19.8f), zb, argb(CHROME_D));
            float ma = side < 0 ? -BODY_Z - 0.26f : BODY_Z + 0.1f;
            body.box(x(47.6f), y(18.6f), ma, x(49f), y(20.6f), ma + 0.16f, argb(P));
        }

        // ---- Dashboard and steering column.
        body.box(x(46), y(14), -BODY_Z + 0.1f, x(50), y(17.6f), BODY_Z - 0.1f, argb(DASH));
        body.box(x(45.6f), y(16.2f), -0.42f, x(46f), y(17.2f), -0.2f, (VoxelMesh.A_GLOW << 24) | 0x7FE7FF);
        body.box(x(45.6f), y(16.2f), 0.2f, x(46f), y(17.2f), 0.42f, (VoxelMesh.A_GLOW << 24) | 0xFFB347);
        float sx = STEER_X, sy = STEER_Y;
        for (int i = 1; i <= 4; i++) {
            float t = i / 4f;
            float cx = sx + (x(47.5f) - sx) * t, cy = sy + (y(16.5f) - sy) * t;
            body.box(cx - 0.035f, cy - 0.035f, -0.035f, cx + 0.035f, cy + 0.035f, 0.035f, argb(STEEL));
        }

        // ---- Seats.
        seat(x(32), x(42), y(10), 13, 25, 0.46f, LEATHER, LEATHER_L, LEATHER_D);
        seat(x(14), x(25), y(13), 15, 26, 0.42f, LEATHER, LEATHER_L, LEATHER_D);

        // ---- Folded hood, luggage and an aerial on the boot.
        body.box(x(11.8f), y(18), -0.44f, x(14f), y(20.4f), 0.44f, argb(0x2A2B31));
        body.box(x(12.2f), y(20.4f), -0.4f, x(13.6f), y(21f), 0.4f, argb(0x34363D));
        body.box(x(5.4f), y(18), -0.36f, x(11.4f), y(21.2f), 0.36f, argb(0x7A5230));
        body.box(x(6.8f), y(17.98f), -0.37f, x(7.5f), y(21.25f), 0.37f, argb(0x4A2F1A));
        body.box(x(9.3f), y(17.98f), -0.37f, x(10f), y(21.25f), 0.37f, argb(0x4A2F1A));
        body.box(x(6.4f), y(21.2f), -0.27f, x(10.6f), y(23.6f), 0.27f, argb(0x2E8B8B));
        body.box(x(7.9f), y(23.6f), -0.08f, x(9.1f), y(24.1f), 0.08f, argb(0x1B5959));
        body.box(x(4.5f), y(17.6f), -0.43f, x(4.8f), y(30.5f), -0.40f, argb(CHROME_D));
        body.box(x(4.3f), y(30.5f), -0.46f, x(5f), y(31.2f), -0.37f, argb(0xE23B2E));

        buildSteering(BLACK, CHROME_D);
        buildWheel(P, CHROME, CHROME_D, STEEL, RUBBER);
    }

    /** A bench: base, squab, backrest with a stitched highlight and a headrest. */
    private void seat(float backX, float frontX, float baseY, int squabTopPx, int backTopPx, float halfZ, int leather, int light, int dark) {
        float squabTop = y(squabTopPx), backFront = backX + 2f / 16f;
        body.box(backFront, baseY, -halfZ, frontX, squabTop - 0.09f, halfZ, argb(dark));
        body.box(backFront, squabTop - 0.09f, -halfZ, frontX, squabTop, halfZ, argb(leather));
        body.box(frontX - 0.06f, squabTop - 0.12f, -halfZ, frontX, squabTop + 0.02f, halfZ, argb(light));
        body.box(backX, baseY, -halfZ, backFront, y(backTopPx), halfZ, argb(leather));
        body.box(backX - 0.01f, y(backTopPx) - 0.07f, -halfZ - 0.01f, backFront + 0.01f, y(backTopPx), halfZ + 0.01f, argb(light));
        for (int i = -1; i <= 1; i += 2) body.box(backFront, squabTop + 0.1f, i * halfZ * 0.5f - 0.012f, backFront + 0.012f, y(backTopPx) - 0.1f, i * halfZ * 0.5f + 0.012f, argb(dark));
        body.box(backX + 0.01f, y(backTopPx), -0.16f, backFront - 0.01f, y(backTopPx) + 0.2f, 0.16f, argb(dark));
    }

    private void buildSteering(int rim, int spoke) {
        // The rim lies in the plane spanned by Z and a tilted "up" vector; small cubes read as a ring.
        float ux = -(float) Math.sin(STEER_TILT), uy = (float) Math.cos(STEER_TILT);
        final int n = 14;
        final float s = 0.036f;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float r = STEER_R;
            float px = (float) (Math.sin(a) * r) * ux, py = (float) (Math.sin(a) * r) * uy, pz = (float) (Math.cos(a) * r);
            steering.box(px - s, py - s, pz - s, px + s, py + s, pz + s, argb(rim));
        }
        for (int k = 0; k < 3; k++) {
            double a = Math.PI * 2 * k / 3 + Math.PI / 2;
            for (int j = 1; j <= 2; j++) {
                float r = STEER_R * j / 3f;
                float px = (float) (Math.sin(a) * r) * ux, py = (float) (Math.sin(a) * r) * uy, pz = (float) (Math.cos(a) * r);
                steering.box(px - 0.022f, py - 0.022f, pz - 0.022f, px + 0.022f, py + 0.022f, pz + 0.022f, argb(spoke));
            }
        }
        steering.box(-0.04f, -0.04f, -0.04f, 0.04f, 0.04f, 0.04f, argb(spoke));
    }

    private void buildWheel(int paint, int chrome, int chromeDark, int steel, int rubber) {
        final int n = 24;
        final float px = 1f / n, o = -0.5f;
        int[][] tyre = new int[n][n], disc = new int[n][n], spokes = new int[n][n], cap = new int[n][n];
        for (int gx = 0; gx < n; gx++) {
            for (int gy = 0; gy < n; gy++) {
                double dx = gx + 0.5 - n / 2.0, dy = gy + 0.5 - n / 2.0;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 11.4) continue;
                double a = Math.atan2(dy, dx) + Math.PI;
                if (r > 7.4) {
                    boolean block = ((int) Math.floor(a / (Math.PI * 2) * 20)) % 2 == 0;
                    tyre[gx][gy] = argb(r > 10.2 ? (block ? 0x34353B : rubber) : (r > 8.2 ? 0x1E1F23 : rubber));
                    continue;
                }
                disc[gx][gy] = argb(r > 5.4 ? 0x3F434B : 0x2C2F35);
                if (r > 6.3) spokes[gx][gy] = argb(chrome);
                else if (r > 2.2) {
                    double f = a / (Math.PI * 2) * 5;
                    double off = Math.abs(f - Math.floor(f) - 0.5);
                    if (off < 0.17 + 0.5 / r) spokes[gx][gy] = argb(off < 0.07 ? chrome : chromeDark);
                } else {
                    spokes[gx][gy] = argb(steel);
                    cap[gx][gy] = argb(r < 1.3 ? paint : chrome);
                }
            }
        }
        wheel.extrude(tyre, o, o, -0.17f, 0.17f, px, VoxelMesh.ALL);
        wheel.extrude(disc, o, o, -0.09f, 0.09f, px, VoxelMesh.FRONT | VoxelMesh.BACK);
        wheel.extrude(spokes, o, o, 0.09f, 0.185f, px, VoxelMesh.FRONT | VoxelMesh.SIDES);
        wheel.extrude(spokes, o, o, -0.185f, -0.09f, px, VoxelMesh.BACK | VoxelMesh.SIDES);
        wheel.extrude(cap, o, o, 0.185f, 0.215f, px, VoxelMesh.FRONT | VoxelMesh.SIDES);
        wheel.extrude(cap, o, o, -0.215f, -0.185f, px, VoxelMesh.BACK | VoxelMesh.SIDES);
    }
}
