package rttt.client.game;

public final class LiveEvent {
    public enum Type { LIKE, GIFT, CHAT, MOB, METERS, ULTRA_PLUS, ULTRA_MINUS, VICTORY, RESTART, RESET_STATS, FOLLOW, SUBSCRIBE, SHARE }

    public final Type type;
    public final String user;
    public final boolean test;
    public String text = "";
    public long amount = 1;
    public long giftId = 0;
    public long diamonds = 0;
    public String effect = null;

    public LiveEvent(Type type, String user, boolean test) {
        this.type = type;
        this.user = (user == null || user.isBlank()) ? "зритель" : user.trim();
        this.test = test;
    }

    public static LiveEvent like(String user, long count, boolean test) {
        LiveEvent e = new LiveEvent(Type.LIKE, user, test);
        e.amount = Math.max(1, count);
        return e;
    }

    public static LiveEvent gift(String user, String name, long count, long id, long diamonds, boolean test) {
        LiveEvent e = new LiveEvent(Type.GIFT, user, test);
        e.text = name == null ? "" : name;
        e.amount = Math.max(1, count);
        e.giftId = id;
        e.diamonds = diamonds;
        return e;
    }

    public static LiveEvent chat(String user, String message, boolean test) {
        LiveEvent e = new LiveEvent(Type.CHAT, user, test);
        e.text = message == null ? "" : message;
        return e;
    }
}
