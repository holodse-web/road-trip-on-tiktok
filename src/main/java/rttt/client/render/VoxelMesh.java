package rttt.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Arrays;

/**
 * Static voxel geometry stored as flat arrays. Faces are greedily merged when the mesh is built,
 * so drawing a frame is a single pass over a few hundred quads with no allocation.
 *
 * The alpha byte of a colour selects the material:
 * 0xFF opaque, 0xFE always lit, 0xFD brake lamp, 0xFC headlamp, below 0xF0 translucent.
 */
public final class VoxelMesh {
    public static final int A_GLOW = 0xFE, A_BRAKE = 0xFD, A_LAMP = 0xFC;
    public static final int FRONT = 1, BACK = 2, SIDES = 4, ALL = 7;
    private static final byte M_SOLID = 0, M_GLOW = 1, M_BRAKE = 2, M_LAMP = 3, M_GLASS = 4;
    private static final int STRIDE = 15;

    private float[] data = new float[STRIDE * 256];
    private int[] color = new int[256];
    private byte[] material = new byte[256];
    private int count;

    public boolean isEmpty() {
        return count == 0;
    }

    public int quadCount() {
        return count;
    }

    /** Read access for offline previews: 12 position floats followed by the normal. */
    public float[] quadData(int i) {
        return Arrays.copyOfRange(data, i * STRIDE, i * STRIDE + STRIDE);
    }

    public int quadColor(int i) {
        return color[i];
    }

