package rttt.client.game;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Bounded handoff to the render thread; counters coalesce without losing units. */
public final class EventInbox {
    private final int capacity;
    private final ArrayDeque<LiveEvent> queue = new ArrayDeque<>();
    private final Map<String, LiveEvent> counters = new HashMap<>();

    public EventInbox(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    private static String key(LiveEvent event) {
        if (event.type == LiveEvent.Type.LIKE) return "L|" + event.test + "|" + event.user;
        if (event.type == LiveEvent.Type.GIFT) return "G|" + event.test + "|" + event.user + "|" + event.giftId + "|" + event.diamonds + "|" + event.text;
        return null;
    }

    public synchronized boolean offer(LiveEvent event) {
        String key = key(event);
        LiveEvent pending = key == null ? null : counters.get(key);
        if (pending != null && pending.amount <= Long.MAX_VALUE - event.amount) {
            pending.amount += event.amount;
            return true;
        }
        if (queue.size() >= capacity) return false;
        queue.addLast(event);
        if (key != null) counters.put(key, event);
        return true;
    }

    public synchronized LiveEvent poll() {
        LiveEvent event = queue.pollFirst();
        if (event != null) {
            String key = key(event);
            if (key != null) counters.remove(key, event);
            notifyAll();
        }
        return event;
    }

    public synchronized int size() { return queue.size(); }

    public synchronized void awaitCapacity() throws InterruptedException {
        if (queue.size() >= capacity) wait(100);
    }

    public synchronized void clear() {
        queue.clear();
        counters.clear();
        notifyAll();
    }
}
