package rttt.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import rttt.RTTT;
import rttt.client.game.CarPhysics;
import rttt.client.game.Effects;
import rttt.client.game.Game;
import rttt.world.Track;

public final class CarRenderer {
    private static final Identifier WHITE = RTTT.id("textures/entity/white.png");
    private static final Identifier BEAM = RTTT.id("textures/entity/beam_add.png");
    private static final Identifier GLOW = RTTT.id("textures/entity/glow.png");
    private static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final Identifier ALEX = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png");

    private static CarModel model;
    private static PlayerModel steve, alex;
    private static boolean modelsFailed = false;
    private static int lastErrorTick = -1000;

    private CarRenderer() {}

    public static void render(SubmitNodeCollector collector, PoseStack ps) {
        Game g = Game.I;
        if (g == null || !g.active || g.entering) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            renderInner(g, mc, collector, ps);
        } catch (Throwable t) {
            if (g.tickCount - lastErrorTick > 200) {
                lastErrorTick = (int) g.tickCount;
                RTTT.LOG.error("RTTT car render failed", t);
            }
        }
    }

    private static void renderInner(Game g, Minecraft mc, SubmitNodeCollector collector, PoseStack ps) {
        if (model == null || model.paint != g.cfg.carColor) model = new CarModel(g.cfg.carColor);
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CarPhysics car = g.car;
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double x = car.ix(pt), y = car.iy(pt), ang = car.iang(pt);
        double z = Track.CAR_Z;

        int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(x, y + 0.8, z + 1.2));
        light = LightCoordsUtil.max(light, LightCoordsUtil.pack(7, 0));

        ps.pushPose();
        ps.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        ps.rotate(Axis.ZP, (float) ang);

        RenderType solid = RenderTypes.entityCutout(WHITE);
        RenderType translucent = RenderTypes.entityTranslucent(WHITE);
        final int fl = light;
        collector.submitCustomGeometry(ps, solid, (pose, vc) -> model.body.emit(pose, vc, fl, false, 0));
        collector.submitCustomGeometry(ps, translucent, (pose, vc) -> model.body.emit(pose, vc, fl, true, 0));

        for (int i = 0; i < 2; i++) {
            double wl = car.isus(i, pt);
            float wx = (float) CarPhysics.WHEEL_X[i];
            float wy = (float) Math.min(CarModel.WHEEL_MAX_Y, Math.max(CarPhysics.ANCHOR_Y - CarPhysics.L_MAX, CarPhysics.ANCHOR_Y - wl));
            float spin = (float) car.ispin(i, pt);
            final float swx = wx, swy = wy;
            collector.submitCustomGeometry(ps, solid, (pose, vc) -> suspension(pose, vc, swx, swy, fl));
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.translate(wx, wy, side * 0.74f);
                ps.rotate(Axis.ZP, -spin);
                ps.scale(CarModel.WHEEL_SCALE, CarModel.WHEEL_SCALE, 1f);
                collector.submitCustomGeometry(ps, solid, (pose, vc) -> model.wheel.emit(pose, vc, fl, false, 0));
                ps.popPose();
            }
        }

        renderPassengers(g, mc, collector, ps, pt, fl);

        ps.popPose();

        float night = Math.min(1f, mc.level.getSkyDarken() / 9f);
        if (night > 0.15f) {
            float k = (night - 0.15f) / 0.85f;
            boolean shaders = Shaders.active();
            ps.pushPose();
            ps.translate(x - camPos.x, y - camPos.y, z - camPos.z);
            ps.rotate(Axis.ZP, (float) ang);
            if (!shaders) {
                final float a = k * 0.22f;
                collector.submitCustomGeometry(ps, RenderTypes.energySwirl(BEAM, 0f, 0f), (pose, vc) -> beam(pose, vc, a));
            }
            final float ga = k * (shaders ? 0.55f : 0.85f);
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(GLOW), (pose, vc) -> glow(pose, vc, ga));
            ps.popPose();
            long nowNs = System.nanoTime();
            float dtl = poolNs == 0 ? 0f : Math.min(0.1f, (nowNs - poolNs) / 1e9f);
            poolNs = nowNs;
            poolK += (k - poolK) * (1f - (float) Math.exp(-dtl / 0.6f));
            if (!shaders && Math.cos(ang) > 0.3 && poolK > 0.01f) {
                final double lampX = x + Math.cos(ang) * 2.3;
                final float pk = poolK;
                final int baseX = (int) Math.floor(lampX);
                ps.pushPose();
                ps.translate(baseX - camPos.x, -camPos.y, -camPos.z);
                collector.submitCustomGeometry(ps, RenderTypes.energySwirl(WHITE, 0f, 0f), (pose, vc) -> lightPool(pose, vc, lampX, baseX, pk));
                ps.popPose();
            }
        }

        if (!g.fx.skids.isEmpty()) {
            ps.pushPose();
            ps.translate(-camPos.x, -camPos.y, -camPos.z);
            collector.submitCustomGeometry(ps, translucent, (pose, vc) -> skids(pose, vc, g.fx, fl));
            ps.popPose();
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c, int light) {
        int side = shadeCol(c, 0.82f), bottom = shadeCol(c, 0.6f);
        quad(pose, vc, new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, c, light, 0, 0, 1);
        quad(pose, vc, new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, c, light, 0, 0, -1);
        quad(pose, vc, new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, c, light, 0, 1, 0);
        quad(pose, vc, new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, bottom, light, 0, -1, 0);
        quad(pose, vc, new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, side, light, 1, 0, 0);
        quad(pose, vc, new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, side, light, -1, 0, 0);
    }

    private static int shadeCol(int c, float f) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (a << 24) | ((int) (r * f) << 16) | ((int) (g * f) << 8) | (int) (b * f);
    }

    /** Coil-over strut from the wheel hub up into the arch, plus the axle stub, on both sides. */
    private static void suspension(PoseStack.Pose pose, VertexConsumer vc, float wx, float wy, int light) {
        float top = 0.02f;
        float len = top - wy;
        if (len <= 0.05f) return;
        int rod = 0xFFB9C2CC, body = 0xFF2A2D33, coilA = 0xFFE23B2E, coilB = 0xFF9E1F17, hub = 0xFF3A3F47;
        for (int side = -1; side <= 1; side += 2) {
            float za = side < 0 ? -0.80f : 0.60f, zb = side < 0 ? -0.60f : 0.80f;
            box(pose, vc, wx - 0.035f, wy, za + 0.06f, wx + 0.035f, wy + len * 0.55f, zb - 0.06f, rod, light);
            box(pose, vc, wx - 0.07f, wy + len * 0.45f, za + 0.03f, wx + 0.07f, top, zb - 0.03f, body, light);
            int turns = Math.max(3, Math.round(len / 0.07f));
            float coilBottom = wy + len * 0.18f, step = (top - coilBottom) / turns;
            for (int k = 0; k < turns; k++) {
                float y0 = coilBottom + k * step, y1 = y0 + step * 0.55f;
                box(pose, vc, wx - 0.11f, y0, za, wx + 0.11f, y1, zb, (k & 1) == 0 ? coilA : coilB, light);
            }
            box(pose, vc, wx - 0.09f, wy - 0.09f, side < 0 ? -0.62f : 0.40f, wx + 0.09f, wy + 0.09f, side < 0 ? -0.40f : 0.62f, hub, light);
        }
        box(pose, vc, wx - 0.04f, wy - 0.04f, -0.62f, wx + 0.04f, wy + 0.04f, 0.62f, hub, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float[] v, int col, int light, float nx, float ny, float nz) {
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(col).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int col) {
        vc.addVertex(pose, x, y, z).setColor(col).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
    }

    private static int additive(float intensity) {
        float i = Math.max(0f, Math.min(1f, intensity));
        int r = Math.round(255 * i), g = Math.round(236 * i), b = Math.round(196 * i);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void beam(PoseStack.Pose pose, VertexConsumer vc, float a) {
        float lx = 2.30f, ly = -0.08f, len = 7.5f;
        float[][] planes = {{0.95f, 1f}, {0.15f, 0.55f}};
        for (float[] pl : planes) {
            float zz = pl[0];
            int c = additive(a * pl[1]);
            vert(pose, vc, lx, ly + 0.07f, zz, 0f, 0f, c);
            vert(pose, vc, lx, ly - 0.07f, zz, 0f, 1f, c);
            vert(pose, vc, lx + len, ly - 1.35f, zz, 1f, 1f, c);
            vert(pose, vc, lx + len, ly + 0.55f, zz, 1f, 0f, c);
        }
    }

    private static float poolIntensity(double d) {
        if (d <= 0) return 0f;
        double rise = Math.min(1.0, d / 2.5);
        double fall = d < 4 ? 1.0 : Math.max(0.0, 1.0 - (d - 4) / 8.5);
        return (float) (rise * rise * (3 - 2 * rise) * fall * fall);
    }

    private static void lightPool(PoseStack.Pose pose, VertexConsumer vc, double lampX, int baseX, float k) {
        final float peak = 0.24f * k;
        final float[] zs = {Track.ROAD_Z0 + 0.02f, Track.ROAD_Z0 + 0.9f, Track.ROAD_Z1 + 0.1f, Track.ROAD_Z1 + 0.98f};
        final float[] zw = {0.15f, 1.0f, 1.0f, 0.15f};
        int from = (int) Math.floor(lampX), to = (int) Math.ceil(lampX + 12.6);
        for (int cx = from; cx <= to; cx++) {
            float y = (float) Track.top(cx) + 0.04f;
            for (int part = 0; part < 2; part++) {
                float xa = cx - baseX + part * 0.5f, xb = xa + 0.5f;
                float ia = poolIntensity(xa + baseX - lampX) * peak, ib = poolIntensity(xb + baseX - lampX) * peak;
                if (ia <= 0.002f && ib <= 0.002f) continue;
                for (int s = 0; s < 3; s++) {
                    float za = zs[s], zb = zs[s + 1];
                    vc.addVertex(pose, xa, y, zb).setColor(additive(ia * zw[s + 1])).setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, xb, y, zb).setColor(additive(ib * zw[s + 1])).setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, xb, y, za).setColor(additive(ib * zw[s])).setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, xa, y, za).setColor(additive(ia * zw[s])).setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
                }
            }
            float prev = (float) Track.top(cx - 1) + 0.04f;
            if (y > prev + 0.01f) {
                float i = poolIntensity(cx - lampX) * peak * 1.25f;
                if (i > 0.002f) {
                    float xw = cx - baseX - 0.03f;
                    for (int s = 0; s < 3; s++) {
                        float za = zs[s], zb = zs[s + 1];
                        vc.addVertex(pose, xw, prev, za).setColor(additive(i * zw[s])).setUv(0.5f, 0.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, -1, 0, 0);
                        vc.addVertex(pose, xw, prev, zb).setColor(additive(i * zw[s + 1])).setUv(0.5f, 0.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, -1, 0, 0);
                        vc.addVertex(pose, xw, y, zb).setColor(additive(i * 0.6f * zw[s + 1])).setUv(0.5f, 0.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, -1, 0, 0);
                        vc.addVertex(pose, xw, y, za).setColor(additive(i * 0.6f * zw[s])).setUv(0.5f, 0.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, -1, 0, 0);
                    }
                }
            }
        }
    }

    private static void glow(PoseStack.Pose pose, VertexConsumer vc, float a) {
        int c = (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | 0xFFFFFF;
        float cx = 2.22f, cy = -0.06f, r = 0.42f, zz = 0.97f;
        vert(pose, vc, cx - r, cy + r, zz, 0f, 0f, c);
        vert(pose, vc, cx - r, cy - r, zz, 0f, 1f, c);
        vert(pose, vc, cx + r, cy - r, zz, 1f, 1f, c);
        vert(pose, vc, cx + r, cy + r, zz, 1f, 0f, c);
    }

    private static void skids(PoseStack.Pose pose, VertexConsumer vc, Effects fx, int light) {
        for (Effects.Skid s : fx.skids) {
            int alpha = (int) (Math.max(0, Math.min(1, s.alpha)) * 170);
            int col = (alpha << 24) | 0x141414;
            float y = (float) s.y + 0.012f;
            float x0 = (float) s.x0 - 0.25f, x1 = (float) s.x1 + 0.1f;
            float[] zs = {(float) s.zNear + 0.55f, (float) s.zNear - 0.95f};
            for (float zc : zs) {
                float za = zc - 0.12f, zb = zc + 0.12f;
                quad(pose, vc, new float[]{x0, y, zb, x1, y, zb, x1, y, za, x0, y, za}, col, light, 0, 1, 0);
            }
        }
    }

    private static void ensureModels(Minecraft mc) {
        if ((steve != null && alex != null) || modelsFailed) return;
        try {
            steve = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            alex = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        } catch (Throwable t) {
            modelsFailed = true;
            RTTT.LOG.error("Could not bake player models", t);
        }
    }

    private static void reset(PlayerModel m) {
        for (ModelPart p : m.root().getAllParts()) p.resetPose();
    }

    private static void sitPose(PlayerModel m) {
        m.rightLeg.xRot = -1.4137167f;
        m.rightLeg.yRot = 0.31415927f;
        m.rightLeg.zRot = 0.07853982f;
        m.leftLeg.xRot = -1.4137167f;
        m.leftLeg.yRot = -0.31415927f;
        m.leftLeg.zRot = -0.07853982f;
    }

    private static long animNs = 0, poolNs = 0;
    private static float poolK = 0;
    private static float steveVictory, steveScared, alexCheerW, alexScared, alexWave, steveLeanTarget, lookSmooth;

    private static void renderPassengers(Game g, Minecraft mc, SubmitNodeCollector collector, PoseStack ps, float pt, int light) {
        ensureModels(mc);
        if (steve == null || alex == null) return;
        double time = (g.tickCount + pt) / 20.0;
        CarPhysics car = g.car;
        boolean victory = g.victoryTicks > 0;
        long sinceChange = System.currentTimeMillis() - g.lastChangeTime;
        boolean recentPlus = g.lastChangeAmount > 0 && sinceChange < 1500;
        boolean recentMinus = g.lastChangeAmount < 0 && sinceChange < 1100;
        double lookAhead = 0;
        double near = 99;
        for (var enc : g.mobs.active) {
            double d = enc.mobX() - car.x;
            if (d > 0 && d < near) near = d;
        }
        if (near < 14) lookAhead = Math.min(0.5, (14 - near) / 20.0);

        long nowNs = System.nanoTime();
        float dt = animNs == 0 ? 0f : Math.min(0.1f, (nowNs - animNs) / 1e9f);
        animNs = nowNs;
        float k = 1f - (float) Math.exp(-dt / 0.22f);
        boolean alexCheer = victory || recentPlus;
        steveVictory += ((victory ? 1f : 0f) - steveVictory) * k;
        steveScared += ((!victory && recentMinus ? 1f : 0f) - steveScared) * k;
        alexCheerW += ((alexCheer ? 1f : 0f) - alexCheerW) * k;
        alexScared += ((!alexCheer && recentMinus ? 1f : 0f) - alexScared) * k;
        alexWave += ((!alexCheer && !recentMinus && Math.sin(time * 0.45) > 0.85 ? 1f : 0f) - alexWave) * k;
        steveLeanTarget += ((Math.abs(car.vx) > 12 ? -0.1f : 0f) - steveLeanTarget) * k;
        lookSmooth += ((float) lookAhead - lookSmooth) * k;

        reset(steve);
        sitPose(steve);
        {
            float sv = steveVictory, sm = steveScared, si = Math.max(0f, 1f - sv - sm);
            float osc = (float) Math.sin(time * 3.1) * 0.05f;
            steve.rightArm.xRot = si * (-0.95f + osc) + sv * (float) (-2.9 + Math.sin(time * 9) * 0.25) + sm * -2.6f;
            steve.leftArm.xRot = si * (-0.95f - osc) + sv * -0.95f + sm * -2.6f;
            steve.rightArm.yRot = si * -0.15f;
            steve.leftArm.yRot = si * 0.15f;
            steve.head.yRot = si * (float) (Math.sin(time * 0.7) * 0.15 + lookSmooth * -0.6);
            steve.head.xRot = si * steveLeanTarget + sm * 0.25f;
        }
        reset(alex);
        sitPose(alex);
        {
            float ac = alexCheerW, as = alexScared, aw = alexWave * Math.max(0f, 1f - ac - as), ai = Math.max(0f, 1f - ac - as - aw);
            float cheer = (float) Math.sin(time * 10) * 0.3f;
            alex.rightArm.xRot = ai * -0.3f + aw * -2.8f + ac * (-2.9f + cheer) + as * -2.2f;
            alex.leftArm.xRot = (ai + aw) * -0.3f + ac * (-2.9f - cheer) + as * -2.2f;
            alex.rightArm.zRot = aw * (float) (Math.sin(time * 12) * 0.35) + as * 0.4f;
            alex.leftArm.zRot = as * -0.4f;
            alex.head.xRot = ac * -0.2f;
            alex.head.yRot = (ai + aw) * (float) (Math.sin(time * 0.5 + 1) * 0.5 + lookSmooth * -0.6);
        }
        double bob = (car.isus(0, pt) + car.isus(1, pt)) * 0.5 - 0.50;
        submitPlayer(collector, ps, steve, STEVE, -0.06f, (float) (-0.1775 + bob * 0.3), 0.0f, light);
        submitPlayer(collector, ps, alex, ALEX, -1.19f, (float) (-0.095 + bob * 0.3), 0.0f, light);
    }

    private static void submitPlayer(SubmitNodeCollector collector, PoseStack ps, PlayerModel m, Identifier tex,
                                     float hipX, float hipY, float hipZ, int light) {
        float scale = 0.9375f;
        ps.pushPose();
        ps.translate(hipX, hipY - 0.75f * scale, hipZ);
        ps.rotate(Axis.YP, (float) Math.toRadians(180 - (-90)));
        ps.scale(-scale, -scale, scale);
        ps.translate(0, -1.501f, 0);
        collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(tex), (pose, vc) -> {
            PoseStack local = new PoseStack();
            local.last().set(pose);
            m.root().render(local, vc, light, OverlayTexture.NO_OVERLAY);
        });
        ps.popPose();
    }

    public static void invalidate() {
        steve = null;
        alex = null;
        modelsFailed = false;
    }

}