    private void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float nx, float ny, float nz, int argb) {
        if (count == color.length) {
            data = Arrays.copyOf(data, data.length * 2);
            color = Arrays.copyOf(color, color.length * 2);
            material = Arrays.copyOf(material, material.length * 2);
        }
        int o = count * STRIDE;
        data[o] = x0; data[o + 1] = y0; data[o + 2] = z0;
        data[o + 3] = x1; data[o + 4] = y1; data[o + 5] = z1;
        data[o + 6] = x2; data[o + 7] = y2; data[o + 8] = z2;
        data[o + 9] = x3; data[o + 10] = y3; data[o + 11] = z3;
        data[o + 12] = nx; data[o + 13] = ny; data[o + 14] = nz;
        int a = argb >>> 24;
        byte m = a == A_GLOW ? M_GLOW : a == A_BRAKE ? M_BRAKE : a == A_LAMP ? M_LAMP : a < 0xF0 ? M_GLASS : M_SOLID;
        color[count] = m == M_SOLID || m == M_GLASS ? argb : (argb | 0xFF000000);
        material[count] = m;
        count++;
    }

    public void extrude(int[][] grid, float ox, float oy, float z0, float z1) {
        extrude(grid, ox, oy, z0, z1, 1f / 16f, ALL);
    }

    /**
     * Extrudes a pixel grid (indexed [x][y], 0 = empty) along Z. {@code px} is the size of one pixel,
     * {@code faces} selects which sides of the slab are generated.
     */
    public void extrude(int[][] grid, float ox, float oy, float z0, float z1, float px, int faces) {
        int w = grid.length, h = grid[0].length;
        if ((faces & (FRONT | BACK)) != 0) {
            boolean[][] used = new boolean[w][h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = grid[x][y];
                    if (c == 0 || used[x][y]) continue;
                    int x2 = x;
                    while (x2 + 1 < w && grid[x2 + 1][y] == c && !used[x2 + 1][y]) x2++;
                    int y2 = y;
                    grow:
                    while (y2 + 1 < h) {
                        for (int i = x; i <= x2; i++) if (grid[i][y2 + 1] != c || used[i][y2 + 1]) break grow;
                        y2++;
                    }
                    for (int i = x; i <= x2; i++) for (int j = y; j <= y2; j++) used[i][j] = true;
                    float ax = ox + x * px, bx = ox + (x2 + 1) * px, ay = oy + y * px, by = oy + (y2 + 1) * px;
                    if ((faces & FRONT) != 0) quad(ax, ay, z1, bx, ay, z1, bx, by, z1, ax, by, z1, 0, 0, 1, c);
                    if ((faces & BACK) != 0) quad(bx, ay, z0, ax, ay, z0, ax, by, z0, bx, by, z0, 0, 0, -1, c);
                }
            }
        }
        if ((faces & SIDES) == 0) return;
        // Top and bottom edges, merged along X.
        for (int y = 0; y < h; y++) {
            for (int dir = 0; dir < 2; dir++) {
                int x = 0;
                while (x < w) {
                    int c = edge(grid, x, y, 0, dir == 0 ? 1 : -1);
                    if (c == 0) {
                        x++;
                        continue;
                    }
                    int x2 = x;
                    while (x2 + 1 < w && edge(grid, x2 + 1, y, 0, dir == 0 ? 1 : -1) == c) x2++;
                    float ax = ox + x * px, bx = ox + (x2 + 1) * px;
                    if (dir == 0) {
                        float by = oy + (y + 1) * px;
                        quad(ax, by, z1, bx, by, z1, bx, by, z0, ax, by, z0, 0, 1, 0, c);
                    } else {
                        float ay = oy + y * px;
                        quad(ax, ay, z0, bx, ay, z0, bx, ay, z1, ax, ay, z1, 0, -1, 0, shade(c, 0.6f));
                    }
                    x = x2 + 1;
                }
            }
        }
        // Left and right edges, merged along Y.
        for (int x = 0; x < w; x++) {
            for (int dir = 0; dir < 2; dir++) {
                int y = 0;
                while (y < h) {
                    int c = edge(grid, x, y, dir == 0 ? 1 : -1, 0);
                    if (c == 0) {
                        y++;
                        continue;
                    }
                    int y2 = y;
                    while (y2 + 1 < h && edge(grid, x, y2 + 1, dir == 0 ? 1 : -1, 0) == c) y2++;
                    float ay = oy + y * px, by = oy + (y2 + 1) * px;
                    int sc = shade(c, 0.86f);
                    if (dir == 0) {
                        float bx = ox + (x + 1) * px;
                        quad(bx, ay, z1, bx, ay, z0, bx, by, z0, bx, by, z1, 1, 0, 0, sc);
                    } else {
                        float ax = ox + x * px;
                        quad(ax, ay, z0, ax, ay, z1, ax, by, z1, ax, by, z0, -1, 0, 0, sc);
                    }
                    y = y2 + 1;
                }
            }
        }
    }

    /** Colour of the cell if its neighbour in the given direction is empty, otherwise 0. */
    private static int edge(int[][] g, int x, int y, int dx, int dy) {
        int c = g[x][y];
        if (c == 0) return 0;
        int nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= g.length || ny >= g[0].length) return c;
        return g[nx][ny] == 0 ? c : 0;
    }

    static int shade(int c, float f) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (a << 24) | ((int) (r * f) << 16) | ((int) (g * f) << 8) | (int) (b * f);
    }

    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        int side = shade(c, 0.86f);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, c);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, c);
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, c);
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, shade(c, 0.6f));
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, side);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, side);
    }

    /** Draws without the lamp states (everything dark, as when parked). */
    public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, boolean translucentPass) {
        emit(pose, vc, light, translucentPass, 0f, 0f);
    }

    /**
     * @param brake 0..1 brightness of the brake lamps
     * @param lamps 0..1 brightness of the headlamps
     */
    public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, boolean translucentPass, float brake, float lamps) {
        final float[] d = data;
        for (int q = 0; q < count; q++) {
            byte m = material[q];
            if ((m == M_GLASS) != translucentPass) continue;
            int col = color[q];
            int l = light;
            if (m == M_GLOW) {
                l = 0xF000F0;
            } else if (m == M_BRAKE) {
                col = scale(col, 0.42f + 0.58f * brake);
                if (brake > 0.25f) l = 0xF000F0;
            } else if (m == M_LAMP) {
                col = scale(col, 0.55f + 0.45f * lamps);
                if (lamps > 0.25f) l = 0xF000F0;
            }
            int o = q * STRIDE;
            float nx = d[o + 12], ny = d[o + 13], nz = d[o + 14];
            for (int i = 0; i < 4; i++) {
                int p = o + i * 3;
                vc.addVertex(pose, d[p], d[p + 1], d[p + 2])
                        .setColor(col)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(l)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static int scale(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
