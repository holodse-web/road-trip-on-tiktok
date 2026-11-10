package rttt.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
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
import rttt.client.game.Host;
import rttt.client.game.MobDirector;
import rttt.world.Track;

public final class CarRenderer {
    private static final Identifier WHITE = RTTT.id("textures/entity/white.png");
    private static final Identifier BEAM = RTTT.id("textures/entity/beam_add.png");
    private static final Identifier GLOW = RTTT.id("textures/entity/glow.png");
    private static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final Identifier ALEX = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png");

    /** Material of a piece of car geometry; the game maps each one to a render type. */
    public enum Layer { SOLID, GLASS, STEVE, ALEX, BEAM, GLOW }

    public interface Geometry {
        void emit(PoseStack.Pose pose, VertexConsumer vc);
    }

    /** Receives geometry. In the game this queues it for rendering; offline tools rasterise it. */
    public interface Sink {
        void submit(PoseStack ps, Layer layer, Geometry geometry);
    }

    /** One frame of the car, independent of the running game so it can also be drawn offline. */
    public static final class Scene {
        /** Car position relative to the camera, and its pitch. */
        public double x, y, z, angle;
        /** Wheel hub height in car space and wheel rotation, front then rear. */
        public final float[] wheelY = new float[2], wheelSpin = new float[2];
        public int paint = 0xD7263D, light = 0xF000F0;
        /** 0..1 */
        public float brake, lamps, beam, lampGlow;
        public PassengerRig rig;
    }

    private static CarModel model;
    private static PlayerModel steve, alex;
    private static boolean modelsFailed = false;
    private static int lastErrorTick = -1000;
    private static final PassengerRig RIG = new PassengerRig();
    private static final PassengerRig.In RIG_IN = new PassengerRig.In();
    private static final Scene SCENE = new Scene();
    private static final PoseStack PART_STACK = new PoseStack();
    private static RenderType rtSolid, rtGlass, rtSteve, rtAlex, rtBeam, rtGlow, rtPool;
    private static long frameNs = 0;
    private static float poolK = 0, landing = 0, brake = 0;

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

    private static RenderType type(Layer layer) {
        if (rtSolid == null) {
            rtSolid = RenderTypes.entityCutout(WHITE);
            rtGlass = RenderTypes.entityTranslucent(WHITE);
            rtSteve = RenderTypes.entityTranslucent(STEVE);
            rtAlex = RenderTypes.entityTranslucent(ALEX);
            rtBeam = RenderTypes.energySwirl(BEAM, 0f, 0f);
            rtGlow = RenderTypes.entityTranslucent(GLOW);
            rtPool = RenderTypes.energySwirl(WHITE, 0f, 0f);
        }
        return switch (layer) {
            case SOLID -> rtSolid;
            case GLASS -> rtGlass;
            case STEVE -> rtSteve;
            case ALEX -> rtAlex;
            case BEAM -> rtBeam;
            case GLOW -> rtGlow;
        };
    }

    private static void renderInner(Game g, Minecraft mc, SubmitNodeCollector collector, PoseStack ps) {
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CarPhysics car = g.car;
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double x = car.ix(pt), y = car.iy(pt), ang = car.iang(pt);
        double z = Track.CAR_Z;

        long nowNs = System.nanoTime();
        float dt = frameNs == 0 ? 0f : Math.min(0.1f, (nowNs - frameNs) / 1e9f);
        frameNs = nowNs;

        int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(x, y + 0.8, z + 1.2));
        light = LightCoordsUtil.max(light, LightCoordsUtil.pack(7, 0));
        float night = Math.min(1f, mc.level.getSkyDarken() / 9f);
        float k = night > 0.15f ? (night - 0.15f) / 0.85f : 0f;
        boolean shaders = Shaders.active();

        Scene sc = SCENE;
        sc.x = x - camPos.x;
        sc.y = y - camPos.y;
        sc.z = z - camPos.z;
        sc.angle = ang;
        sc.paint = g.cfg.carColor;
        sc.light = light;
        for (int i = 0; i < 2; i++) {
            double wl = car.isus(i, pt);
            sc.wheelY[i] = (float) Math.min(CarModel.WHEEL_MAX_Y, Math.max(CarPhysics.ANCHOR_Y - CarPhysics.L_MAX, CarPhysics.ANCHOR_Y - wl));
            sc.wheelSpin[i] = (float) car.ispin(i, pt);
        }
        boolean braking = car.braking > 0.25 || car.thrownBack() || car.frozen;
        brake += ((braking ? 1f : 0f) - brake) * (1f - (float) Math.exp(-dt / 0.08f));
        sc.brake = Math.max(brake, k * 0.35f);
        sc.lamps = k;
        sc.beam = shaders ? 0f : k * 0.22f;
        sc.lampGlow = k * (shaders ? 0.55f : 0.85f);
        updateRig(g, car, pt, dt);
        sc.rig = RIG;

