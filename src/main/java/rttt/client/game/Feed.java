package rttt.client.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** What viewers just did and what it changed: the source for the on-screen event cards. */
public final class Feed {
    public enum Kind { GIFT, LIKE, MOB_HELP, MOB_HURT, SOCIAL, HORDE, SYSTEM, OTHER }

    public static final class Item {
        public final long id, time, amount;
        public final Kind kind;
        /** Who did it, what it was ("Роза ×3", "Крипер") and an optional short remark ("НИТРО"). */
        public final String user, title, note;
        /** Gift name for its picture, or a mob id for its spawn-egg icon; either may be null. */
        public final String gift, mob;

        Item(long id, long time, Kind kind, long amount, String user, String title, String note, String gift, String mob) {
            this.id = id;
            this.time = time;
            this.kind = kind;
            this.amount = amount;
            this.user = user == null ? "" : user;
            this.title = title == null ? "" : title;
            this.note = note == null ? "" : note;
            this.gift = gift;
            this.mob = mob;
        }
    }

    /** A stand-in card for the layout editor; never enters the history. */
    public static Item sample(long id, Kind kind, long amount, String user, String title, String note, String gift, String mob) {
        return new Item(-id, System.currentTimeMillis(), kind, amount, user, title, note, gift, mob);
    }

    private static final int KEEP = 8;
    private final ArrayDeque<Item> items = new ArrayDeque<>();
    private long nextId = 1;
    private volatile Item last, banner;
    private volatile long bannerUntil;

    public synchronized Item post(Kind kind, long amount, String user, String title, String note, String gift, String mob) {
        Item item = new Item(nextId++, System.currentTimeMillis(), kind, amount, user, title, note, gift, mob);
        items.addFirst(item);
        while (items.size() > KEEP) items.removeLast();
        last = item;
        return item;
    }

    /** Holds an item in the large banner until the given time. */
    public void spotlight(Item item, long untilMs) {
        banner = item;
        bannerUntil = untilMs;
    }

    public Item last() {
        return last;
    }

    public Item banner(long now) {
        Item b = banner;
        return b != null && now < bannerUntil ? b : null;
    }

    public long bannerUntil() {
        return bannerUntil;
    }

    /** Newest first, skipping {@code skip} entries and anything older than {@code maxAgeMs}. */
    public synchronized List<Item> recent(int skip, int count, long maxAgeMs, long now) {
        List<Item> out = new ArrayList<>(count);
        int i = 0;
        for (Item item : items) {
            if (i++ < skip) continue;
            if (now - item.time > maxAgeMs) break;
            out.add(item);
            if (out.size() >= count) break;
        }
        return out;
    }

    public synchronized void clear() {
        items.clear();
        last = null;
        banner = null;
        bannerUntil = 0;
    }
}
