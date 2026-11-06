package rttt.client.game;

public final class CarPhysicsTest {
    public static void main(String[] args) {
        CarPhysics blast = new CarPhysics();
        blast.reset(0);
        blast.blast(Math.log10(40_001) / 3.0);
        blast.w = 0; // Repeatable upright trajectory: same real integration and road collision code.
        blast.noAirControl = 0;
        for (int tick = 0; tick < 50; tick++) blast.tick();
        check(blast.x < -70, "40k blast did not throw the car substantially backwards: " + blast.x);
        check(Double.isFinite(blast.y) && Double.isFinite(blast.vx), "blast produced invalid physics");
        double backDistance = -blast.x;
        blast.nitro(36, 4);
        check(blast.targetSpeed() == 36 && blast.vx > 0, "positive nitro failed to cancel blast recoil");

        CarPhysics airborne = new CarPhysics();
        airborne.reset(0);
        airborne.y = 50;
        airborne.ang = Math.PI;
        airborne.vx = airborne.vy = airborne.w = 0;
        for (int tick = 0; tick < 30; tick++) airborne.tick();
        check(airborne.recoveries == 0 && airborne.y > 3, "a normal airborne flip triggered rescue");

        CarPhysics landed = new CarPhysics();
        landed.reset(0);
        landed.blast(1.535);
        landed.x = -60;
        landed.y = 0.96;
        landed.ang = landed.w = landed.vy = 0;
        landed.vx = -18;
        landed.noAirControl = 0;
        for (int tick = 0; tick < 100; tick++) landed.tick();
        check(landed.recoveries == 0, "upright blast landing triggered rescue");
        check(landed.x < -60, "upright blast landing was relocated forward");

        CarPhysics overturned = new CarPhysics();
        overturned.reset(0);
        overturned.y = 1;
        overturned.ang = Math.PI;
        overturned.vx = overturned.vy = overturned.w = 0;
        for (int tick = 0; tick < 180; tick++) overturned.tick();
        check(overturned.recoveries > 0, "genuinely stranded overturned car was not rescued");

        CarPhysics idle = new CarPhysics();
        idle.cruise = 0;
        idle.reset(0);
        for (int tick = 0; tick < 120; tick++) idle.tick();
        check(idle.recoveries == 0, "intentionally stopped upright car was rescued");
        System.out.printf("PASS: 40k blast travels %.1f blocks backwards in 2.5s; upright/airborne landings stay natural; stranded rescue and positive nitro work.%n", backDistance);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
