package rttt.client.render;

import java.util.Random;

public final class CarModel {
    public static final int W = 76, H = 34;
    public static final float OX = -38f / 16f;
    public static final float OY = -16f / 16f;
    public static final int REAR_WHEEL_PX = 16, FRONT_WHEEL_PX = 61;

    public static final float BODY_Z = 0.86f;
    public static final double ARCH_R_PX = 10.0, ARCH_CY_PX = 8.0, WHEEL_R_PX = 6.8;
    public static final float WHEEL_SCALE = (float) (WHEEL_R_PX / 7.6);
    public static final double WHEEL_MAX_Y = OY + (ARCH_CY_PX + ARCH_R_PX - WHEEL_R_PX - 0.6) / 16.0;

    public final VoxelMesh body = new VoxelMesh();
    public final VoxelMesh wheel = new VoxelMesh();
    public final int paint;

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int mul(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    public CarModel(int paintRgb) {
        this.paint = paintRgb;
        Random rnd = new Random(1234);
        int P = paintRgb & 0xFFFFFF;
        int P_DARK = mul(P, 0.68f), P_LIGHT = mul(P, 1.28f), P_MID = mul(P, 0.85f);
        int CHROME = 0xDDE3EA, CHROME_D = 0x8C96A3, BLACK = 0x1E1F24, STRIPE = 0xF5F2EA;
        int HEAD = 0xFFF4C2, TAIL = 0xFF3B30, LEATHER = 0x8A5530, LEATHER_D = 0x5C361E, INTERIOR = 0x2B2B30;
        int PLATE = 0xF5C518;

        int[][] g = new int[W][H];
        for (int px = 0; px < W; px++) {
            int bottom = (px <= 4 || px >= 71) ? 7 : 6;
            int top;
            if (px <= 3) top = 14;
            else if (px <= 10) top = 16 + (px - 4) / 2;
            else if (px <= 24) top = 18;
            else if (px <= 52) top = 16;
            else if (px <= 70) top = 16 - (px - 53) / 6;
            else top = 13;
            top = Math.max(top, fender(px, REAR_WHEEL_PX));
            top = Math.max(top, fender(px, FRONT_WHEEL_PX));
            for (int py = bottom; py <= top; py++) {
                if (inArch(px, py, REAR_WHEEL_PX) || inArch(px, py, FRONT_WHEEL_PX)) continue;
                int c;
                if (px <= 3 || px >= 71) c = (py == bottom || py == top) ? CHROME_D : CHROME;
                else if (py == top) c = P_LIGHT;
                else if (py <= bottom + 1) c = P_DARK;
                else if (py == 11) c = STRIPE;
                else c = P;
                if (nearArch(px, py, REAR_WHEEL_PX) || nearArch(px, py, FRONT_WHEEL_PX)) c = CHROME_D;
                if ((px == 30 || px == 47) && py >= bottom + 2 && py <= top - 1 && py != 11) c = P_MID;
                if (px >= 43 && px <= 45 && py == 14) c = CHROME;
                if (px >= 26 && px <= 28 && py == 14) c = CHROME;
                if (px >= 69 && px <= 70 && (py == 14 || py == 15)) c = HEAD | 0xFE000000;
                if (px >= 4 && px <= 5 && py >= 13 && py <= 15) c = TAIL | 0xFE000000;
                if (px == 0 && py >= 9 && py <= 11) c = PLATE;
                if (c != CHROME && c != CHROME_D && (c >>> 24) == 0) {
                    float n = 0.94f + rnd.nextFloat() * 0.1f;
                    c = mul(c, n);
                }
                g[px][py] = (c >>> 24) == 0 ? argb(c) : c;
            }
        }
        body.extrude(g, OX, OY, -BODY_Z, BODY_Z, 0);

        int[][] ws = new int[W][H];
        for (int i = 0; i <= 9; i++) {
            int py = 17 + i;
            int px = 53 - i / 3;
            ws[px][py] = argb(CHROME);
            ws[px - 1][py] = i < 9 ? 0x9AA8D8F0 : argb(CHROME);
        }
        for (int px = 49; px <= 53; px++) ws[px][26] = argb(CHROME);
        mask(ws, g);
        body.extrude(ws, OX, OY, -0.78f, 0.78f, 0);

        int[][] seats = new int[W][H];
        int[] seatBackX = {32, 14};
        for (int sb : seatBackX) {
            int floor = top(g, sb + 6) + 1;
            for (int py = floor; py <= floor + 8; py++) {
                for (int px = sb; px <= sb + 2; px++) seats[px][py] = argb(py >= floor + 7 ? LEATHER_D : LEATHER);
            }
            for (int px = sb + 3; px <= sb + 10; px++) seats[px][floor] = argb(LEATHER);
        }
        for (int px = 46; px <= 50; px++) for (int py = 15; py <= 18; py++) seats[px][py] = argb(INTERIOR);
        mask(seats, g);
        body.extrude(seats, OX, OY, -0.7f, 0.7f, 0);
        int[][] steer = new int[W][H];
        for (int py = 18; py <= 21; py++) steer[45][py] = argb(BLACK);
        steer[44][21] = argb(BLACK);
        steer[46][18] = argb(BLACK);
        mask(steer, g);
        mask(steer, seats);
        body.extrude(steer, OX, OY, -0.12f, 0.12f, 0);

        int[][] bag = new int[W][H];
        for (int px = 4; px <= 10; px++) for (int py = 19; py <= 22; py++) bag[px][py] = argb(px == 7 ? 0x3A2A1A : 0x6B4A2E);
        for (int px = 6; px <= 8; px++) bag[px][23] = argb(0x3A2A1A);
        mask(bag, g);
        mask(bag, seats);
        body.extrude(bag, OX, OY, -0.45f, 0.45f, 0);

        int[][] wg = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                double dx = x - 7.5, dy = y - 7.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 7.6) continue;
                double a = Math.atan2(dy, dx);
                int c;
                if (r > 5.6) {
                    boolean tread = ((int) Math.floor((a + Math.PI) / (Math.PI * 2) * 16)) % 2 == 0;
                    c = r > 6.9 && tread ? 0x3B3B40 : 0x202024;
                } else if (r > 4.6) {
                    c = 0xE8ECF0;
                } else if (r > 1.6) {
                    int spoke = (int) Math.floor((a + Math.PI) / (Math.PI * 2) * 10);
                    c = spoke % 2 == 0 ? 0xC9D1D9 : 0x6E7884;
                } else {
                    c = P;
                }
                wg[x][y] = argb(c);
            }
        }
        wheel.extrude(wg, -0.5f, -0.5f, -0.16f, 0.16f, 0);
    }

    private static boolean inArch(int px, int py, int wheelPx) {
        double dx = px + 0.5 - wheelPx, dy = py + 0.5 - ARCH_CY_PX;
        return dx * dx + dy * dy < ARCH_R_PX * ARCH_R_PX && py < ARCH_CY_PX + ARCH_R_PX;
    }

    private static int fender(int px, int wheelPx) {
        double d = Math.abs(px + 0.5 - wheelPx);
        int peak = (int) Math.ceil(ARCH_CY_PX + ARCH_R_PX) + 1;
        if (d <= ARCH_R_PX - 2) return peak;
        if (d <= ARCH_R_PX + 3) return peak - (int) Math.round((d - (ARCH_R_PX - 2)) * 0.6);
        return 0;
    }

    private static boolean nearArch(int px, int py, int wheelPx) {
        double dx = px + 0.5 - wheelPx, dy = py + 0.5 - ARCH_CY_PX;
        double d = Math.sqrt(dx * dx + dy * dy);
        return d >= ARCH_R_PX && d < ARCH_R_PX + 1.1;
    }

    private static int top(int[][] g, int px) {
        for (int py = g[px].length - 1; py >= 0; py--) if (g[px][py] != 0) return py;
        return 0;
    }

    private static void mask(int[][] layer, int[][] under) {
        for (int x = 0; x < layer.length; x++)
            for (int y = 0; y < layer[x].length; y++)
                if (under[x][y] != 0) layer[x][y] = 0;
    }
}
