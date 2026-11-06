package rttt.client.game;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import sun.misc.Unsafe;

/** Exercises the actual queue with zero-meter effects, without opening a world. */
public final class GiftCooldownTest {
    private static final Method PROCESS;
    static {
        try {
            PROCESS = Game.class.getDeclaredMethod("processRewards");
            PROCESS.setAccessible(true);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.defaults();
        check(cfg.giftCooldownSeconds == 0.3, "default adds a short 0.3s pause");
        check(Config.fromJson("{\"giftCooldownSeconds\":-1}", cfg).giftCooldownSeconds == 0, "lower clamp");
        check(Config.fromJson("{\"giftCooldownSeconds\":99}", cfg).giftCooldownSeconds == 30, "upper clamp");
        check(Config.fromJson("{}", cfg).giftCooldownSeconds == 0.3, "old config default");

        Fixture timed = fixture(cfg);
        timed.queue.add(item("Rose", 1));
        timed.queue.add(item("Lion", 2));
        tick(timed.game, 1);
        check(timed.queue.size() == 1, "first gift starts immediately");
        tick(timed.game, 18);
        check(timed.queue.size() == 1, "second gift remains queued after original 0.9s spotlight");
        tick(timed.game, 5);
        check(timed.queue.size() == 1, "gift waits until added pause completes");
        tick(timed.game, 1);
        check(timed.queue.isEmpty(), "gift executes after spotlight plus 0.3s; no drop");

        Fixture nonGift = fixture(cfg);
        set(nonGift.game, "giftCooldown", 0.3);
        nonGift.queue.add(item("Rose", 1));
        nonGift.queue.add(item(null, 2));
        tick(nonGift.game, 1);
        check(nonGift.queue.size() == 1 && nonGift.queue.get(0).r.gift() != null,
                "added gift pause does not block non-gift effects");

        cfg.giftQueue = false;
        Fixture instant = fixture(cfg);
        set(instant.game, "giftCooldown", 30.0);
        instant.queue.add(item("Rose", 1));
        instant.queue.add(item("Lion", 2));
        tick(instant.game, 1);
        check(instant.queue.isEmpty(), "disabled queue bypasses cooldown");
        System.out.println("PASS gift cooldown timing, old config default, bounds, non-gift bypass and immediate mode");
    }

    private record Fixture(Game game, ArrayList<Game.QItem> queue) {}
    private static Fixture fixture(Config cfg) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Game game = (Game) ((Unsafe) unsafeField.get(null)).allocateInstance(Game.class);
        game.cfg = cfg;
        ArrayList<Game.QItem> queue = new ArrayList<>();
        set(game, "rewards", queue);
        return new Fixture(game, queue);
    }

    private static Game.QItem item(String gift, long sequence) {
        return new Game.QItem(new Game.Reward(0, "fixture", "fixture", "BOOST", gift), 1, sequence, null, 1);
    }

    private static void tick(Game game, int count) throws Exception {
        for (int i = 0; i < count; i++) PROCESS.invoke(game);
    }

    private static void set(Game game, String name, Object value) throws Exception {
        Field field = Game.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
