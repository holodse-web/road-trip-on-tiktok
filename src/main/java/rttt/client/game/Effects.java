package rttt.client.game;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rttt.world.Palette;
import rttt.world.Track;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class Effects {
    public static final String[] POSITIVE = {"SPARKLE", "HEARTS", "BOOST", "RAINBOW", "FIREWORKS", "CONFETTI", "TOTEM", "LIGHTNING", "NITRO"};
    public static final String[] NEGATIVE = {"KNOCK", "FREEZE", "ANVIL", "EXPLOSION", "METEOR"};

    private final Game game;
    private final Random rnd = new Random();
    public float flash = 0, flashPrev = 0;
    public int flashColor = 0xFFFFFF;
    public final List<Skid> skids = new ArrayList<>();
    private int rainbowTicks = 0, heartTicks = 0, flameTicks = 0, confettiTicks = 0, sparkTicks = 0, freezeTicks = 0;
    private int victoryTicks = 0;
    private int nitroTicks = 0;
    private int blastTicks = 0;

    public static final class Skid {
        public double x0, x1, y, zNear;
        public float alpha;
    }

    public Effects(Game game) {
        this.game = game;
    }

    private ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private int particleBudget = 0;
    private static final int PARTICLES_PER_TICK = 260;

    public void particle(ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        if (particleBudget <= 0) return;
        particleBudget--;
        ClientLevel l = level();
        if (l == null) return;
        try {
            l.addParticle(p, true, true, x, y, z, vx, vy, vz);
        } catch (Throwable ignored) {
        }
    }

    public void burst(ParticleOptions p, double x, double y, double z, int n, double spread, double speed) {
        for (int i = 0; i < n; i++) {
            particle(p, x + (rnd.nextDouble() - 0.5) * spread, y + (rnd.nextDouble() - 0.5) * spread, z + (rnd.nextDouble() - 0.5) * spread,
                    (rnd.nextDouble() - 0.5) * speed, rnd.nextDouble() * speed, (rnd.nextDouble() - 0.5) * speed);
        }
    }

    public void carRepaint(int rgb) {
        CarPhysics c = game.car;
        double z = Track.CAR_Z;
        for (int i = 0; i < 70; i++) {
            double px = c.x - 2.2 + rnd.nextDouble() * 4.6, py = c.y - 0.3 + rnd.nextDouble() * 1.4;
            particle(new DustParticleOptions(rgb, 1.4f + rnd.nextFloat()), px, py, z + 0.4 + rnd.nextDouble() * 0.8,
                    (rnd.nextDouble() - 0.5) * 0.2, 0.05 + rnd.nextDouble() * 0.15, 0);
        }
        burst(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.8, z + 0.8, 12, 2.5, 0.1);
        flash(rgb, 0.25f);
    }

    public void flash(int rgb, float a) {
        flashColor = rgb;
        flash = Math.max(flash, a);
    }

    public void firework(double x, double y, double z, int shapeIdx, int c1, int c2, boolean big) {
        ClientLevel l = level();
        if (l == null) return;
        FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.STAR,
                FireworkExplosion.Shape.BURST, FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.CREEPER};
        IntList colors = new IntArrayList(new int[]{c1, c2});
        IntList fade = new IntArrayList(new int[]{0xFFFFFF});
        List<FireworkExplosion> list = new ArrayList<>();
        list.add(new FireworkExplosion(shapes[Math.floorMod(shapeIdx, shapes.length)], colors, fade, true, true));
        if (big) list.add(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, new IntArrayList(new int[]{c2}), fade, false, true));
        try {
            l.createFireworks(x, y, z, 0, 0, 0, list, true);
        } catch (Throwable ignored) {
        }
    }

    private static final int[] PALETTE = {0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x00C7BE, 0x32ADE6, 0x5856D6, 0xAF52DE, 0xFF2D55};

    public int randomColor() {
        return PALETTE[rnd.nextInt(PALETTE.length)];
    }

    public void fireworksAround(double cx, double cy, int count) {
        for (int i = 0; i < count; i++) {
            double x = cx + (rnd.nextDouble() - 0.3) * 14;
            double y = cy + 5 + rnd.nextDouble() * 6;
            double z = -2 - rnd.nextDouble() * 6;
            firework(x, y, z, rnd.nextInt(5), randomColor(), randomColor(), rnd.nextBoolean());
        }
        game.sfx.play(Sfx.Bus.GIFT, "entity.firework_rocket.large_blast", 0.7f, 0.9f + rnd.nextFloat() * 0.2f, 60);
        game.sfx.play(Sfx.Bus.GIFT, "entity.firework_rocket.twinkle", 0.6f, 1.0f, 120);
    }

    public void lightning(double x, double y, double z) {
        ClientLevel l = level();
        if (l == null) return;
        try {
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
            if (bolt == null) return;
            bolt.setVisualOnly(true);
            bolt.setId(game.mobs.nextId());
            bolt.setPos(x, y, z);
            l.addEntity(bolt);
            game.mobs.trackTemp(bolt, 8);
        } catch (Throwable ignored) {
        }
        game.sfx.play(Sfx.Bus.GIFT, "entity.lightning_bolt.thunder", 0.55f, 1.1f, 300);
        game.sfx.play(Sfx.Bus.GIFT, "entity.lightning_bolt.impact", 0.7f, 1.0f, 300);
        flash(0xE8F0FF, 0.6f);
    }

    public void positive(String effect, double st) {
        CarPhysics c = game.car;
        double x = c.x, y = c.y + 0.8, z = Track.CAR_Z;
        String e = effect == null ? "SPARKLE" : effect.toUpperCase();
        flameTicks = Math.max(flameTicks, (int) (14 + st * 20));
        sparkTicks = Math.max(sparkTicks, (int) (10 + st * 10));
        burst(ParticleTypes.END_ROD, x, y, z, (int) (8 + st * 10), 2.2, 0.25);
        particle(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 0.95f, 0.7f), x, y + 0.4, z + 0.6, 0, 0, 0);
        switch (e) {
            case "HEARTS" -> heartTicks = Math.max(heartTicks, (int) (20 + st * 20));
            case "RAINBOW" -> rainbowTicks = Math.max(rainbowTicks, (int) (40 + st * 50));
            case "FIREWORKS" -> fireworksAround(x, y, (int) Math.max(2, 2 + st * 3));
            case "CONFETTI" -> confettiTicks = Math.max(confettiTicks, (int) (30 + st * 30));
            case "TOTEM" -> {
                burst(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.5, z, (int) (40 + st * 40), 1.5, 0.9);
                game.sfx.play(Sfx.Bus.GIFT, "item.totem.use", 0.7f, 1.0f, 500);
            }
            case "LIGHTNING" -> {
                lightning(x - 2.5, Track.top((int) Math.floor(x - 2.5)), -4);
                burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 2.5, 0.6);
            }
            case "BOOST" -> {
                flameTicks += 20;
                burst(ParticleTypes.FIREWORK, x - 2.3, y - 0.3, z, 25, 0.6, 0.3);
            }
            case "ULTRA_PLUS" -> {
                flameTicks = 120;
                rainbowTicks = 160;
                confettiTicks = 120;
                heartTicks = 60;
                fireworksAround(x, y, 8);
                burst(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.5, z, 160, 3, 1.4);
                flash(0xFFD54A, 0.85f);
                game.cam.addTrauma(0.5);
                game.sfx.play(Sfx.Bus.VICTORY, "item.totem.use", 1f, 0.9f, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "ui.toast.challenge_complete", 1f, 1.0f, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "block.beacon.activate", 1f, 1.2f, 0);
            }
            default -> {
                burst(ParticleTypes.HAPPY_VILLAGER, x, y, z, (int) (10 + st * 12), 2.5, 0.2);
                burst(ParticleTypes.WAX_ON, x, y, z, (int) (6 + st * 6), 2.5, 0.2);
            }
        }
        if (!e.equals("ULTRA_PLUS")) {
            if (st < 0.8) game.sfx.play(Sfx.Bus.GIFT, "entity.experience_orb.pickup", 0.6f, 0.9f + (float) rnd.nextDouble() * 0.3f, 60);
            else if (st < 1.25) game.sfx.play(Sfx.Bus.GIFT, "entity.player.levelup", 0.6f, 1.1f, 120);
            else game.sfx.play(Sfx.Bus.GIFT, "ui.toast.challenge_complete", 0.75f, 1.05f, 600);
            game.sfx.play(Sfx.Bus.GIFT, "block.beacon.power_select", 0.35f, 1.6f, 300);
            game.sfx.play(Sfx.Bus.GIFT, "block.amethyst_block.chime", 0.8f, 1.2f + (float) st * 0.2f, 90);
        }
        flash(0xFFF3C0, (float) Math.min(0.35, 0.08 + st * 0.12));
    }

    public void negativeImpact(String effect, double st) {
        CarPhysics c = game.car;
        double x = c.frontX(), y = c.y + 0.4, z = Track.CAR_Z;
        String e = effect == null ? "KNOCK" : effect.toUpperCase();
        burst(ParticleTypes.CRIT, x, y, z, (int) (12 + st * 12), 1.2, 0.6);
        particle(ParticleTypes.SWEEP_ATTACK, x, y + 0.3, z + 0.8, 0, 0, 0);
        game.cam.addTrauma(0.25 + st * 0.25);
        switch (e) {
            case "FREEZE" -> {
                freezeTicks = Math.max(freezeTicks, (int) (30 + st * 30));
                burst(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.8, z, 60, 3.5, 0.3);
                burst(ParticleTypes.ITEM_SNOWBALL, c.x, c.y + 0.8, z, 30, 2.5, 0.4);
                game.sfx.play(Sfx.Bus.IMPACT, "block.powder_snow.break", 0.9f, 0.8f, 100);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.player.hurt_freeze", 0.6f, 1.0f, 100);
                c.slow = Math.max(c.slow, 4);
                c.slowTime = 2.5;
                flash(0xBFE9FF, 0.4f);
            }
            case "ANVIL" -> {
                burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ANVIL.defaultBlockState()), c.x, c.y + 1.2, z, 30, 1.5, 0.5);
                game.sfx.play(Sfx.Bus.IMPACT, "block.anvil.land", 1f, 0.8f, 100);
            }
            case "EXPLOSION", "METEOR" -> {
                particle(ParticleTypes.EXPLOSION_EMITTER, x + 0.5, y, z, 0, 0, 0);
                burst(ParticleTypes.LARGE_SMOKE, x, y, z, 25, 2, 0.15);
                burst(ParticleTypes.FLAME, x, y, z, 25, 2, 0.25);
                game.sfx.play(Sfx.Bus.IMPACT, "entity.generic.explode", 1f, 0.9f, 80);
                flash(0xFFB070, 0.5f);
            }
            case "ULTRA_MINUS" -> {
                for (int i = 0; i < 4; i++) particle(ParticleTypes.EXPLOSION_EMITTER, x + rnd.nextDouble() * 3, y + rnd.nextDouble() * 2, z, 0, 0, 0);
                burst(ParticleTypes.LARGE_SMOKE, x, y, z, 80, 4, 0.25);
                burst(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 60, 4, 0.4);
                burst(ParticleTypes.SONIC_BOOM, x, y + 0.5, z, 3, 1, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "entity.generic.explode", 1f, 0.6f, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "entity.wither.spawn", 0.8f, 1.0f, 0);
                game.sfx.play(Sfx.Bus.VICTORY, "entity.ender_dragon.growl", 0.9f, 0.8f, 0);
                flash(0xFF2A2A, 0.8f);
                game.cam.addTrauma(1.0);
            }
            default -> {
                game.sfx.play(Sfx.Bus.IMPACT, "entity.player.attack.knockback", 0.9f, 0.9f, 60);
            }
        }
        game.sfx.play(Sfx.Bus.IMPACT, "entity.zombie.attack_iron_door", (float) Math.min(1.0, 0.45 + st * 0.3), 0.9f + (float) rnd.nextDouble() * 0.2f, 90);
        if (st > 1.2) game.sfx.play(Sfx.Bus.IMPACT, "item.shield.block", 0.7f, 0.7f, 120);
    }

    public void nitro(double secs) {
        nitroTicks = Math.max(nitroTicks, (int) (secs * 20));
        flash(0x9FE8FF, 0.35f);
    }

    public void blastTrail(double power) {
        blastTicks = Math.max(blastTicks, (int) Math.min(90, 35 + power * 20));
    }

    public void victory(boolean start) {
        if (start) victoryTicks = 140;
    }

    public void tick() {
        particleBudget = PARTICLES_PER_TICK;
        flashPrev = flash;
        flash = Math.max(0, flash - 0.05f);
        CarPhysics c = game.car;
        ClientLevel l = level();
        if (l == null) return;
        double z = Track.CAR_Z;
        double cs = Math.cos(c.ang), sn = Math.sin(c.ang);
        double ex = c.x - cs * 2.35 + sn * 0.35, ey = c.y + sn * -2.35 - cs * 0.35 + 0.05;

        boolean rearGround = c.grounded[1];
        double speed = Math.abs(c.vx);
        if (blastTicks > 0) {
            blastTicks--;
            double front = c.frontX();
            particle(ParticleTypes.LARGE_SMOKE, front, c.y + 0.2, z + 0.4, 0.2, 0.05, 0);
            particle(ParticleTypes.ELECTRIC_SPARK, front, c.y + 0.3, z + 0.5, 0.4, 0.08, 0);
            if (blastTicks % 3 == 0) particle(ParticleTypes.FLAME, front, c.y + 0.3, z, 0.25, 0.04, 0);
        }
        if (rearGround && speed > 1.5) {
            double wx = c.x - cs * 1.38, wy = Track.top((int) Math.floor(wx)) + 0.05;
            BlockState road = Palette.at((int) Math.floor(wx), 0).road.defaultBlockState();
            int n = speed > 9 ? 2 : 1;
            for (int i = 0; i < n; i++) {
                particle(new BlockParticleOption(ParticleTypes.BLOCK, road), wx - 0.4, wy + 0.1, z + 0.8 * (rnd.nextDouble() - 0.5) + 0.6,
                        -0.15 - speed * 0.02, 0.15 + rnd.nextDouble() * 0.1, 0.02);
            }
            if (rnd.nextDouble() < 0.25 + c.slip) particle(ParticleTypes.POOF, wx - 0.5, wy + 0.2, z + 0.7, -0.05, 0.03, 0.01);
        }
        if (c.slip > 0.25 && (c.grounded[0] || c.grounded[1])) {
            for (int i = 0; i < 2; i++) particle(ParticleTypes.CLOUD, c.x - cs * 1.38 - 0.4, Track.topAt(c.x - 1.38) + 0.25, z + 0.6, -0.08, 0.06, 0.02);
        }
        if (c.landImpact > 0.25) {
            double wy = Track.topAt(c.x) + 0.1;
            BlockState road = Palette.at((int) Math.floor(c.x), 0).road.defaultBlockState();
            burst(new BlockParticleOption(ParticleTypes.BLOCK, road), c.x, wy, z + 0.5, (int) (8 + c.landImpact * 16), 3.5, 0.25);
            burst(ParticleTypes.POOF, c.x, wy + 0.2, z + 0.5, (int) (3 + c.landImpact * 6), 3.5, 0.05);
        }
        if (c.scrapeImpact > 0.3) {
            burst(ParticleTypes.ELECTRIC_SPARK, c.x, c.y - 0.2, z + 0.6, 6, 3.0, 0.3);
            burst(ParticleTypes.CRIT, c.x, c.y - 0.2, z + 0.6, 4, 3.0, 0.3);
        }
        if (c.recoveringNow) {
            burst(ParticleTypes.CLOUD, c.x, c.y, z, 30, 3, 0.15);
            burst(ParticleTypes.END_ROD, c.x, c.y, z, 12, 3, 0.2);
        }
        boolean skidding = (c.braking > 0.25 || c.slip > 0.35) && rearGround && speed > 2.5;
        if (skidding) {
            double wx = c.x - cs * 1.38;
            double wy = Track.top((int) Math.floor(wx));
            Skid last = skids.isEmpty() ? null : skids.get(skids.size() - 1);
            if (last != null && last.alpha > 0.85f && Math.abs(last.x1 - wx) < 1.2 && Math.abs(last.y - wy) < 0.01) {
                last.x1 = wx;
            } else {
                Skid s = new Skid();
                s.x0 = s.x1 = wx;
                s.y = wy;
                s.zNear = z;
                s.alpha = 1f;
                skids.add(s);
            }
            if (rnd.nextDouble() < 0.5) particle(ParticleTypes.WHITE_SMOKE, wx, wy + 0.2, z + 0.6, -0.03, 0.04, 0);
        }
        Iterator<Skid> it = skids.iterator();
        while (it.hasNext()) {
            Skid s = it.next();
            s.alpha -= 0.004f;
            if (s.alpha <= 0 || s.x1 < c.x - 40) it.remove();
        }
        if (skids.size() > 60) skids.remove(0);

        if (nitroTicks > 0) {
            nitroTicks--;
            for (int i = 0; i < 2; i++) {
                particle(ParticleTypes.FLAME, ex, ey, z + 0.3 - i * 0.4, -0.5 - rnd.nextDouble() * 0.3, 0.01, 0);
                particle(ParticleTypes.SOUL_FIRE_FLAME, ex - 0.2, ey, z + 0.1, -0.6, 0.0, 0);
            }
            if (nitroTicks % 2 == 0) particle(ParticleTypes.LARGE_SMOKE, ex - 0.6, ey + 0.1, z, -0.15, 0.04, 0);
            for (int i = 0; i < 3; i++) {
                particle(ParticleTypes.END_ROD, game.cam.x + 9 + rnd.nextDouble() * 3, c.y + rnd.nextDouble() * 6 - 1,
                        z + 1.5 + rnd.nextDouble() * 3, -2.5 - rnd.nextDouble(), 0, 0);
            }
        }
        if (flameTicks > 0) {
            flameTicks--;
            particle(ParticleTypes.FLAME, ex, ey, z + 0.3, -0.25 - rnd.nextDouble() * 0.2, 0.02, 0);
            particle(ParticleTypes.SOUL_FIRE_FLAME, ex, ey, z - 0.1, -0.3, 0.01, 0);
            if (rnd.nextBoolean()) particle(ParticleTypes.SMOKE, ex - 0.3, ey, z + 0.2, -0.1, 0.03, 0);
        }
        if (sparkTicks > 0) {
            sparkTicks--;
            particle(ParticleTypes.END_ROD, c.x + (rnd.nextDouble() - 0.5) * 4.5, c.y + 0.6 + rnd.nextDouble(), z + 1.0, -0.2, 0.02, 0);
            particle(ParticleTypes.GLOW, c.x + (rnd.nextDouble() - 0.5) * 4.5, c.y + rnd.nextDouble() * 1.4, z + 1.0, 0, 0.02, 0);
        }
        if (rainbowTicks > 0) {
            rainbowTicks--;
            for (int i = 0; i < 6; i++) {
                int col = PALETTE[i % 7];
                particle(new DustParticleOptions(col, 1.6f), c.x - 2.4 - rnd.nextDouble() * 0.3, c.y + 0.9 - i * 0.18, z, -0.02, 0, 0);
            }
        }
        if (heartTicks > 0) {
            heartTicks--;
            if (heartTicks % 3 == 0) particle(ParticleTypes.HEART, c.x + (rnd.nextDouble() - 0.5) * 2.5, c.y + 1.5, z + 0.3, 0, 0.1, 0);
        }
        if (confettiTicks > 0) {
            confettiTicks--;
            for (int i = 0; i < 4; i++) {
                particle(new DustParticleOptions(randomColor(), 1.1f), c.x + (rnd.nextDouble() - 0.4) * 10,
                        c.y + 4 + rnd.nextDouble() * 4, z - rnd.nextDouble() * 2, 0, -0.05, 0);
            }
        }
        if (freezeTicks > 0) {
            freezeTicks--;
            particle(ParticleTypes.SNOWFLAKE, c.x + (rnd.nextDouble() - 0.5) * 4, c.y + 1, z + 0.5, 0, -0.02, 0);
        }
        if (victoryTicks > 0) {
            victoryTicks--;
            if (victoryTicks % 7 == 0) fireworksAround(game.cam.x, c.y, 2);
            if (victoryTicks % 3 == 0) {
                particle(new DustParticleOptions(randomColor(), 1.4f), game.cam.x + (rnd.nextDouble() - 0.5) * 18,
                        c.y + 8 + rnd.nextDouble() * 3, -1 - rnd.nextDouble() * 3, 0, -0.08, 0);
                particle(ParticleTypes.TOTEM_OF_UNDYING, c.x + (rnd.nextDouble() - 0.5) * 6, c.y + 2, z, 0, 0.3, 0);
            }
        }
    }

    public void clear() {
        skids.clear();
        flash = flashPrev = 0;
        rainbowTicks = heartTicks = flameTicks = confettiTicks = sparkTicks = freezeTicks = victoryTicks = nitroTicks = 0;
    }
}
