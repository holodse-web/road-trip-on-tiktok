package rttt.client.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

public final class Sfx {
    public enum Bus { ENGINE, ROAD, IMPACT, MOB, GIFT, VICTORY }

    private final Game game;
    private final Map<String, Long> lastPlayed = new HashMap<>();
    private final long[] window = new long[16];
    private int windowIdx = 0;
    private Loop engine, road, wind;
    private double rev = 0;

    public void rev(double amount) {
        rev = Math.max(rev, Math.min(1.2, amount));
    }

    public void blastImpact(double power) {
        rev = 0;
        float weight = (float) Math.min(1, 0.65 + power * 0.14);
        play(Bus.IMPACT, "rttt:car.crash", weight, 0.68f, 180);
        play(Bus.IMPACT, "entity.iron_golem.damage", weight * 0.75f, 0.58f, 180);
        play(Bus.IMPACT, "entity.breeze.wind_burst", weight * 0.8f, 0.55f, 180);
    }
    private int restartCooldown = 0;
    private int engineMissing = 0, roadMissing = 0, windMissing = 0;

    public Sfx(Game game) {
        this.game = game;
    }

    public static SoundEvent ev(String id) {
        Identifier rl = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        return SoundEvent.createVariableRangeEvent(rl);
    }

    private double busVol(Bus b) {
        Config c = game.cfg;
        double v = switch (b) {
            case ENGINE -> c.volEngine;
            case ROAD -> c.volRoad;
            case IMPACT -> c.volImpacts;
            case MOB -> c.volMobs;
            case GIFT -> c.volGifts;
            case VICTORY -> c.volVictory;
        };
        return Math.max(0, Math.min(1, v)) * Math.max(0, Math.min(1, c.volMaster));
    }

    public void play(Bus bus, String id, float volume, float pitch) {
        play(bus, id, volume, pitch, 70);
    }

    public void play(Bus bus, String id, float volume, float pitch, int cooldownMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        long now = System.currentTimeMillis();
        Long last = lastPlayed.get(id);
        if (last != null && now - last < cooldownMs) return;
        int recent = 0;
        for (long t : window) if (now - t < 300) recent++;
        boolean important = bus == Bus.VICTORY || bus == Bus.IMPACT;
        if (recent >= 12 && !important) return;
        float duck = recent > 6 ? (float) Math.max(0.45, 1.0 - (recent - 6) * 0.08) : 1f;
        lastPlayed.put(id, now);
        window[windowIdx++ & 15] = now;
        float v = (float) (volume * busVol(bus) * duck);
        if (v <= 0.005f) return;
        try {
            mc.getSoundManager().play(new OneShot(ev(id), v, pitch));
        } catch (Throwable ignored) {
        }
    }

    public void tick(CarPhysics car, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        if (!active) {
            stopLoops();
            return;
        }
        if (restartCooldown > 0) restartCooldown--;
        if (restartCooldown == 0) {
            var sm = mc.getSoundManager();
            engineMissing = (engine == null || engine.isStopped() || !sm.isActive(engine)) ? engineMissing + 1 : 0;
            roadMissing = (road == null || road.isStopped() || !sm.isActive(road)) ? roadMissing + 1 : 0;
            windMissing = (wind == null || wind.isStopped() || !sm.isActive(wind)) ? windMissing + 1 : 0;
            if (engine == null || engineMissing > 40) {
                if (engine != null) engine.kill();
                engine = new Loop(ev("rttt:car.engine"));
                sm.play(engine);
                engineMissing = 0;
                restartCooldown = 10;
            }
            if (road == null || roadMissing > 40) {
                if (road != null) road.kill();
                road = new Loop(ev("rttt:car.road"));
                sm.play(road);
                roadMissing = 0;
                restartCooldown = 10;
            }
            if (wind == null || windMissing > 40) {
                if (wind != null) wind.kill();
                wind = new Loop(ev("rttt:car.wind"));
                sm.play(wind);
                windMissing = 0;
                restartCooldown = 10;
            }
        }
        rev *= 0.965;
        boolean ground = car.grounded[0] || car.grounded[1];
        double speed = Math.abs(car.vx);
        double rpm = car.rpm;
        if (engine != null) {
            engine.targetVol = (float) (busVol(Bus.ENGINE) * Math.min(1.25, 0.55 + 0.45 * Math.min(1, rpm) + rev * 0.3));
            engine.targetPitch = (float) Math.min(2.0, 0.62 + rpm * 0.75 + (car.boost > 1 ? 0.08 : 0) + rev * 0.3);
        }
        if (road != null) {
            road.targetVol = (float) (busVol(Bus.ROAD) * (ground ? Math.min(1, speed / 12.0) * (0.6 + car.slip * 0.6) : 0.0));
            road.targetPitch = (float) (0.8 + Math.min(0.5, speed / 30.0));
        }
        if (wind != null) {
            wind.targetVol = (float) (busVol(Bus.ROAD) * 0.6 * Math.min(1, Math.max(0, speed - 6) / 14.0 + (ground ? 0 : 0.25)));
            wind.targetPitch = (float) (0.9 + Math.min(0.4, speed / 40.0));
        }
    }

    public void stopLoops() {
        if (engine != null) engine.kill();
        if (road != null) road.kill();
        if (wind != null) wind.kill();
        engine = road = wind = null;
    }

    static final class OneShot extends AbstractSoundInstance {
        OneShot(SoundEvent e, float vol, float pitch) {
            super(e, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.volume = vol;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
    }

    static final class Loop extends AbstractSoundInstance implements TickableSoundInstance {
        float targetVol = 0, targetPitch = 1;
        boolean stopped = false;

        Loop(SoundEvent e) {
            super(e, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.pitch = 1f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void kill() {
            stopped = true;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            volume += (targetVol - volume) * 0.25f;
            pitch += (targetPitch - pitch) * 0.3f;
            if (volume < 0.001f) volume = 0.001f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
