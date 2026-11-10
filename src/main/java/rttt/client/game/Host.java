package rttt.client.game;

import java.util.ArrayDeque;
import java.util.Random;

/** Steve and Alex take turns: exactly one line is on screen at a time, the rest wait in a short queue. */
public final class Host {
    public enum Who { STEVE, ALEX }

    public static final class Line {
        public final Who who;
        public final String ru, en;
        public final int prio;
        public long start, until;
        final long ms;
        long queuedAt;

        Line(Who who, int prio, long ms, String ru, String en) {
            this.who = who;
            this.prio = prio;
            this.ms = ms;
            this.ru = ru;
            this.en = en;
        }
    }

    /** A line that is never queued; the layout editor uses it to show where bubbles appear. */
    public Line sample(Who who, String ru, String en) {
        return new Line(who, 1, 4000, ru, en);
    }

    public static final long FADE_MS = 260;
    private static final long GAP_MS = 450;
    private static final int MAX_QUEUE = 3;
    private Line cur;
    private long lastEnd;
    private final ArrayDeque<Line> queue = new ArrayDeque<>();
    private final Random rnd = new Random();
    public boolean enabled = true;

    /** The single line on screen right now, if any. */
    public synchronized Line current(long now) {
        return cur != null && now < cur.until + FADE_MS ? cur : null;
    }

    public synchronized Line current(Who who, long now) {
        Line l = current(now);
        return l != null && l.who == who ? l : null;
    }

    public synchronized void say(Who who, int prio, long ms, String ru, String en) {
        if (!enabled || ru == null || ru.isEmpty()) return;
        long now = System.currentTimeMillis();
        Line line = new Line(who, prio, Math.max(3000, ms), ru, en == null ? "" : en);
        if (prio <= 1 && rnd.nextDouble() < 0.7) return;
        if (cur == null) {
            if (!queue.isEmpty() || now - lastEnd < gapFor(prio)) enqueue(line);
            else show(line, now);
            return;
        }
        if (prio >= 4 && prio > cur.prio) {
            if (now < cur.until) cur.until = Math.max(now, cur.start + 600);
            queue.removeIf(q -> q.prio < prio);
            line.queuedAt = now;
            queue.addFirst(line);
            return;
        }
        enqueue(line);
    }

    /** Pause after the previous line: routine chatter is rare, big moments come quickly. */
    private static long gapFor(int prio) {
        if (prio >= 4) return GAP_MS;
        if (prio == 3) return 5000;
        return 15000;
    }

    private void enqueue(Line line) {
        if (line.prio <= 1 && !queue.isEmpty()) return;
        queue.removeIf(q -> q.prio < line.prio && q.prio <= 1);
        if (queue.size() >= MAX_QUEUE) {
            Line weakest = null;
            for (Line q : queue) if (weakest == null || q.prio < weakest.prio) weakest = q;
            if (weakest == null || weakest.prio >= line.prio) return;
            queue.remove(weakest);
        }
        line.queuedAt = System.currentTimeMillis();
        queue.add(line);
    }

    private void show(Line line, long now) {
        line.start = now;
        line.until = now + line.ms;
        cur = line;
    }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        if (cur != null && now >= cur.until + FADE_MS) {
            cur = null;
            lastEnd = now;
        }
        queue.removeIf(q -> q.prio < 4 && now - q.queuedAt > (q.prio >= 2 ? 9000 : 6000));
        if (cur == null && !queue.isEmpty()) {
            Line best = null;
            for (Line q : queue) if (best == null || q.prio > best.prio) best = q;
            if (now - lastEnd >= gapFor(best.prio)) {
                queue.remove(best);
                show(best, now);
            }
        }
    }

    public synchronized void clear() {
        cur = null;
        queue.clear();
    }

    public String pick(String... options) {
        return options[rnd.nextInt(options.length)];
    }

    public int pickIndex(int n) {
        return rnd.nextInt(n);
    }

    public static String nick(String user) {
        if (user == null || user.isBlank()) return "друг";
        String u = user.strip();
        return u.length() > 16 ? u.substring(0, 15) + "…" : u;
    }
}