        ensureModels(mc);
        final SubmitNodeCollector out = collector;
        draw((stack, layer, geometry) -> out.submitCustomGeometry(stack, type(layer), geometry::emit), ps, sc, steve, alex);

        poolK += (k - poolK) * (1f - (float) Math.exp(-dt / 0.6f));
        if (!shaders && Math.cos(ang) > 0.3 && poolK > 0.01f) {
            final double lampX = x + Math.cos(ang) * 2.3;
            final float pk = poolK;
            final int baseX = (int) Math.floor(lampX);
            ps.pushPose();
            ps.translate(baseX - camPos.x, -camPos.y, -camPos.z);
            collector.submitCustomGeometry(ps, rtPool, (pose, vc) -> lightPool(pose, vc, lampX, baseX, pk));
            ps.popPose();
        }

        if (!g.fx.skids.isEmpty()) {
            final int fl = light;
            ps.pushPose();
            ps.translate(-camPos.x, -camPos.y, -camPos.z);
            collector.submitCustomGeometry(ps, rtGlass, (pose, vc) -> skids(pose, vc, g.fx, fl));
            ps.popPose();
        }
    }

    private static void updateRig(Game g, CarPhysics car, float pt, float dt) {
        PassengerRig.In in = RIG_IN;
        in.time = (g.tickCount + pt) / 20.0;
        in.dt = dt;
        double c = Math.cos(car.ang), s = Math.sin(car.ang);
        in.accelForward = (float) (car.ax * c + car.ay * s);
        in.accelUp = (float) (-car.ax * s + car.ay * c);
        in.speed = (float) Math.abs(car.vx);
        in.carAngle = (float) car.ang;
        in.carSpin = (float) car.w;
        in.airborne = car.airTicks > 3;
        in.nitro = car.nitroTime > 0 || car.rocketing();
        in.airborne |= car.rocketing() && car.airTicks > 1;
        in.thrownBack = car.thrownBack();
        in.freezing = g.fx.freezing();
        boolean ending = g.victoryTicks > 0;
        in.victory = ending && !g.defeat;
        in.defeat = ending && g.defeat;
        landing = Math.max(landing - dt * 2.5f, (float) Math.min(1.0, car.landImpact));
        in.landing = landing;
        long since = System.currentTimeMillis() - g.lastChangeTime;
        long amount = g.lastChangeAmount;
        in.cheer = amount > 0 && since < 1700 ? (amount >= 10000 ? 2.5f : amount >= 2000 ? 1.8f : amount >= 300 ? 1f : 0.6f) : 0f;
        in.scare = amount < 0 && since < 1200 ? (amount <= -2000 ? 2f : 1f) : 0f;
        float ahead = 99, behind = 99;
        for (MobDirector.Enc enc : g.mobs.active) {
            double d = enc.mobX() - car.x;
            if (enc.isHelper()) {
                if (d < 0 && -d < behind) behind = (float) -d;
            } else if (d > 0 && d < ahead && !enc.isSpent()) {
                ahead = (float) d;
            }
        }
        in.mobAhead = ahead;
        in.helperBehind = behind;
        Host.Line line = g.cfg.hostEnabled ? g.host.current(System.currentTimeMillis()) : null;
        in.steveTalks = line != null && line.who == Host.Who.STEVE;
        in.alexTalks = line != null && line.who == Host.Who.ALEX;
        RIG.update(in);
    }

    /** Draws the car, its wheels, the passengers and the lamp sprites into {@code sink}. */
    public static void draw(Sink sink, PoseStack ps, Scene sc, PlayerModel steveModel, PlayerModel alexModel) {
        if (model == null || model.paint != sc.paint) model = new CarModel(sc.paint);
        final CarModel m = model;
        final int light = sc.light;
        final float brakeK = sc.brake, lampK = sc.lamps;

        ps.pushPose();
        ps.translate(sc.x, sc.y, sc.z);
        ps.rotate(Axis.ZP, (float) sc.angle);

        sink.submit(ps, Layer.SOLID, (pose, vc) -> m.body.emit(pose, vc, light, false, brakeK, lampK));
        sink.submit(ps, Layer.GLASS, (pose, vc) -> m.body.emit(pose, vc, light, true, brakeK, lampK));

        for (int i = 0; i < 2; i++) {
            final float wx = (float) CarPhysics.WHEEL_X[i], wy = sc.wheelY[i];
            sink.submit(ps, Layer.SOLID, (pose, vc) -> suspension(pose, vc, wx, wy, light));
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.translate(wx, wy, side * CarModel.WHEEL_Z);
                ps.rotate(Axis.ZP, -sc.wheelSpin[i]);
                ps.scale(CarModel.WHEEL_SCALE, CarModel.WHEEL_SCALE, 1f);
                sink.submit(ps, Layer.SOLID, (pose, vc) -> m.wheel.emit(pose, vc, light, false));
                ps.popPose();
            }
        }

        PassengerRig rig = sc.rig;
        ps.pushPose();
        ps.translate(CarModel.STEER_X, CarModel.STEER_Y, 0f);
        if (rig != null) {
            ps.rotate(Axis.ZP, CarModel.STEER_TILT);
            ps.rotate(Axis.XP, rig.steer * 2.2f);
            ps.rotate(Axis.ZP, -CarModel.STEER_TILT);
        }
        sink.submit(ps, Layer.SOLID, (pose, vc) -> m.steering.emit(pose, vc, light, false));
        ps.popPose();

        if (rig != null && steveModel != null && alexModel != null) {
            PassengerRig.apply(steveModel, rig.steve);
            submitPlayer(sink, ps, steveModel, Layer.STEVE, CarModel.DRIVER_HIP_X, CarModel.DRIVER_HIP_Y + rig.steve.lift, rig.steve.lean, light);
            PassengerRig.apply(alexModel, rig.alex);
            submitPlayer(sink, ps, alexModel, Layer.ALEX, CarModel.REAR_HIP_X, CarModel.REAR_HIP_Y + rig.alex.lift, rig.alex.lean, light);
        }

        if (sc.beam > 0.002f) {
            final float a = sc.beam;
            sink.submit(ps, Layer.BEAM, (pose, vc) -> beam(pose, vc, a));
        }
        if (sc.lampGlow > 0.01f) {
            final float a = sc.lampGlow;
            sink.submit(ps, Layer.GLOW, (pose, vc) -> glow(pose, vc, a));
        }
        ps.popPose();
    }

    private static void submitPlayer(Sink sink, PoseStack ps, PlayerModel m, Layer layer, float hipX, float hipY, float lean, int light) {
        final float scale = PassengerRig.SCALE;
        ps.pushPose();
        ps.translate(hipX, hipY, 0f);
        ps.rotate(Axis.ZP, lean);
        ps.translate(0f, -0.75f * scale, 0f);
        ps.rotate(Axis.YP, (float) Math.toRadians(270));
        ps.scale(-scale, -scale, scale);
        ps.translate(0, -1.501f, 0);
        sink.submit(ps, layer, (pose, vc) -> {
            PART_STACK.last().set(pose);
            m.root().render(PART_STACK, vc, light, OverlayTexture.NO_OVERLAY);
        });
        ps.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c, int light) {
        int side = shadeCol(c, 0.82f), bottom = shadeCol(c, 0.6f);
        quad(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c, light, 0, 0, 1);
        quad(pose, vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, c, light, 0, 0, -1);
        quad(pose, vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, c, light, 0, 1, 0);
        quad(pose, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, light, 0, -1, 0);
        quad(pose, vc, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, side, light, 1, 0, 0);
        quad(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, side, light, -1, 0, 0);
    }

    private static int shadeCol(int c, float f) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (a << 24) | ((int) (r * f) << 16) | ((int) (g * f) << 8) | (int) (b * f);
    }

    /** Coil-over strut between the tub and each wheel hub, plus the axle. */
    private static void suspension(PoseStack.Pose pose, VertexConsumer vc, float wx, float wy, int light) {
        float top = 0.02f;
        float len = top - wy;
        if (len <= 0.05f) return;
        int rod = 0xFFB9C2CC, body = 0xFF2A2D33, coilA = 0xFFE23B2E, coilB = 0xFF9E1F17, hub = 0xFF3A3F47;
        for (int side = -1; side <= 1; side += 2) {
            float za = side < 0 ? -0.585f : 0.505f, zb = za + 0.08f;
            box(pose, vc, wx - 0.03f, wy, za + 0.02f, wx + 0.03f, wy + len * 0.55f, zb - 0.02f, rod, light);
            box(pose, vc, wx - 0.06f, wy + len * 0.45f, za + 0.01f, wx + 0.06f, top, zb - 0.01f, body, light);
            int turns = Math.max(3, Math.round(len / 0.07f));
            float coilBottom = wy + len * 0.18f, step = (top - coilBottom) / turns;
            for (int k = 0; k < turns; k++) {
                float y0 = coilBottom + k * step, y1 = y0 + step * 0.55f;
                box(pose, vc, wx - 0.1f, y0, za, wx + 0.1f, y1, zb, (k & 1) == 0 ? coilA : coilB, light);
            }
        }
        box(pose, vc, wx - 0.04f, wy - 0.04f, -0.62f, wx + 0.04f, wy + 0.04f, 0.62f, hub, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int col, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x0, y0, z0).setColor(col).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x1, y1, z1).setColor(col).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(col).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(col).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
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
        float lx = CarModel.LAMP_X + 0.04f, ly = CarModel.LAMP_Y, len = 7.5f;
        float[][] planes = {{CarModel.LAMP_Z + 0.2f, 1f}, {0.15f, 0.55f}};
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

    private static void poolVert(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float i, float nx, float ny) {
        vc.addVertex(pose, x, y, z).setColor(additive(i)).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, 0);
    }

    private static final float[] POOL_Z = {Track.ROAD_Z0 + 0.02f, Track.ROAD_Z0 + 0.9f, Track.ROAD_Z1 + 0.1f, Track.ROAD_Z1 + 0.98f};
    private static final float[] POOL_W = {0.15f, 1.0f, 1.0f, 0.15f};

    private static void lightPool(PoseStack.Pose pose, VertexConsumer vc, double lampX, int baseX, float k) {
        final float peak = 0.24f * k;
        final float[] zs = POOL_Z, zw = POOL_W;
        int from = (int) Math.floor(lampX), to = (int) Math.ceil(lampX + 12.6);
        for (int cx = from; cx <= to; cx++) {
            float y = (float) Track.top(cx) + 0.04f;
            for (int part = 0; part < 2; part++) {
                float xa = cx - baseX + part * 0.5f, xb = xa + 0.5f;
                float ia = poolIntensity(xa + baseX - lampX) * peak, ib = poolIntensity(xb + baseX - lampX) * peak;
                if (ia <= 0.002f && ib <= 0.002f) continue;
                for (int s = 0; s < 3; s++) {
                    float za = zs[s], zb = zs[s + 1];
                    poolVert(pose, vc, xa, y, zb, ia * zw[s + 1], 0, 1);
                    poolVert(pose, vc, xb, y, zb, ib * zw[s + 1], 0, 1);
                    poolVert(pose, vc, xb, y, za, ib * zw[s], 0, 1);
                    poolVert(pose, vc, xa, y, za, ia * zw[s], 0, 1);
                }
            }
            float prev = (float) Track.top(cx - 1) + 0.04f;
            if (y > prev + 0.01f) {
                float i = poolIntensity(cx - lampX) * peak * 1.25f;
                if (i > 0.002f) {
                    float xw = cx - baseX - 0.03f;
                    for (int s = 0; s < 3; s++) {
                        float za = zs[s], zb = zs[s + 1];
                        poolVert(pose, vc, xw, prev, za, i * zw[s], -1, 0);
                        poolVert(pose, vc, xw, prev, zb, i * zw[s + 1], -1, 0);
                        poolVert(pose, vc, xw, y, zb, i * 0.6f * zw[s + 1], -1, 0);
                        poolVert(pose, vc, xw, y, za, i * 0.6f * zw[s], -1, 0);
                    }
                }
            }
        }
    }

    private static void glow(PoseStack.Pose pose, VertexConsumer vc, float a) {
        int c = (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | 0xFFFFFF;
        float cx = CarModel.LAMP_X, cy = CarModel.LAMP_Y, r = 0.42f, zz = CarModel.LAMP_Z + 0.2f;
        vert(pose, vc, cx - r, cy + r, zz, 0f, 0f, c);
        vert(pose, vc, cx - r, cy - r, zz, 0f, 1f, c);
        vert(pose, vc, cx + r, cy - r, zz, 1f, 1f, c);
        vert(pose, vc, cx + r, cy + r, zz, 1f, 0f, c);
    }

    private static void skids(PoseStack.Pose pose, VertexConsumer vc, Effects fx, int light) {
        for (int n = 0, size = fx.skids.size(); n < size; n++) {
            Effects.Skid s = fx.skids.get(n);
            int alpha = (int) (Math.max(0, Math.min(1, s.alpha)) * 170);
            int col = (alpha << 24) | 0x141414;
            float y = (float) s.y + 0.012f;
            float x0 = (float) s.x0 - 0.25f, x1 = (float) s.x1 + 0.1f;
            for (int side = -1; side <= 1; side += 2) {
                float zc = (float) s.zNear + side * CarModel.WHEEL_Z;
                float za = zc - 0.14f, zb = zc + 0.14f;
                quad(pose, vc, x0, y, zb, x1, y, zb, x1, y, za, x0, y, za, col, light, 0, 1, 0);
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

    public static void invalidate() {
        steve = null;
        alex = null;
        modelsFailed = false;
        RIG.reset();
    }
}
