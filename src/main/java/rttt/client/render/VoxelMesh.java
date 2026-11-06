package rttt.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.ArrayList;
import java.util.List;

public final class VoxelMesh {
    static final class Quad {
        final float[] v = new float[12];
        float nx, ny, nz;
        int argb;
        boolean glow;
    }

    final List<Quad> quads = new ArrayList<>();

    public boolean isEmpty() {
        return quads.isEmpty();
    }

    private void quad(float[] v, float nx, float ny, float nz, int argb, boolean glow) {
        Quad q = new Quad();
        System.arraycopy(v, 0, q.v, 0, 12);
        q.nx = nx;
        q.ny = ny;
        q.nz = nz;
        q.argb = argb;
        q.glow = glow;
        quads.add(q);
    }

    public void extrude(int[][] grid, float ox, float oy, float z0, float z1, int glowColorMask) {
        int w = grid.length, h = grid[0].length;
        final float s = 1f / 16f;
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                int c = grid[x][y];
                if (c == 0) {
                    x++;
                    continue;
                }
                int x2 = x;
                while (x2 + 1 < w && grid[x2 + 1][y] == c) x2++;
                float ax = ox + x * s, bx = ox + (x2 + 1) * s, ay = oy + y * s, by = oy + (y + 1) * s;
                boolean glow = isGlow(c);
                quad(new float[]{ax, ay, z1, bx, ay, z1, bx, by, z1, ax, by, z1}, 0, 0, 1, c, glow);
                quad(new float[]{bx, ay, z0, ax, ay, z0, ax, by, z0, bx, by, z0}, 0, 0, -1, c, glow);
                x = x2 + 1;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int c = grid[x][y];
                if (c == 0) continue;
                boolean glow = isGlow(c);
                float ax = ox + x * s, bx = ox + (x + 1) * s, ay = oy + y * s, by = oy + (y + 1) * s;
                int shadeTop = c, shadeSide = shade(c, 0.86f), shadeBottom = shade(c, 0.6f);
                if (y + 1 >= h || grid[x][y + 1] == 0)
                    quad(new float[]{ax, by, z1, bx, by, z1, bx, by, z0, ax, by, z0}, 0, 1, 0, shadeTop, glow);
                if (y == 0 || grid[x][y - 1] == 0)
                    quad(new float[]{ax, ay, z0, bx, ay, z0, bx, ay, z1, ax, ay, z1}, 0, -1, 0, shadeBottom, glow);
                if (x + 1 >= w || grid[x + 1][y] == 0)
                    quad(new float[]{bx, ay, z1, bx, ay, z0, bx, by, z0, bx, by, z1}, 1, 0, 0, shadeSide, glow);
                if (x == 0 || grid[x - 1][y] == 0)
                    quad(new float[]{ax, ay, z0, ax, ay, z1, ax, by, z1, ax, by, z0}, -1, 0, 0, shadeSide, glow);
            }
        }
    }

    static boolean isGlow(int c) {
        return (c >>> 24) == 0xFE;
    }

    static int shade(int c, float f) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (a << 24) | ((int) (r * f) << 16) | ((int) (g * f) << 8) | (int) (b * f);
    }

    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        boolean glow = isGlow(c);
        quad(new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, 0, 0, 1, c, glow);
        quad(new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, 0, 0, -1, c, glow);
        quad(new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, 0, 1, 0, c, glow);
        quad(new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, 0, -1, 0, shade(c, 0.6f), glow);
        quad(new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, 1, 0, 0, shade(c, 0.86f), glow);
        quad(new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, -1, 0, 0, shade(c, 0.86f), glow);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, boolean translucentPass, int tintArgb) {
        for (Quad q : quads) {
            int a = q.argb >>> 24;
            boolean translucent = a < 0xF0;
            if (translucent != translucentPass) continue;
            int col = q.argb;
            if (tintArgb != 0 && (col & 0x00FFFFFF) == 0x00FF00FF) col = (col & 0xFF000000) | (tintArgb & 0xFFFFFF);
            if (q.glow) col = col | 0xFF000000;
            int l = q.glow ? 0xF000F0 : light;
            for (int i = 0; i < 4; i++) {
                vc.addVertex(pose, q.v[i * 3], q.v[i * 3 + 1], q.v[i * 3 + 2])
                        .setColor(col)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(l)
                        .setNormal(pose, q.nx, q.ny, q.nz);
            }
        }
    }
}
