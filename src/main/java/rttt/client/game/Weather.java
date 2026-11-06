package rttt.client.game;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Random;

public final class Weather {
    public enum State { CLEAR, RAIN, THUNDER }

    private final Game game;
    private final Random rnd = new Random();
    public State state = State.CLEAR;
    private int timer = 20 * 120;
    private float rain = 0, thunder = 0;
    private int boltTimer = 200;

    public Weather(Game game) {
        this.game = game;
    }

    public void tick(ClientLevel level) {
        if (level == null) return;
        if (!game.cfg.autoWeather) {
            state = State.CLEAR;
        } else if (--timer <= 0) {
            switch (state) {
                case CLEAR -> {
                    state = rnd.nextDouble() < 0.25 ? State.THUNDER : State.RAIN;
                    timer = 20 * (70 + rnd.nextInt(110));
                }
                default -> {
                    state = State.CLEAR;
                    timer = 20 * (240 + rnd.nextInt(300));
                }
            }
        }
        float tr = state == State.CLEAR ? 0f : 1f;
        float tt = state == State.THUNDER ? 1f : 0f;
        rain += Math.signum(tr - rain) * Math.min(Math.abs(tr - rain), 0.004f);
        thunder += Math.signum(tt - thunder) * Math.min(Math.abs(tt - thunder), 0.004f);
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
        if (state == State.THUNDER && thunder > 0.8f && --boltTimer <= 0) {
            boltTimer = 120 + rnd.nextInt(400);
            double x = game.cam.x + (rnd.nextDouble() - 0.5) * 30;
            game.fx.lightning(x, rttt.world.Scenery.groundTop((int) x, -30) + 1, -30 - rnd.nextInt(30));
        }
    }

    public void force(State s) {
        state = s;
        timer = 20 * 120;
    }
}
