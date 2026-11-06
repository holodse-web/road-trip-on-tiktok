package rttt.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rttt.RTTT;
import rttt.client.game.Config;
import rttt.client.game.Game;
import rttt.client.game.LiveEvent;
import rttt.client.hud.GiftImages;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class TikTokConnector {
    private static final int MAX_MESSAGE_CHARS = 2 * 1024 * 1024;
    private final Game game;
    private volatile WebSocket socket;
    private volatile boolean running = true;
    private volatile String url;
    private Thread thread;
    private final AtomicLong generation = new AtomicLong();
    private final Object wake = new Object();
    private record Streak(long count, boolean ended, long time) {}
    private final Map<String, Streak> streaks = new LinkedHashMap<>();
    private final Map<String, Long> seen = new LinkedHashMap<>();

    public TikTokConnector(Game game) { this.game = game; }

    public synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        running = true;
        thread = new Thread(this::loop, "RTTT-TikTok");
        thread.setDaemon(true);
        thread.start();
    }

    public void reconnect() {
        generation.incrementAndGet();
        WebSocket previous = socket;
        socket = null;
        if (previous != null) previous.abort();
        game.liveConnected = false;
        synchronized (wake) { wake.notifyAll(); }
    }

    public void shutdown() {
        running = false;
        reconnect();
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        while (running) {
            try {
                if (!game.cfg.liveEnabled) {
                    if (socket != null) reconnect();
                    game.liveConnected = false;
                    game.liveStatus = "выключено в настройках";
                } else {
                    String wanted = game.cfg.liveWsUrl;
                    if (socket != null && !wanted.equals(url)) reconnect();
                    if (socket == null) {
                        url = wanted;
                        long token = generation.incrementAndGet();
                        game.liveStatus = "подключение к " + wanted + " …";
                        CompletableFuture<WebSocket> pending = null;
                        try {
                            pending = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(4))
                                    .buildAsync(URI.create(wanted), new Listener(token));
                            WebSocket opened = pending.get(6, TimeUnit.SECONDS);
                            if (!running || token != generation.get()) opened.abort();
                        } catch (Throwable error) {
                            if (pending != null) pending.cancel(true);
                            if (token == generation.get()) {
                                generation.incrementAndGet();
                                socket = null;
                                game.liveConnected = false;
                                game.liveStatus = "нет связи с " + wanted + " (запустите мост или TikFinity)";
                            }
                        }
                    }
                }
                synchronized (wake) { wake.wait(socket == null ? 5000 : 1000); }
            } catch (InterruptedException interrupted) {
                if (!running) return;
            } catch (Throwable error) { RTTT.LOG.warn("TikTok connector: {}", error.toString()); }
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final long token;
        private final StringBuilder partial = new StringBuilder();
        Listener(long token) { this.token = token; }
        private boolean current(WebSocket webSocket) { return running && token == generation.get() && socket == webSocket; }

        @Override public void onOpen(WebSocket webSocket) {
            if (!running || token != generation.get()) { webSocket.abort(); return; }
            socket = webSocket;
            game.liveConnected = true; // TikFinity compatibility; bridgeStatus supplies actual upstream state.
            game.liveStatus = "подключено: " + url;
            game.log("🔌 Соединение с TikTok-источником установлено");
            webSocket.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (!current(webSocket)) return CompletableFuture.completedFuture(null);
            if (partial.length() + data.length() > MAX_MESSAGE_CHARS) {
                partial.setLength(0);
                webSocket.abort();
                if (current(webSocket)) {
                    socket = null;
                    game.liveConnected = false;
                    game.liveStatus = "слишком большое сообщение — переподключение…";
                }
                return CompletableFuture.completedFuture(null);
            }
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                try { handle(message, (event) -> game.submitLive(event, () -> current(webSocket))); }
                catch (Throwable error) { RTTT.LOG.debug("Bad TikTok message: {}", error.toString()); }
            }
            if (current(webSocket)) webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
            if (current(webSocket)) {
                socket = null;
                game.liveConnected = false;
                game.liveStatus = "соединение закрыто — переподключение…";
                game.log("🔌 TikTok-источник отключён");
                synchronized (wake) { wake.notifyAll(); }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket webSocket, Throwable error) {
            if (current(webSocket)) {
                socket = null;
                game.liveConnected = false;
                game.liveStatus = "ошибка соединения — переподключение…";
                synchronized (wake) { wake.notifyAll(); }
            }
        }
    }

    private static String str(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static JsonObject obj(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonObject()) return value.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static long num(JsonObject object, long fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) try { return value.getAsLong(); } catch (Exception ignored) { }
        }
        return fallback;
    }

    private static boolean bool(JsonObject object, String key) {
        String value = str(object, key);
        return value.equalsIgnoreCase("true") || value.equals("1");
    }

    private static String chatText(JsonObject object, int depth) {
        if (depth > 3) return "";
        for (String key : new String[]{"content", "comment", "message", "text"}) {
            JsonElement value = object.get(key);
            if (value == null) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isEmpty()) return value.getAsString();
            if (value.isJsonObject()) {
                String nested = chatText(value.getAsJsonObject(), depth + 1);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private synchronized boolean duplicate(String event, JsonObject data) {
        String id = str(obj(data, "common"), "msgId");
        if (id.isEmpty()) id = str(data, "msgId", "messageId");
        if (id.isEmpty() || id.equals("0")) return false;
        String key = event + "|" + str(obj(data, "common"), "roomId") + "|" + id;
        if (event.equals("gift")) key += "|" + num(data, 1, "repeatCount", "count") + "|" + bool(data, "repeatEnd");
        long now = System.currentTimeMillis();
        Long old = seen.remove(key);
        seen.put(key, now);
        var entries = seen.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Long> oldest = entries.next();
            if (seen.size() <= 20000 && now - oldest.getValue() <= 600_000L) break;
            entries.remove();
        }
        return old != null && now - old < 600_000L;
    }

    public void handle(String message) { handle(message, game::submit); }

    private void handle(String message, Consumer<LiveEvent> submit) {
        JsonElement root = JsonParser.parseString(message);
        if (!root.isJsonObject()) return;
        JsonObject outer = root.getAsJsonObject();
        String event = str(outer, "event", "type").toLowerCase(Locale.ROOT);
        JsonObject data = outer.has("data") && outer.get("data").isJsonObject() ? outer.getAsJsonObject("data") : outer;
        if (duplicate(event, data)) return;
        JsonObject user = data.has("user") && data.get("user").isJsonObject() ? data.getAsJsonObject("user") : data;
        String uid = str(user, "userId", "id", "idStr", "uniqueId", "displayId", "username");
        String nick = str(user, "nickname", "nickName", "displayName", "uniqueId", "displayId", "username");
        if (nick.isEmpty()) nick = uid;
        switch (event) {
            case "bridgestatus" -> {
                game.liveConnected = bool(data, "connected");
                game.liveStatus = str(data, "status");
            }
            case "like" -> {
                long count = num(data, 1, "likeCount", "count");
                if (count > 0) submit.accept(LiveEvent.like(nick, count, false));
            }
            case "chat", "comment" -> {
                String text = chatText(data, 0);
                if (!text.isEmpty()) submit.accept(LiveEvent.chat(nick, text, false));
            }
            case "gift" -> {
                JsonObject detail = obj(data, "gift", "giftDetails");
                String name = str(data, "giftName", "name");
                if (name.isEmpty()) name = str(detail, "giftName", "name");
                long id = num(data, num(detail, 0, "id"), "giftId", "id");
                if (name.isEmpty()) name = "Gift " + id;
                long diamonds = num(data, num(detail, 0, "diamondCount", "diamond_count"), "diamondCount", "diamonds");
                long repeat = Math.max(1, num(data, 1, "repeatCount", "count"));
                long type = num(data, num(detail, 0, "giftType", "type"), "giftType");
                long delta = repeat;
                if (bool(data, "giftDelta")) delta = Math.max(0, num(data, 0, "giftCountDelta"));
                else if (type == 1) {
                    String group = str(data, "groupId", "streakId", "comboId");
                    if (group.equals("0")) group = "";
                    String key = str(obj(data, "common"), "roomId") + "|" + (uid.isEmpty() ? nick : uid) + "|" + id + "|" + name + "|" + group;
                    boolean end = bool(data, "repeatEnd");
                    long now = System.currentTimeMillis();
                    synchronized (streaks) {
                        Streak old = streaks.get(key);
                        long previous = old == null || now - old.time() > 600_000L ? 0 : old.count();
                        if (group.isEmpty() && old != null && !end && (old.ended() || repeat < previous)) previous = 0;
                        delta = Math.max(0, repeat - previous);
                        streaks.remove(key);
                        streaks.put(key, new Streak(Math.max(previous, repeat), end, now));
                        while (streaks.size() > 5000) streaks.remove(streaks.keySet().iterator().next());
                    }
                }
                String image = str(data, "giftPictureUrl", "giftImage", "imageUrl", "image");
                if (image.isEmpty()) image = picture(detail);
                if (!image.isEmpty()) giftPicture(name, image);
                if (delta > 0) submit.accept(LiveEvent.gift(nick, name, delta, id, diamonds, false));
            }
            case "follow" -> submit.accept(new LiveEvent(LiveEvent.Type.FOLLOW, nick, false));
            case "subscribe", "superfan", "superfanjoin" -> submit.accept(new LiveEvent(LiveEvent.Type.SUBSCRIBE, nick, false));
            case "share" -> submit.accept(new LiveEvent(LiveEvent.Type.SHARE, nick, false));
            case "social" -> {
                String label = str(data, "displayType", "label") + " " + str(obj(obj(data, "common"), "displayText"), "key");
                label = label.toLowerCase(Locale.ROOT);
                if (label.contains("follow")) submit.accept(new LiveEvent(LiveEvent.Type.FOLLOW, nick, false));
                else if (label.contains("share")) submit.accept(new LiveEvent(LiveEvent.Type.SHARE, nick, false));
            }
            case "giftlist", "gifts" -> {
                JsonElement gifts = data.has("gifts") ? data.get("gifts") : outer.get("data");
                if (gifts == null || !gifts.isJsonArray()) return;
                int count = 0;
                for (JsonElement entry : gifts.getAsJsonArray()) {
                    if (count++ >= 5000) break;
                    if (!entry.isJsonObject()) continue;
                    JsonObject g = entry.getAsJsonObject();
                    String name = str(g, "name", "giftName");
                    String image = str(g, "giftPictureUrl", "imageUrl");
                    if (image.isEmpty()) image = picture(g);
                    if (!name.isEmpty() && !image.isEmpty()) giftPicture(name, image);
                }
            }
            default -> { }
        }
    }

    private static String picture(JsonObject gift) {
        for (String key : new String[]{"giftImage", "image", "icon", "giftPicture"}) {
            JsonElement element = gift.get(key);
            if (element == null) continue;
            if (element.isJsonPrimitive()) return element.getAsString();
            if (!element.isJsonObject()) continue;
            JsonObject image = element.getAsJsonObject();
            String direct = str(image, "giftPictureUrl", "url");
            if (!direct.isEmpty()) return direct;
            for (String listKey : new String[]{"urlList", "url_list", "urls"}) {
                JsonElement list = image.get(listKey);
                if (list != null && list.isJsonArray() && !list.getAsJsonArray().isEmpty()) {
                    JsonElement first = list.getAsJsonArray().get(0);
                    if (first.isJsonPrimitive()) return first.getAsString();
                }
            }
        }
        return "";
    }

    private boolean giftPicture(String name, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        Config cfg = game.cfg;
        boolean known = false;
        for (Config.GiftRule rule : cfg.gifts) {
            if (rule != null && rule.name != null && rule.name.equalsIgnoreCase(name)) {
                known = true;
                if (rule.image == null || rule.image.isBlank()) rule.image = url;
            }
        }
        for (String value : (cfg.ultraPlusGift + "," + cfg.ultraMinusGift).split(","))
            if (value.trim().equalsIgnoreCase(name)) known = true;
        if (known) GiftImages.request(name, url);
        return known;
    }
}
