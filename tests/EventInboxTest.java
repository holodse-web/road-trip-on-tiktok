import rttt.client.game.EventInbox;
import rttt.client.game.LiveEvent;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone regression checks: compile with EventInbox.java and LiveEvent.java. */
public final class EventInboxTest {
    public static void main(String[] args) throws Exception {
        pausedBurstCoalescesWithoutLosingUnits();
        capacityAppliesBackpressureAndDrainPreservesEveryEvent();
        clearWakesWaitingProducerAndResetsCounters();
        overflowKeepsSeparateCounters();
        System.out.println("PASS: 101,000 paused counters; 12,000 unique events across capacity 4096; clear/wakeup; long overflow.");
    }

    private static void pausedBurstCoalescesWithoutLosingUnits() {
        EventInbox inbox = new EventInbox(32);
        Map<String, Long> expected = new HashMap<>();
        for (int i = 0; i < 50_500; i++) {
            String viewer = "viewer-" + i % 8;
            LiveEvent likes = LiveEvent.like(viewer, i % 7 + 1, false);
            LiveEvent gift = LiveEvent.gift(viewer, "Rose", i % 3 + 1, 5655, i % 2 + 1, false);
            add(expected, likes);
            add(expected, gift);
            check(inbox.offer(likes), "paused like rejected");
            check(inbox.offer(gift), "paused gift rejected");
            check(inbox.size() <= 32, "counter queue grew beyond its cap");
        }
        LiveEvent live = LiveEvent.like("same-viewer", 11, false);
        LiveEvent test = LiveEvent.like("same-viewer", 13, true);
        add(expected, live);
        add(expected, test);
        check(inbox.offer(live) && inbox.offer(test), "live/test identity insert failed");
        Map<String, Long> actual = new HashMap<>();
        LiveEvent next;
        while ((next = inbox.poll()) != null) add(actual, next);
        check(expected.equals(actual), "paused burst changed gift/like totals or their identities");
        check(inbox.size() == 0, "drained queue is not empty");
    }

    private static void capacityAppliesBackpressureAndDrainPreservesEveryEvent() throws Exception {
        final int capacity = 4096, events = 12_000;
        EventInbox inbox = new EventInbox(capacity);
        CountDownLatch saturated = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < events; i++) {
                    LiveEvent event = i % 2 == 0
                            ? LiveEvent.like("viewer-" + i, i % 7 + 1, false)
                            : LiveEvent.gift("viewer-" + i, "Rose", i % 3 + 1, 5655, 1, false);
                    while (!inbox.offer(event)) {
                        saturated.countDown();
                        inbox.awaitCapacity();
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "event-inbox-regression-producer");
        producer.setDaemon(true);
        producer.start();
        check(saturated.await(2, TimeUnit.SECONDS), "producer never reached capacity");
        check(inbox.size() == capacity, "paused queue exceeded or lost its capacity");
        check(!inbox.offer(LiveEvent.chat("overflow", "not accepted", false)), "over-cap event was accepted");
        // No consumer runs during this interval, as while Minecraft is paused.
        producer.join(120);
        check(producer.isAlive() && inbox.size() == capacity, "paused producer did not wait at the cap");

        long likeTotal = 0, giftTotal = 0, expectedLikes = 0, expectedGifts = 0;
        for (int i = 0; i < events; i++) {
            if (i % 2 == 0) expectedLikes += i % 7 + 1;
            else expectedGifts += i % 3 + 1;
        }
        int received = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (received < events && System.nanoTime() < deadline) {
            LiveEvent event = inbox.poll();
            if (event == null) {
                check(failure.get() == null, "producer failed: " + failure.get());
                Thread.yield();
                continue;
            }
            check(event.user.equals("viewer-" + received), "event was lost, duplicated, or reordered at " + received);
            if (event.type == LiveEvent.Type.LIKE) likeTotal += event.amount;
            else if (event.type == LiveEvent.Type.GIFT) giftTotal += event.amount;
            else throw new AssertionError("unexpected event type: " + event.type);
            received++;
            check(inbox.size() <= capacity, "concurrent queue exceeded cap");
        }
        producer.join(2_000);
        check(!producer.isAlive() && failure.get() == null, "producer failed to finish");
        check(received == events && inbox.size() == 0, "unique burst was not fully delivered");
        check(likeTotal == expectedLikes && giftTotal == expectedGifts, "unique burst lost gift or like units");
    }

    private static void clearWakesWaitingProducerAndResetsCounters() throws Exception {
        EventInbox inbox = new EventInbox(1);
        check(inbox.offer(LiveEvent.like("before-clear", 500, false)), "initial insert failed");
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                LiveEvent other = LiveEvent.chat("waiting-viewer", "message", false);
                while (!inbox.offer(other)) {
                    waiting.countDown();
                    inbox.awaitCapacity();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "event-inbox-clear-producer");
        producer.setDaemon(true);
        producer.start();
        check(waiting.await(2, TimeUnit.SECONDS), "clear test producer did not wait");
        inbox.clear();
        producer.join(2_000);
        check(!producer.isAlive() && failure.get() == null, "clear failed to release the waiting producer");
        LiveEvent resumed = inbox.poll();
        check(resumed != null && resumed.user.equals("waiting-viewer") && inbox.poll() == null, "clear retained old events or lost new event");
        check(inbox.offer(LiveEvent.like("before-clear", 3, false)), "post-clear counter rejected");
        check(inbox.poll().amount == 3, "clear retained a stale coalescing counter");
    }

    private static void overflowKeepsSeparateCounters() {
        EventInbox inbox = new EventInbox(3);
        check(inbox.offer(LiveEvent.like("viewer", Long.MAX_VALUE - 4, false)), "large counter rejected");
        check(inbox.offer(LiveEvent.like("viewer", 10, false)), "overflow counter rejected");
        check(inbox.offer(LiveEvent.like("viewer", 2, false)), "overflow tail rejected");
        BigInteger total = BigInteger.ZERO;
        LiveEvent event;
        while ((event = inbox.poll()) != null) {
            check(event.amount > 0, "counter wrapped negative");
            total = total.add(BigInteger.valueOf(event.amount));
        }
        check(total.equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(8))), "overflow changed the exact total");
    }

    private static void add(Map<String, Long> totals, LiveEvent event) {
        String key = event.type + "|" + event.user + "|" + event.test + "|" + event.text + "|" + event.giftId + "|" + event.diamonds;
        totals.merge(key, event.amount, Long::sum);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
