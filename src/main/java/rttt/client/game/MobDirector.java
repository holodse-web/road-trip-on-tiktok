package rttt.client.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rttt.client.mixin.FallingBlockAccess;
import rttt.client.mixin.LivingSoundAccess;
import rttt.client.mixin.MobSoundAccess;
import rttt.world.Palette;
import rttt.world.Track;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class MobDirector {
    private final Game game;
    private final Random rnd = new Random();
    private int idCounter = -2_000_000;
    public final List<Enc> active = new ArrayList<>();
    private final List<Temp> temps = new ArrayList<>();

    public static final double DT = 0.05;

    public MobDirector(Game game) {
        this.game = game;
    }

    public int nextId() {
        return idCounter--;
    }

    public static boolean isManaged(int id) {
        return id <= -1_999_990;
    }

    public int activeCount() {
        int n = 0;
        for (Enc e : active) if (!e.hit) n++;
        return n;
    }

    public final class Enc {
        final MobCatalog.Entry e;
        final String user;
        LivingEntity mob;
        int age = 0, phase = 0, timer = 0;
        boolean hit = false;
        double x, y, z, vx, vy, spinYaw;
        double sx, sy, cx, cy;
        double half = 0.4, height = 1.8;
        float yaw = 90f;
        Entity proj;
        double pt, pdur, p0x, p0y, p0z;
        int shots = 0;
        boolean removed = false;

        Enc(MobCatalog.Entry e, String user) {
            this.e = e;
            this.user = user;
        }

        public double mobX() {
            return x;
        }

        public boolean isBoss() {
            return e.size == MobCatalog.Size.BOSS;
        }

        public boolean isHelper() {
            return e.helper;
        }

        /** True once the mob has already struck the car and is just leaving. */
        public boolean isSpent() {
            return hit;
        }
    }

    private static final class Temp {
        Entity ent;
        int life;
    }

    private ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static Identifier mc(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private Entity create(String typeId) {
        ClientLevel l = level();
        if (l == null) return null;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(mc(typeId));
            if (type == null) return null;
            Entity ent = type.create(l, EntitySpawnReason.TRIGGERED);
            if (ent == null) return null;
            ent.setId(nextId());
            ent.setNoGravity(true);
            return ent;
        } catch (Throwable t) {
            return null;
        }
    }

    private void place(Entity ent, double x, double y, double z, float yaw, float pitch) {
        ent.snapTo(x, y, z, yaw, pitch);
        ent.setOldPosAndRot();
        if (ent instanceof LivingEntity le) {
            le.yBodyRot = le.yBodyRotO = yaw;
            le.yHeadRot = le.yHeadRotO = yaw;
        }
    }

    private void addToLevel(Entity ent) {
        ClientLevel l = level();
        if (l != null) l.addEntity(ent);
    }

    public void trackTemp(Entity ent, int life) {
        Temp t = new Temp();
        t.ent = ent;
        t.life = life;
        temps.add(t);
    }

    private void removeEntity(Entity ent) {
        if (ent == null) return;
        ClientLevel l = level();
        try {
            if (l != null) l.removeEntity(ent.getId(), Entity.RemovalReason.DISCARDED);
            else ent.discard();
        } catch (Throwable ignored) {
        }
    }

    private void move(Entity ent, double x, double y, double z) {
        ent.setPos(x, y, z);
    }

    private void face(LivingEntity le, float yaw) {
        le.setYRot(yaw);
        le.yBodyRot = yaw;
        le.yHeadRot = yaw;
    }

    private void walkAnim(LivingEntity le, double dist) {
        try {
            le.walkAnimation.update((float) Math.min(1.0, dist * 4.0), 0.4f, 1f);
        } catch (Throwable ignored) {
        }
    }

    private double ground(double x) {
        return Track.topAt(x);
    }

    private double carTargetY() {
        return game.car.y + 0.25;
    }

    void playMobSound(LivingEntity le, int which, float vol) {
        try {
            SoundEvent s = null;
            if (which == 0 && le instanceof Mob m) s = ((MobSoundAccess) m).rttt$ambient();
            else if (which == 1) s = ((LivingSoundAccess) le).rttt$hurt(le.level().damageSources().generic());
            else if (which == 2) s = ((LivingSoundAccess) le).rttt$death();
            if (s != null) {
                float pitch = 0.9f + rnd.nextFloat() * 0.2f;
                if (le.getBbHeight() > 3) pitch *= 0.85f;
                game.sfx.play(Sfx.Bus.MOB, s.location().toString(), vol, pitch, 90);
            }
        } catch (Throwable ignored) {
        }
    }

    private void swing(LivingEntity le) {
        try {
            le.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        } catch (Throwable ignored) {
        }
    }

    public boolean levelReady() {
        return level() != null;
    }

    public boolean spawn(MobCatalog.Entry e, String user) {
        ClientLevel l = level();
        if (l == null) return false;
        Entity ent = create(e.id);
        if (!(ent instanceof LivingEntity le)) {
            if (ent != null) ent.discard();
            return false;
        }
        Enc enc = new Enc(e, user);
        enc.mob = le;
        if (le instanceof Mob m) {
            m.setNoAi(true);
            if (e.baby) m.setBaby(true);
        }
        le.setCustomName(Component.literal(user).withStyle(e.helper ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        le.setCustomNameVisible(true);
        if (e.hand != null) {
            Item item = BuiltInRegistries.ITEM.getValue(mc(e.hand));
            if (item != null) le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        }
        if (le instanceof Slime s) s.setSize(e.size == MobCatalog.Size.MEDIUM ? 3 : 2, true);
        if (le instanceof Phantom p) p.setPhantomSize(1);
        if (le instanceof Enderman en) en.setCarriedBlock(Palette.at((int) game.car.x, 0).surface.defaultBlockState());

        enc.half = Math.max(0.25, le.getBbWidth() * 0.5);
        enc.height = Math.max(0.3, le.getBbHeight());
        CarPhysics car = game.car;
        double camRight = game.cam.x + game.cfg.viewWidth * 0.5;
        enc.z = Track.CAR_Z;
        enc.yaw = 90f;
        if (e.helper) {
            spawnHelper(enc, le, car);
            return true;
        }
        game.sfx.play(Sfx.Bus.IMPACT, "rttt:car.honk", 0.55f, 1f, 3500);
        switch (e.kind) {
            case FLY -> {
                enc.sx = Math.max(camRight + 1.5, car.x + 11) + rnd.nextDouble() * 3;
                enc.sy = car.y + 6 + rnd.nextDouble() * 3 + (e.size == MobCatalog.Size.HUGE ? 3 : 0);
                enc.cx = car.x + 6 + rnd.nextDouble() * 3;
                enc.cy = car.y + 5 + rnd.nextDouble() * 2;
                enc.x = enc.sx;
                enc.y = enc.sy;
                enc.pdur = e.size == MobCatalog.Size.HUGE ? 60 : (e.id.equals("vex") ? 22 : 30);
                if (le instanceof Vex v) v.setIsCharging(true);
            }
            case SWIM, ELDER -> {
                enc.x = car.x + 8 + rnd.nextDouble() * 2;
                enc.y = ground(enc.x) - enc.height * 0.6;
                if (le instanceof Pufferfish pf) pf.setPuffState(2);
            }
            case TELEPORT -> {
                enc.x = car.x + 6.5;
                enc.y = ground(enc.x);
            }
            case WITHER -> {
                enc.x = car.x + 11;
                enc.y = car.y + 5;
                if (le instanceof WitherBoss wb) wb.setInvulnerableTicks(120);
            }
            case DRAGON -> {
                enc.sx = car.x + 34;
                enc.sy = car.y + 14;
                enc.x = enc.sx;
                enc.y = enc.sy;
                enc.yaw = -90f;
            }
            case WARDEN -> {
                enc.x = car.x + 8.5;
                enc.y = ground(enc.x);
                le.setPose(Pose.EMERGING);
            }
            default -> {
                enc.x = Math.max(camRight + 0.5 + enc.half, car.x + 10.5);
                if (e.kind == MobCatalog.Kind.GIANT) enc.x = Math.max(camRight + 4, car.x + 14);
                enc.y = ground(enc.x);
            }
        }
        place(le, enc.x, enc.y, enc.z, enc.yaw, 0);
        addToLevel(le);
        active.add(enc);

        Effects fx = game.fx;
        fx.burst(ParticleTypes.POOF, enc.x, enc.y + enc.height * 0.5, enc.z, 12, Math.max(1, enc.half * 2), 0.06);
        playMobSound(le, 0, 0.9f);
        switch (e.kind) {
            case TELEPORT -> {
                fx.burst(ParticleTypes.PORTAL, enc.x, enc.y + 1.5, enc.z, 50, 1.5, 0.8);
                game.sfx.play(Sfx.Bus.MOB, "entity.enderman.teleport", 0.9f, 1f);
            }
            case WITHER -> {
                game.sfx.play(Sfx.Bus.VICTORY, "entity.wither.spawn", 0.9f, 1f, 0);
                fx.flash(0x8899FF, 0.35f);
                game.cam.addTrauma(0.4);
            }
            case DRAGON -> game.sfx.play(Sfx.Bus.VICTORY, "entity.ender_dragon.growl", 1f, 1f, 0);
            case WARDEN -> {
                game.sfx.play(Sfx.Bus.VICTORY, "entity.warden.emerge", 1f, 1f, 0);
                game.cam.addTrauma(0.3);
            }
            case ELDER -> {
                fx.particle(ParticleTypes.ELDER_GUARDIAN, enc.x, enc.y, enc.z, 0, 0, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "entity.elder_guardian.curse", 0.9f, 1f, 0);
            }
            case SWIM -> splash(enc.x, 1f);
            default -> {
            }
        }
        game.car.slow = Math.max(game.car.slow, e.size.ordinal() >= 3 ? 3.0 : 1.8);
        game.car.slowTime = Math.max(game.car.slowTime, 1.0);
        return true;
    }

    private void splash(double x, float strength) {
        double gy = ground(x) + 0.1;
        BlockState water = Blocks.WATER.defaultBlockState();
        game.fx.burst(ParticleTypes.SPLASH, x, gy + 0.4, Track.CAR_Z, (int) (30 * strength), 1.6, 0.6);
        game.fx.burst(ParticleTypes.BUBBLE_POP, x, gy + 0.5, Track.CAR_Z, (int) (12 * strength), 1.2, 0.3);
        game.fx.burst(ParticleTypes.FALLING_WATER, x, gy + 1.5, Track.CAR_Z, (int) (20 * strength), 1.6, 0.2);
        game.fx.burst(new BlockParticleOption(ParticleTypes.BLOCK, Palette.at((int) x, 0).road.defaultBlockState()), x, gy, Track.CAR_Z, 15, 1.5, 0.3);
        game.sfx.play(Sfx.Bus.MOB, "entity.generic.splash", 0.8f, 1f);
        game.sfx.play(Sfx.Bus.MOB, "entity.player.splash", 0.6f, 1.1f);
    }

    private void spawnHelper(Enc enc, LivingEntity le, CarPhysics car) {
        double camLeft = game.cam.x - game.cfg.viewWidth * 0.5;
        enc.yaw = -90f;
        switch (enc.e.kind) {
            case FLY -> {
                enc.sx = camLeft - 2;
                enc.sy = car.y + 5 + rnd.nextDouble() * 2;
                enc.x = enc.sx;
                enc.y = enc.sy;
                enc.pdur = enc.e.size == MobCatalog.Size.HUGE ? 40 : 26;
            }
            case SWIM -> {
                enc.x = car.rearX() - 1.6;
                enc.y = ground(enc.x) - enc.height * 0.5;
                splash(enc.x, 0.7f);
            }
            default -> {
                enc.x = camLeft - 1.5 - enc.half;
                enc.y = ground(enc.x);
            }
        }
        place(le, enc.x, enc.y, enc.z, enc.yaw, 0);
        addToLevel(le);
        active.add(enc);
        game.fx.burst(ParticleTypes.HAPPY_VILLAGER, enc.x, enc.y + enc.height * 0.6, enc.z, 10, Math.max(1, enc.half * 2), 0.05);
        playMobSound(le, 0, 0.9f);
    }

    private void tickHelper(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        MobCatalog.Kind k = enc.e.kind;
        enc.yaw = -90f;
        if (k == MobCatalog.Kind.FLY) {
            if (enc.phase == 0) {
                double t = Math.min(1, enc.age / enc.pdur);
                double tx = car.x - 0.8, ty = car.y + 1.3;
                double cx = (enc.sx + tx) / 2, cy = car.y + 6;
                double a = 1 - t;
                enc.x = a * a * enc.sx + 2 * a * t * cx + t * t * tx;
                enc.y = a * a * enc.sy + 2 * a * t * cy + t * t * ty - enc.height * 0.3;
                walkAnim(le, 0.3);
                if (t >= 1) {
                    helperPush(enc);
                    enc.phase = 1;
                    enc.timer = 0;
                }
            } else {
                enc.timer++;
                enc.x += (Math.max(0, car.vx) + 5) * DT;
                enc.y += 2.5 * DT;
                if (enc.timer > 50) enc.removed = true;
            }
            return;
        }
        if (k == MobCatalog.Kind.SWIM) {
            enc.timer++;
            double t = Math.min(1, enc.timer / 20.0);
            double x0 = car.rearX() - 1.6, x1 = car.frontX() + 2.0;
            enc.x = x0 + (x1 - x0) * t;
            enc.y = Math.max(ground(enc.x), car.y) + Math.sin(t * Math.PI) * 3.2 - enc.height * 0.3;
            game.fx.particle(ParticleTypes.FALLING_WATER, enc.x, enc.y + enc.height * 0.5, enc.z, 0, 0, 0);
            if (t >= 0.5 && enc.phase == 0) {
                helperPush(enc);
                enc.phase = 1;
            }
            if (t >= 1) {
                splash(enc.x, 0.6f);
                enc.removed = true;
            }
            return;
        }
        if (enc.phase == 0) {
            double ox = enc.x;
            enc.x += (Math.max(0, car.vx) + 6.5) * DT;
            enc.y = ground(enc.x);
            if (k == MobCatalog.Kind.HOP) {
                double t = (enc.age % 12) / 12.0;
                enc.y += Math.sin(t * Math.PI) * 0.8;
            }
            walkAnim(le, Math.abs(enc.x - ox));
            le.setSprinting(true);
            if (enc.x + enc.half >= car.rearX() - 0.05) {
                enc.x = car.rearX() - enc.half;
                if (enc.e.attackEvent >= 0) le.handleEntityEvent(enc.e.attackEvent);
                swing(le);
                helperPush(enc);
                enc.phase = 1;
                enc.timer = 0;
            }
        } else {
            enc.timer++;
            le.setSprinting(false);
            enc.x += Math.max(0, 3.0 - enc.timer * 0.15) * DT;
            enc.z += (Track.ROAD_Z0 - 1.8 - enc.z) * 0.15;
            enc.y = Math.max(ground(enc.x), rttt.world.Scenery.groundTop((int) Math.floor(enc.x), (int) Math.floor(enc.z)) + 1);
            if (enc.timer % 12 == 0) game.fx.particle(ParticleTypes.HEART, enc.x, enc.y + enc.height + 0.3, enc.z, 0, 0.05, 0);
            if (enc.timer > 70) enc.removed = true;
        }
    }

    private void helperPush(Enc enc) {
        if (enc.hit) return;
        enc.hit = true;
        game.fx.burst(ParticleTypes.HEART, game.car.rearX(), game.car.y + 0.8, enc.z, 6, 1.2, 0.1);
        game.fx.burst(ParticleTypes.HAPPY_VILLAGER, game.car.rearX(), game.car.y + 0.5, enc.z, 12, 1.5, 0.1);
        playMobSound(enc.mob, 0, 1f);
        game.onHelperPush(enc.e, enc.user);
    }

    private void doHit(Enc enc, double upBias) {
        if (enc.hit) return;
        enc.hit = true;
        if (enc.mob != null) {
            enc.mob.hurtTime = enc.mob.hurtDuration = 10;
            playMobSound(enc.mob, 1, 1f);
        }
        game.onMobHit(enc.e, enc.user, upBias);
    }

    private void fling(Enc enc, double vx, double vy) {
        enc.phase = 9;
        enc.timer = 0;
        enc.vx = vx;
        enc.vy = vy;
        enc.spinYaw = (rnd.nextBoolean() ? 1 : -1) * (14 + rnd.nextDouble() * 12);
        if (enc.mob != null) {
            try {
                enc.mob.setHealth(0f);
            } catch (Throwable ignored) {
            }
        }
    }

    public void tick() {
        ClientLevel l = level();
        if (l == null) return;
        CarPhysics car = game.car;
        double camLeft = game.cam.x - game.cfg.viewWidth * 0.5;
        Iterator<Enc> it = active.iterator();
        while (it.hasNext()) {
            Enc enc = it.next();
            enc.age++;
            try {
                if (enc.mob == null || enc.mob.isRemoved()) {
                    finish(enc);
                    it.remove();
                    continue;
                }
                if (enc.phase == 9) {
                    tickFlung(enc);
                } else if (enc.e.helper) {
                    tickHelper(enc, car);
                } else {
                    switch (enc.e.kind) {
                        case FLY -> tickFly(enc, car);
                        case SWIM -> tickSwim(enc, car);
                        case SHOOT -> tickShooter(enc, car);
                        case WITHER -> tickWither(enc, car);
                        case DRAGON -> tickDragon(enc, car);
                        case WARDEN -> tickWarden(enc, car);
                        case ELDER -> tickElder(enc, car);
                        default -> tickGround(enc, car);
                    }
                }
                if (enc.removed) {
                    finish(enc);
                    it.remove();
                    continue;
                }
                move(enc.mob, enc.x, enc.y, enc.z);
                if (enc.e.kind != MobCatalog.Kind.DRAGON || enc.phase != 9) face(enc.mob, enc.yaw);
                boolean off = enc.x < camLeft - 6 || enc.y < -80 || enc.age > 20 * 25;
                if (off) {
                    if (!enc.hit && enc.age > 20 * 25 && !enc.e.helper) doHit(enc, 0.4);
                    finish(enc);
                    it.remove();
                }
            } catch (Throwable t) {
                rttt.RTTT.LOG.warn("Mob encounter failed: {}", t.toString());
                finish(enc);
                it.remove();
            }
        }
        Iterator<Temp> ti = temps.iterator();
        while (ti.hasNext()) {
            Temp t = ti.next();
            if (--t.life <= 0 || t.ent.isRemoved()) {
                removeEntity(t.ent);
                ti.remove();
            }
        }
    }

    private void finish(Enc enc) {
        if (enc.mob != null && !enc.mob.isRemoved()) {
            game.fx.burst(ParticleTypes.POOF, enc.x, enc.y + enc.height * 0.5, enc.z, 10, Math.max(1, enc.half * 2), 0.05);
            removeEntity(enc.mob);
        }
        if (enc.proj != null) removeEntity(enc.proj);
        enc.removed = true;
    }

    private void tickFlung(Enc enc) {
        enc.timer++;
        enc.x += enc.vx * DT;
        enc.y += enc.vy * DT;
        enc.vy -= 22 * DT;
        enc.yaw += (float) enc.spinYaw;
        double g = ground(enc.x);
        if (enc.y < g && enc.vy < 0) {
            enc.y = g;
            enc.vy = -enc.vy * 0.35;
            enc.vx *= 0.6;
        }
        if (enc.timer == 1) playMobSound(enc.mob, 2, 0.6f);
        if (enc.timer > 24) enc.removed = true;
    }

    private void tickGround(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        MobCatalog.Kind k = enc.e.kind;
        double front = car.frontX();
        if (enc.phase == 0) {
            double speed = enc.e.speed;
            if (k == MobCatalog.Kind.TELEPORT) speed = 0;
            double ox = enc.x;
            enc.x -= speed * DT;
            enc.y = ground(enc.x);
            if (k == MobCatalog.Kind.HOP) {
                double t = (enc.age % 14) / 14.0;
                enc.y += Math.sin(t * Math.PI) * (enc.e.size == MobCatalog.Size.TINY ? 0.7 : 1.1);
                if (enc.age % 14 == 0) {
                    if (enc.e.id.contains("slime") || enc.e.id.contains("cube")) game.sfx.play(Sfx.Bus.MOB, "entity.slime.jump", 0.5f, 1f);
                    if (enc.e.id.equals("rabbit")) le.handleEntityEvent((byte) 1);
                }
            } else {
                walkAnim(le, Math.abs(enc.x - ox));
            }
            if (k == MobCatalog.Kind.RUN) le.setSprinting(true);
            if (le instanceof Mob m && enc.age > 4) m.setAggressive(true);
            if (le instanceof PolarBear pb && enc.x - front < 4) pb.setStanding(true);
            if (enc.x - enc.half - front < 0.35) {
                enc.phase = 1;
                enc.timer = 0;
                if (k == MobCatalog.Kind.EXPLODE) {
                    if (le instanceof Creeper c) {
                        c.ignite();
                        c.setSwellDir(1);
                    }
                    game.sfx.play(Sfx.Bus.MOB, "entity.creeper.primed", 1f, 1f);
                }
                if (enc.e.attackEvent >= 0) le.handleEntityEvent(enc.e.attackEvent);
                swing(le);
                if (enc.e.id.equals("ravager")) game.sfx.play(Sfx.Bus.MOB, "entity.ravager.roar", 0.6f, 1f);
                if (enc.e.id.equals("iron_golem")) game.sfx.play(Sfx.Bus.MOB, "entity.iron_golem.attack", 0.9f, 1f);
                if (enc.e.id.equals("goat")) game.sfx.play(Sfx.Bus.MOB, "entity.goat.ram_impact", 0.9f, 1f);
            }
        } else if (enc.phase == 1) {
            enc.timer++;
            enc.x = Math.max(enc.x, front + enc.half - 0.1);
            enc.y = ground(enc.x);
            int windup = k == MobCatalog.Kind.EXPLODE ? 20 : 3;
            if (k == MobCatalog.Kind.EXPLODE) {
                car.slow = Math.max(car.slow, 6);
                car.slowTime = 0.3;
                if (enc.timer % 4 == 0) game.fx.particle(ParticleTypes.SMOKE, enc.x, enc.y + 1.8, enc.z, 0, 0.05, 0);
            }
            if (enc.timer >= windup) {
                if (k == MobCatalog.Kind.EXPLODE) {
                    game.fx.particle(ParticleTypes.EXPLOSION_EMITTER, enc.x, enc.y + 0.8, enc.z, 0, 0, 0);
                    game.sfx.play(Sfx.Bus.IMPACT, "entity.generic.explode", 1f, 1f, 50);
                    doHit(enc, 1.3);
                    enc.removed = true;
                    removeEntity(le);
                    enc.mob = null;
                    return;
                }
                doHit(enc, k == MobCatalog.Kind.GIANT ? 1.2 : (enc.e.attackEvent == 4 ? 1.4 : 0.6));
                game.fx.burst(ParticleTypes.CRIT, front, car.y + 0.4, enc.z, 10, 1, 0.5);
                if (k == MobCatalog.Kind.GIANT) {
                    fling(enc, 2, 3);
                } else {
                    fling(enc, 7 + rnd.nextDouble() * 5, 6 + rnd.nextDouble() * 3);
                }
            }
        }
    }

    private void tickFly(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        if (enc.phase == 0) {
            double t = Math.min(1, enc.age / enc.pdur);
            double tx = car.x + 0.6, ty = carTargetY() + 0.6;
            double a = 1 - t;
            double nx = a * a * enc.sx + 2 * a * t * enc.cx + t * t * tx;
            double ny = a * a * enc.sy + 2 * a * t * enc.cy + t * t * ty;
            double dx = nx - enc.x;
            enc.yaw = dx < 0 ? 90f : -90f;
            enc.x = nx;
            enc.y = ny - enc.height * 0.4;
            walkAnim(le, 0.3);
            if (enc.age % 10 == 0) playMobSound(le, 0, 0.5f);
            if (t >= 1) {
                doHit(enc, enc.e.size == MobCatalog.Size.HUGE ? 1.0 : 0.8);
                game.fx.burst(ParticleTypes.CRIT, car.x, car.y + 0.8, enc.z, 10, 1.5, 0.4);
                if (le instanceof Vex v) v.setIsCharging(false);
                enc.phase = 2;
                enc.timer = 0;
            }
        } else {
            enc.timer++;
            enc.x += 9 * DT;
            enc.y += 7 * DT;
            enc.yaw = -90f;
            if (enc.timer > 30) enc.removed = true;
        }
    }

    private void tickSwim(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        if (enc.phase == 0) {
            if (enc.age == 6) {
                enc.p0x = enc.x;
                enc.p0y = enc.y;
                enc.pdur = 14;
                enc.phase = 1;
                enc.timer = 0;
                splash(enc.x, 0.7f);
            }
            if (enc.age % 2 == 0) game.fx.burst(ParticleTypes.BUBBLE_POP, enc.x, ground(enc.x) + 0.2, enc.z, 3, 1.0, 0.2);
        } else if (enc.phase == 1) {
            enc.timer++;
            double t = Math.min(1, enc.timer / enc.pdur);
            double tx = car.frontX() - 0.3, ty = carTargetY();
            enc.x = enc.p0x + (tx - enc.p0x) * t;
            enc.y = enc.p0y + (ty - enc.p0y) * t + Math.sin(t * Math.PI) * 3.0 - enc.height * 0.3;
            game.fx.particle(ParticleTypes.FALLING_WATER, enc.x, enc.y + enc.height * 0.5, enc.z, 0, 0, 0);
            if (enc.e.id.equals("guardian")) {
                game.fx.particle(new DustParticleOptions(0x7FE3FF, 1.2f), enc.x - 0.5, enc.y + 0.4, enc.z, -0.2, 0, 0);
            }
            if (t >= 1) {
                doHit(enc, 0.9);
                game.fx.burst(ParticleTypes.SPLASH, car.frontX(), car.y + 0.6, enc.z, 40, 1.5, 0.5);
                if (enc.e.id.contains("squid")) {
                    game.fx.burst(enc.e.id.startsWith("glow") ? ParticleTypes.GLOW_SQUID_INK : ParticleTypes.SQUID_INK,
                            car.frontX(), car.y + 0.8, enc.z, 25, 1.5, 0.2);
                    game.sfx.play(Sfx.Bus.MOB, enc.e.id.startsWith("glow") ? "entity.glow_squid.squirt" : "entity.squid.squirt", 0.9f, 1f);
                }
                if (le instanceof Pufferfish) game.sfx.play(Sfx.Bus.MOB, "entity.puffer_fish.blow_up", 0.9f, 1f);
                if (enc.e.id.equals("dolphin")) game.sfx.play(Sfx.Bus.MOB, "entity.dolphin.play", 0.9f, 1f);
                fling(enc, 5 + rnd.nextDouble() * 3, 5);
            }
        }
    }

    private Entity spawnProjectile(String type, double x, double y, double z) {
        Entity p = create(type);
        if (p == null) return null;
        place(p, x, y, z, -90f, 0);
        addToLevel(p);
        return p;
    }

    private void aimProjectile(Entity p, double dx, double dy) {
        double h = Math.abs(dx);
        float yRot = (float) (Math.atan2(dx, 0.0001) * 180 / Math.PI);
        float xRot = (float) (Math.atan2(dy, h) * 180 / Math.PI);
        p.setYRot(yRot);
        p.setXRot(xRot);
    }

    private String projectileType(MobCatalog.Shot s) {
        return switch (s) {
            case ARROW -> "arrow";
            case SNOWBALL -> "snowball";
            case SPIT -> "llama_spit";
            case SMALL_FIREBALL -> "small_fireball";
            case FIREBALL -> "fireball";
            case POTION -> "splash_potion";
            case TRIDENT -> "trident";
            case WIND -> "breeze_wind_charge";
            case BULLET -> "shulker_bullet";
            case SKULL -> "wither_skull";
            default -> null;
        };
    }

    private void shootSound(MobCatalog.Shot s) {
        String id = switch (s) {
            case ARROW -> "entity.arrow.shoot";
            case SNOWBALL -> "entity.snowball.throw";
            case SPIT -> "entity.llama.spit";
            case SMALL_FIREBALL -> "entity.blaze.shoot";
            case FIREBALL -> "entity.ghast.shoot";
            case POTION -> "entity.witch.throw";
            case TRIDENT -> "item.trident.throw";
            case WIND -> "entity.breeze.shoot";
            case BULLET -> "entity.shulker.shoot";
            case SKULL -> "entity.wither.shoot";
            case FANGS -> "entity.evoker.prepare_attack";
            default -> "entity.arrow.shoot";
        };
        game.sfx.play(Sfx.Bus.MOB, id, 0.9f, 1f);
    }

    private void tickShooter(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        MobCatalog.Shot shot = enc.e.shot;
        boolean flying = enc.e.id.equals("ghast") || enc.e.id.equals("blaze");
        double front = car.frontX();
        double hover = flying ? (enc.e.id.equals("ghast") ? 4.5 : 1.6) : 0;
        if (enc.phase == 0) {
            double ox = enc.x;
            if (enc.x - front > 8.5) enc.x -= 2.5 * DT;
            enc.y = ground(enc.x) + hover + (flying ? Math.sin(enc.age * 0.2) * 0.3 : 0);
            walkAnim(le, Math.abs(enc.x - ox));
            if (le instanceof Mob m) m.setAggressive(true);
            if (enc.x - front < 10.5 && enc.age > 8) {
                enc.phase = 1;
                enc.timer = 0;
                if (le instanceof Ghast g) {
                    g.setCharging(true);
                    game.sfx.play(Sfx.Bus.MOB, "entity.ghast.warn", 0.8f, 1f);
                }
            }
        } else if (enc.phase == 1) {
            enc.timer++;
            enc.y = ground(enc.x) + hover + (flying ? Math.sin(enc.age * 0.2) * 0.3 : 0);
            int aimTime = shot == MobCatalog.Shot.FIREBALL ? 12 : 5;
            if (enc.timer == aimTime) {
                swing(le);
                shootSound(shot);
                if (shot == MobCatalog.Shot.FANGS) {
                    enc.phase = 3;
                    enc.timer = 0;
                    le.handleEntityEvent((byte) 10);
                    return;
                }
                if (shot == MobCatalog.Shot.BEAM) {
                    enc.phase = 4;
                    enc.timer = 0;
                    return;
                }
                String type = projectileType(shot);
                enc.p0x = enc.x - enc.half - 0.2;
                enc.p0y = enc.y + (flying ? 0.5 : le.getBbHeight() * 0.7);
                enc.p0z = enc.z;
                enc.proj = type == null ? null : spawnProjectile(type, enc.p0x, enc.p0y, enc.p0z);
                if (enc.proj instanceof FallingBlockEntity) enc.proj = null;
                double dist = Math.max(1, enc.p0x - front);
                enc.pdur = Math.max(6, Math.min(16, dist / 14.0 * 20));
                enc.pt = 0;
                enc.phase = 2;
                if (le instanceof Ghast g) g.setCharging(false);
            }
        } else if (enc.phase == 2) {
            enc.pt++;
            double t = Math.min(1, enc.pt / enc.pdur);
            double tx = car.frontX() - 0.2, ty = carTargetY() + 0.3;
            double arc = (shot == MobCatalog.Shot.ARROW || shot == MobCatalog.Shot.TRIDENT || shot == MobCatalog.Shot.POTION || shot == MobCatalog.Shot.SNOWBALL) ? 1.4 : 0.2;
            double px = enc.p0x + (tx - enc.p0x) * t;
            double py = enc.p0y + (ty - enc.p0y) * t + Math.sin(t * Math.PI) * arc;
            if (enc.proj != null) {
                double dx = px - enc.proj.getX(), dy = py - enc.proj.getY();
                move(enc.proj, px, py, enc.z);
                aimProjectile(enc.proj, dx, dy);
            } else {
                game.fx.particle(ParticleTypes.CRIT, px, py, enc.z, 0, 0, 0);
            }
            if (shot == MobCatalog.Shot.SMALL_FIREBALL || shot == MobCatalog.Shot.FIREBALL)
                game.fx.particle(ParticleTypes.FLAME, px, py, enc.z, 0, 0.02, 0);
            if (t >= 1) {
                if (enc.proj != null) removeEntity(enc.proj);
                enc.proj = null;
                impactOf(shot, car);
                doHit(enc, shot == MobCatalog.Shot.WIND || shot == MobCatalog.Shot.BULLET ? 1.8 : (shot == MobCatalog.Shot.FIREBALL ? 1.2 : 0.5));
                enc.phase = 5;
                enc.timer = 0;
            }
        } else if (enc.phase == 3) {
            enc.timer++;
            if (enc.timer % 2 == 0 && enc.shots < 8) {
                enc.shots++;
                double fx = enc.x - enc.half - (enc.x - enc.half - front + 0.2) * (enc.shots / 8.0);
                Entity fang = create("evoker_fangs");
                if (fang != null) {
                    place(fang, fx, ground(fx), enc.z, 0, 0);
                    addToLevel(fang);
                    fang.handleEntityEvent((byte) 4);
                    trackTemp(fang, 22);
                }
                game.sfx.play(Sfx.Bus.MOB, "entity.evoker_fangs.attack", 0.7f, 1f, 60);
                if (enc.shots == 8) {
                    doHit(enc, 1.1);
                    enc.phase = 5;
                    enc.timer = 0;
                }
            }
        } else if (enc.phase == 4) {
            enc.timer++;
            double sx = enc.x, sy = enc.y + enc.height * 0.5;
            double tx = car.frontX(), ty = carTargetY() + 0.4;
            for (int i = 0; i < 6; i++) {
                double t = rnd.nextDouble();
                game.fx.particle(new DustParticleOptions(0x6FF2E8, 1.0f), sx + (tx - sx) * t, sy + (ty - sy) * t, enc.z, 0, 0, 0);
            }
            if (enc.timer == 1) game.sfx.play(Sfx.Bus.MOB, "entity.guardian.attack", 0.9f, 1f);
            if (enc.timer >= 18) {
                doHit(enc, 0.8);
                enc.phase = 5;
                enc.timer = 0;
            }
        } else {
            enc.y = ground(enc.x) + hover;
            if (front > enc.x - enc.half - 0.2) {
                fling(enc, 8, 6);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.player.attack.strong", 0.6f, 1.2f);
            }
            enc.timer++;
            if (enc.timer > 120) enc.removed = true;
        }
    }

    private void impactOf(MobCatalog.Shot shot, CarPhysics car) {
        double x = car.frontX(), y = car.y + 0.6, z = Track.CAR_Z;
        Effects fx = game.fx;
        switch (shot) {
            case FIREBALL, SKULL -> {
                fx.particle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0, 0, 0);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.generic.explode", 0.9f, 1f);
            }
            case SMALL_FIREBALL -> {
                fx.burst(ParticleTypes.FLAME, x, y, z, 20, 1, 0.2);
                fx.burst(ParticleTypes.LAVA, x, y, z, 6, 1, 0.2);
            }
            case SNOWBALL -> fx.burst(ParticleTypes.ITEM_SNOWBALL, x, y, z, 20, 0.6, 0.3);
            case SPIT -> fx.burst(ParticleTypes.SPIT, x, y, z, 20, 0.6, 0.2);
            case POTION -> {
                fx.burst(ParticleTypes.WITCH, x, y, z, 40, 1.5, 0.3);
                game.sfx.play(Sfx.Bus.IMPACT, "block.glass.break", 0.7f, 1.1f);
            }
            case WIND -> {
                fx.particle(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 0, 0, 0);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.wind_charge.wind_burst", 1f, 1f);
            }
            case BULLET -> {
                fx.burst(ParticleTypes.END_ROD, x, y, z, 20, 1, 0.2);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.shulker_bullet.hit", 1f, 1f);
                car.levitate = 1.0;
            }
            case ARROW, TRIDENT -> game.sfx.play(Sfx.Bus.IMPACT, "entity.arrow.hit", 0.9f, 1f);
            default -> {
            }
        }
    }

    private void tickWither(Enc enc, CarPhysics car) {
        enc.timer++;
        enc.x += (car.x + 9 - enc.x) * 0.06;
        enc.y = car.y + 4.5 + Math.sin(enc.age * 0.15) * 0.4;
        if (enc.phase == 0 && enc.timer > 30) {
            enc.phase = 1;
            enc.timer = 0;
        }
        if (enc.phase == 1 && enc.timer % 8 == 1 && enc.shots < 3) {
            enc.shots++;
            shootSound(MobCatalog.Shot.SKULL);
            Entity skull = spawnProjectile("wither_skull", enc.x - 1, enc.y + 2.4, enc.z);
            if (skull != null) trackTemp(skull, 10);
            enc.p0x = enc.x - 1;
            enc.p0y = enc.y + 2.4;
            enc.proj = skull;
            enc.pt = 0;
        }
        if (enc.proj != null && !enc.proj.isRemoved()) {
            enc.pt++;
            double t = Math.min(1, enc.pt / 9.0);
            double px = enc.p0x + (car.frontX() - enc.p0x) * t, py = enc.p0y + (carTargetY() + 0.4 - enc.p0y) * t;
            move(enc.proj, px, py, enc.z);
            game.fx.particle(ParticleTypes.SMOKE, px, py, enc.z, 0, 0, 0);
            if (t >= 1) {
                removeEntity(enc.proj);
                enc.proj = null;
                game.fx.particle(ParticleTypes.EXPLOSION_EMITTER, car.frontX(), car.y + 0.6, enc.z, 0, 0, 0);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.generic.explode", 1f, 0.8f, 40);
                if (!enc.hit) doHit(enc, 1.6);
                else {
                    car.knockBack(0.5, 0.5, false);
                    game.cam.addTrauma(0.3);
                }
            }
        }
        if (enc.phase == 1 && enc.shots >= 3 && enc.proj == null && enc.timer > 40) {
            enc.phase = 2;
            enc.timer = 0;
        }
        if (enc.phase == 2) {
            enc.y += enc.timer * 0.05;
            enc.x += 0.3;
            if (enc.timer > 40) enc.removed = true;
        }
    }

    private void tickDragon(Enc enc, CarPhysics car) {
        enc.timer++;
        double dur = 34;
        double t = Math.min(1.3, enc.timer / dur);
        double tx = car.x + 0.5, ty = car.y + 1.2;
        double ex = car.x - 26, ey = car.y + 16;
        double nx, ny;
        if (t <= 1) {
            double a = 1 - t;
            double cx = car.x + 12, cy = car.y + 2;
            nx = a * a * enc.sx + 2 * a * t * cx + t * t * tx;
            ny = a * a * enc.sy + 2 * a * t * cy + t * t * ty;
        } else {
            double u = (t - 1) / 0.3;
            nx = tx + (ex - tx) * u * 0.4;
            ny = ty + (ey - ty) * u * 0.4;
        }
        enc.x = nx;
        enc.y = ny - 1.5;
        enc.yaw = -90f;
        if (enc.timer % 6 == 0) game.fx.burst(ParticleTypes.REVERSE_PORTAL, enc.x - 3, enc.y + 1, enc.z, 6, 2, 0.05);
        if (!enc.hit && t >= 1) {
            game.sfx.play(Sfx.Bus.VICTORY, "entity.ender_dragon.growl", 1f, 0.9f, 0);
            game.fx.burst(ParticleTypes.PORTAL, car.x, car.y + 1, enc.z, 60, 3, 1);
            doHit(enc, 2.2);
        }
        if (enc.timer > dur * 2.4) enc.removed = true;
    }

    private void tickWarden(Enc enc, CarPhysics car) {
        LivingEntity le = enc.mob;
        enc.timer++;
        enc.y = ground(enc.x);
        if (enc.timer % 3 == 0 && enc.phase == 0) {
            game.fx.burst(new BlockParticleOption(ParticleTypes.BLOCK, Palette.at((int) enc.x, 0).road.defaultBlockState()),
                    enc.x, enc.y + 0.2, enc.z, 10, 2.5, 0.3);
        }
        if (enc.phase == 0 && enc.timer > 34) {
            enc.phase = 1;
            enc.timer = 0;
            le.setPose(Pose.ROARING);
            game.sfx.play(Sfx.Bus.VICTORY, "entity.warden.roar", 1f, 1f, 0);
            game.cam.addTrauma(0.4);
        }
        if (enc.phase == 1 && enc.timer > 26) {
            enc.phase = 2;
            enc.timer = 0;
            le.setPose(Pose.STANDING);
            le.handleEntityEvent((byte) 62);
            game.sfx.play(Sfx.Bus.VICTORY, "entity.warden.sonic_boom", 1f, 1f, 0);
            double sx = enc.x - 0.5, sy = enc.y + 1.8, tx = car.frontX(), ty = car.y + 0.6;
            for (int i = 0; i <= 10; i++) {
                double t = i / 10.0;
                game.fx.particle(ParticleTypes.SONIC_BOOM, sx + (tx - sx) * t, sy + (ty - sy) * t, enc.z, 0, 0, 0);
            }
            doHit(enc, 1.8);
        }
        if (enc.phase == 2 && enc.timer > 30) {
            enc.phase = 3;
            enc.timer = 0;
            le.setPose(Pose.DIGGING);
        }
        if (enc.phase == 3) {
            enc.y -= enc.timer * 0.02;
            if (enc.timer > 40) enc.removed = true;
        }
    }

    private void tickElder(Enc enc, CarPhysics car) {
        enc.timer++;
        double g = ground(enc.x);
        if (enc.phase == 0) {
            enc.y = Math.min(g + 2.5, enc.y + 0.25);
            if (enc.timer % 2 == 0) splash(enc.x, 0.4f);
            if (enc.timer > 20) {
                enc.phase = 1;
                enc.timer = 0;
            }
        } else if (enc.phase == 1) {
            enc.y = g + 2.5 + Math.sin(enc.age * 0.2) * 0.2;
            double sx = enc.x - 0.8, sy = enc.y + 1, tx = car.frontX(), ty = car.y + 0.6;
            for (int i = 0; i < 10; i++) {
                double t = rnd.nextDouble();
                game.fx.particle(new DustParticleOptions(enc.timer > 20 ? 0xFFD27F : 0x7FFFE8, 1.4f),
                        sx + (tx - sx) * t, sy + (ty - sy) * t, enc.z, 0, 0, 0);
            }
            if (enc.timer == 1) game.sfx.play(Sfx.Bus.MOB, "entity.guardian.attack", 1f, 0.7f);
            if (enc.timer > 30) {
                doHit(enc, 1.5);
                game.fx.burst(ParticleTypes.SPLASH, car.frontX(), car.y + 0.6, enc.z, 60, 2, 0.6);
                enc.phase = 2;
                enc.timer = 0;
            }
        } else {
            enc.y -= 0.15;
            if (enc.timer % 3 == 0) splash(enc.x, 0.3f);
            if (enc.timer > 30) enc.removed = true;
        }
    }

    public void dropProp(String kind, Runnable onImpact) {
        CarPhysics car = game.car;
        Entity prop;
        if (kind.equals("ANVIL")) {
            prop = create("falling_block");
            if (prop instanceof FallingBlockEntity fb) ((FallingBlockAccess) fb).rttt$setBlockState(Blocks.ANVIL.defaultBlockState());
        } else {
            prop = create("fireball");
        }
        Enc enc = new Enc(MobCatalog.BY_ID.get("zombie"), "");
        enc.hit = true;
        final Entity p = prop;
        final double startY = car.y + 9;
        final double[] st = {0};
        if (p != null) {
            place(p, car.x + (kind.equals("ANVIL") ? 0.4 : 8), startY, Track.CAR_Z, 0, 0);
            addToLevel(p);
        }
        game.schedule(new Game.Scheduled() {
            int t = 0;

            @Override
            public boolean tick() {
                t++;
                double dur = kind.equals("ANVIL") ? 12 : 18;
                double u = Math.min(1, t / dur);
                double tx = car.x + 0.4, ty = car.y + 0.6;
                if (p != null) {
                    double sx = kind.equals("ANVIL") ? tx : tx + 8 * (1 - u);
                    double sy = startY + (ty - startY) * u * u;
                    move(p, sx, sy, Track.CAR_Z);
                    if (!kind.equals("ANVIL")) game.fx.particle(ParticleTypes.FLAME, sx, sy, Track.CAR_Z, 0, 0.05, 0);
                }
                if (t == 1 && kind.equals("ANVIL")) game.sfx.play(Sfx.Bus.IMPACT, "block.anvil.fall", 0.6f, 1f);
                if (u >= 1) {
                    removeEntity(p);
                    onImpact.run();
                    return false;
                }
                return true;
            }
        });
    }

    public void clearAll() {
        for (Enc e : active) finish(e);
        active.clear();
        for (Temp t : temps) removeEntity(t.ent);
        temps.clear();
    }
}
