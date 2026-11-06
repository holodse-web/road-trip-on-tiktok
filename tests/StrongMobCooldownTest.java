package rttt.client.game;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import sun.misc.Unsafe;

/** Tests the actual admission scan without opening Minecraft or spawning entities. */
public final class StrongMobCooldownTest {
    public static void main(String[] args) throws Exception {
        Config cfg = Config.defaults();
        check(cfg.strongMobCooldownSeconds == 30, "default thirty seconds");
        check(Config.fromJson("{}", cfg).strongMobCooldownSeconds == 30, "old config default");
        check(Config.fromJson("{\"strongMobCooldownSeconds\":-1}", cfg).strongMobCooldownSeconds == 0, "minimum clamp");
        check(Config.fromJson("{\"strongMobCooldownSeconds\":999}", cfg).strongMobCooldownSeconds == 300, "maximum clamp");
        Method strong = Game.class.getDeclaredMethod("isStrongMob", MobCatalog.Entry.class);
        strong.setAccessible(true);
        for (String id : new String[]{"warden", "wither", "ender_dragon", "elder_guardian", "giant"})
            check((boolean) strong.invoke(null, MobCatalog.BY_ID.get(id)), id + " shares strong cooldown");
        for (String id : new String[]{"zombie", "happy_ghast", "iron_golem"})
            check(!(boolean) strong.invoke(null, MobCatalog.BY_ID.get(id)), id + " is exempt");

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Game game = (Game) ((Unsafe) unsafeField.get(null)).allocateInstance(Game.class);
        game.cfg = cfg;
        MobDirector director = new MobDirector(game);
        set(game, "mobs", director);
        ArrayDeque<Game.MobReq> queue = new ArrayDeque<>();
        set(game, "mobQueue", queue);
        Map<String, Integer> pending = new HashMap<>();
        pending.put("fixture", 4);
        set(game, "pendingByUser", pending);
        Game.MobReq warden = request("warden"), giant = request("giant"), zombie = request("zombie"), cow = request("cow");
        queue.add(warden); queue.add(giant); queue.add(zombie); queue.add(cow);
        set(game, "strongMobCooldown", 600);
        Method next = Game.class.getDeclaredMethod("nextMobRequest");
        next.setAccessible(true);
        check(next.invoke(game) == zombie, "ordinary mob passes cooling bosses and giant");
        check(queue.size() == 4 && pending.get("fixture") == 4, "admission scan preserves pending requests");
        queue.remove(zombie);
        check(next.invoke(game) == cow, "eligible FIFO order preserved");
        cfg.strongMobCooldownSeconds = 0;
        check(next.invoke(game) == warden, "zero disables extra cooldown immediately");
        MobDirector.Enc activeBoss = director.new Enc(MobCatalog.BY_ID.get("wither"), "active");
        director.active.add(activeBoss);
        check(next.invoke(game) == giant, "active boss guard remains when cooldown disabled");
        activeBoss.hit = true;
        check(next.invoke(game) == warden, "finished boss does not block next boss");
        director.active.clear();
        cfg.strongMobCooldownSeconds = 30;
        set(game, "spawnCooldown", 1000);
        Method spawn = Game.class.getDeclaredMethod("spawnMobs");
        spawn.setAccessible(true);
        for (int tick = 0; tick < 599; tick++) spawn.invoke(game);
        check(next.invoke(game) == cow, "all strong names stay cooling until thirty seconds");
        spawn.invoke(game);
        check(next.invoke(game) == warden, "shared cooldown expires on six hundredth game tick");
        check(queue.size() == 3 && pending.get("fixture") == 4, "waiting never dequeues or loses pending requests");
        System.out.println("PASS strong mob group, thirty-second timing, ordinary FIFO bypass, helpers, disable toggle and boss overlap guard");
    }

    private static Game.MobReq request(String id) { return new Game.MobReq(MobCatalog.BY_ID.get(id), "fixture", false); }
    private static void set(Game game, String name, Object value) throws Exception {
        Field field = Game.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
