package rttt.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import rttt.RTTT;
import rttt.client.CaptureWindow;
import rttt.client.game.Config;
import rttt.client.game.Effects;
import rttt.client.game.Game;
import rttt.client.game.LiveEvent;
import rttt.client.game.MobCatalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class PanelServer {
    private final Game game;
    private final TikTokConnector live;
    private final BridgeProcess bridge;
    private HttpServer server;
    private int port;

    public PanelServer(Game game, TikTokConnector live) {
        this.game = game;
        this.live = live;
        this.bridge = new BridgeProcess(() -> game.runOnGameThread(() -> {
            game.cfg.liveEnabled = true;
            game.cfg.liveWsUrl = BridgeProcess.URL;
            game.cfg.save();
            live.reconnect();
        }));
    }

    public void start() {
        port = game.cfg.panelPort;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
            server.setExecutor(Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "RTTT-Panel");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/", this::handle);
            server.start();
            RTTT.LOG.info("RTTT panel: http://127.0.0.1:{}/", port);
        } catch (IOException e) {
            RTTT.LOG.error("Could not start RTTT panel on port " + port, e);
        }
    }

    public void stop() {
        bridge.shutdown();
        if (server != null) server.stop(0);
    }

    public int port() {
        return port;
    }

    private static Map<String, String> query(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String part : q.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            m.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void json(HttpExchange ex, Object o) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", Config.GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (!allowedHost(ex.getRequestHeaders().getFirst("Host"))) {
                send(ex, 403, "text/plain; charset=utf-8", "Локальная панель: недопустимый Host".getBytes(StandardCharsets.UTF_8));
                return;
            }
            boolean mutation = method.equals("POST") || path.equals("/api/action") || path.equals("/api/event");
            if (mutation && !allowedOrigin(ex)) {
                send(ex, 403, "text/plain; charset=utf-8", "Запрос разрешён только из локальной панели".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (method.equals("OPTIONS")) {
                send(ex, 204, "text/plain", new byte[0]);
                return;
            }
            switch (path) {
                case "/", "/index.html" -> {
                    try (InputStream in = PanelServer.class.getResourceAsStream("/assets/rttt/web/index.html")) {
                        byte[] data = in == null ? "panel missing".getBytes() : in.readAllBytes();
                        send(ex, 200, "text/html; charset=utf-8", data);
                    }
                }
                case "/api/status", "/api/state" -> {
                    JsonObject status = game.status();
                    status.add("bridge", bridge.status());
                    status.add("capture", CaptureWindow.status());
                    json(ex, status);
                }
                case "/api/config" -> {
                    if (method.equals("POST")) {
                        Config next = Config.fromJson(body(ex), game.cfg);
                        boolean urlChanged = !next.liveWsUrl.equals(game.cfg.liveWsUrl) || next.liveEnabled != game.cfg.liveEnabled;
                        next.save();
                        game.runOnGameThread(() -> {
                            game.cfg = next;
                            if (urlChanged) live.reconnect();
                        });
                        JsonObject ok = new JsonObject();
                        ok.addProperty("ok", true);
                        ok.addProperty("restartPanel", next.panelPort != port);
                        json(ex, ok);
                    } else {
                        json(ex, game.cfg.toJson());
                    }
                }
                case "/api/meta" -> {
                    JsonObject o = new JsonObject();
                    JsonArray mobs = new JsonArray();
                    for (MobCatalog.Entry e : MobCatalog.BY_ID.values()) {
                        JsonObject m = new JsonObject();
                        m.addProperty("id", e.id);
                        m.addProperty("ru", e.ru);
                        m.addProperty("kind", e.kind.name());
                        m.addProperty("size", e.size.name());
                        m.addProperty("helper", e.helper);
                        JsonArray names = new JsonArray();
                        for (String n : e.names) names.add(n);
                        m.add("names", names);
                        mobs.add(m);
                    }
                    o.add("mobs", mobs);
                    JsonArray pos = new JsonArray();
                    for (String s : Effects.POSITIVE) pos.add(s);
                    JsonArray neg = new JsonArray();
                    for (String s : Effects.NEGATIVE) neg.add(s);
                    o.add("positiveEffects", pos);
                    o.add("negativeEffects", neg);
                    o.addProperty("port", port);
                    json(ex, o);
                }
                case "/api/action" -> {
                    if (!method.equals("POST")) {
                        ex.getResponseHeaders().set("Allow", "POST");
                        send(ex, 405, "text/plain; charset=utf-8", "Используйте POST".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    JsonObject b = JsonParser.parseString(body(ex)).getAsJsonObject();
                    String action = s(b, "action", "");
                    doAction(action, b);
                    JsonObject ok = new JsonObject();
                    ok.addProperty("ok", true);
                    json(ex, ok);
                }
                case "/api/test" -> {
                    JsonObject b = JsonParser.parseString(body(ex)).getAsJsonObject();
                    game.submit(eventFrom(b, true));
                    JsonObject ok = new JsonObject();
                    ok.addProperty("ok", true);
                    json(ex, ok);
                }
                case "/api/event" -> {
                    if (method.equals("POST")) {
                        String txt = body(ex);
                        JsonObject b = JsonParser.parseString(txt).getAsJsonObject();
                        if (b.has("event")) live.handle(txt);
                        else game.submit(eventFrom(b, b.has("test") && b.get("test").getAsBoolean()));
                    } else {
                        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
                        JsonObject b = new JsonObject();
                        q.forEach(b::addProperty);
                        game.submit(eventFrom(b, "1".equals(q.get("test"))));
                    }
                    JsonObject ok = new JsonObject();
                    ok.addProperty("ok", true);
                    json(ex, ok);
                }
                default -> {
                    byte[] img = path.startsWith("/gift/") ? giftIcon(path.substring(6)) : path.startsWith("/mc/") ? vanillaTexture(path.substring(4)) : null;
                    if (img != null) {
                        ex.getResponseHeaders().set("Cache-Control", "max-age=86400");
                        ex.getResponseHeaders().set("Content-Type", "image/png");
                        ex.sendResponseHeaders(200, img.length);
                        try (OutputStream os = ex.getResponseBody()) {
                            os.write(img);
                        }
                    } else {
                        send(ex, 404, "text/plain", "not found".getBytes());
                    }
                }
            }
        } catch (Throwable t) {
            try {
                int code = t instanceof IllegalArgumentException || t instanceof IllegalStateException ? 400 : 500;
                send(ex, code, "text/plain; charset=utf-8", ("error: " + t.getMessage()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private boolean allowedHost(String host) {
        return host != null && (host.equals("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port)
                || host.equals("[::1]:" + port));
    }

    private boolean allowedOrigin(HttpExchange ex) {
        if ("cross-site".equalsIgnoreCase(ex.getRequestHeaders().getFirst("Sec-Fetch-Site"))) return false;
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true; // Local native integrations do not send browser Origin headers.
        return origin.equals("http://127.0.0.1:" + port) || origin.equalsIgnoreCase("http://localhost:" + port)
                || origin.equals("http://[::1]:" + port);
    }

    private static final Map<String, byte[]> IMG_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final byte[] NONE = new byte[0];

    private static byte[] giftIcon(String name) {
        String k = rttt.client.hud.GiftImages.key(URLDecoder.decode(name.replace(".png", ""), StandardCharsets.UTF_8));
        byte[] b = IMG_CACHE.computeIfAbsent("g/" + k, x -> {
            try (InputStream in = PanelServer.class.getResourceAsStream("/assets/rttt/gifts/" + k + ".png")) {
                if (in != null) return in.readAllBytes();
            } catch (IOException ignored) {
            }
            try {
                java.nio.file.Path p = Config.dir().resolve("gift_images").resolve(k + ".png");
                if (java.nio.file.Files.exists(p)) return java.nio.file.Files.readAllBytes(p);
            } catch (IOException ignored) {
            }
            return NONE;
        });
        if (b == NONE) IMG_CACHE.remove("g/" + k);
        return b.length == 0 ? null : b;
    }

    private static byte[] vanillaTexture(String rel) {
        if (!rel.matches("[a-z0-9_/]+\\.png")) return null;
        byte[] b = IMG_CACHE.computeIfAbsent("mc/" + rel, x -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(net.minecraft.resources.Identifier.withDefaultNamespace("textures/" + rel));
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) {
                        return in.readAllBytes();
                    }
                }
            } catch (Throwable ignored) {
            }
            return NONE;
        });
        return b.length == 0 ? null : b;
    }

    private static String s(JsonObject b, String k, String def) {
        return b.has(k) && !b.get(k).isJsonNull() ? b.get(k).getAsString() : def;
    }

    private static long l(JsonObject b, String k, long def) {
        try {
            return b.has(k) ? Long.parseLong(b.get(k).getAsString().trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private LiveEvent eventFrom(JsonObject b, boolean test) {
        String type = s(b, "type", "").toLowerCase();
        String user = s(b, "user", test ? "тест" : "зритель");
        return switch (type) {
            case "like", "likes" -> LiveEvent.like(user, l(b, "count", 1), test);
            case "gift" -> LiveEvent.gift(user, s(b, "name", "Rose"), l(b, "count", 1), l(b, "giftId", 0), l(b, "diamonds", 0), test);
            case "chat" -> LiveEvent.chat(user, s(b, "text", s(b, "name", "")), test);
            case "mob" -> {
                LiveEvent e = new LiveEvent(LiveEvent.Type.MOB, user, test);
                e.text = s(b, "name", s(b, "id", "zombie"));
                yield e;
            }
            case "meters" -> {
                LiveEvent e = new LiveEvent(LiveEvent.Type.METERS, user, test);
                e.amount = l(b, "count", l(b, "meters", 100));
                e.effect = b.has("effect") ? s(b, "effect", null) : null;
                e.text = s(b, "label", "");
                yield e;
            }
            case "ultraplus", "ultra_plus" -> new LiveEvent(LiveEvent.Type.ULTRA_PLUS, user, test);
            case "ultraminus", "ultra_minus" -> new LiveEvent(LiveEvent.Type.ULTRA_MINUS, user, test);
            case "victory" -> new LiveEvent(LiveEvent.Type.VICTORY, user, test);
            case "follow" -> new LiveEvent(LiveEvent.Type.FOLLOW, user, test);
            case "subscribe" -> new LiveEvent(LiveEvent.Type.SUBSCRIBE, user, test);
            case "share" -> new LiveEvent(LiveEvent.Type.SHARE, user, test);
            default -> null;
        };
    }

    private void doAction(String action, JsonObject b) {
        switch (action) {
            case "start" -> game.runOnGameThread(game::start);
            case "stop" -> game.runOnGameThread(game::stop);
            case "restart" -> game.submit(new LiveEvent(LiveEvent.Type.RESTART, "панель", true));
            case "resetStats" -> game.submit(new LiveEvent(LiveEvent.Type.RESET_STATS, "панель", true));
            case "reconnect" -> live.reconnect();
            case "resetTop" -> game.resetTop();
            case "bridgeStart" -> {
                String username = BridgeProcess.normalizeUsername(s(b, "username", game.cfg.liveUsername));
                bridge.start(username);
                game.runOnGameThread(() -> {
                    game.cfg.liveUsername = username;
                    game.cfg.save();
                });
            }
            case "bridgeStop" -> bridge.stop();
            case "timeOfDay" -> {
                String mode = s(b, "value", "AUTO").trim().toUpperCase(java.util.Locale.ROOT);
                if (!mode.equals("DAY") && !mode.equals("NIGHT") && !mode.equals("AUTO"))
                    throw new IllegalArgumentException("Время суток: DAY, NIGHT или AUTO");
                game.runOnGameThread(() -> game.setTimeOfDay(mode));
            }
            case "weather" -> game.runOnGameThread(() -> {
                String w = s(b, "value", "CLEAR");
                try {
                    game.weather.force(rttt.client.game.Weather.State.valueOf(w));
                } catch (Exception ignored) {
                }
            });
            case "window" -> {
                int w = (int) l(b, "w", 1080), h = (int) l(b, "h", 1920);
                boolean fit = !b.has("fitDisplay") || b.get("fitDisplay").getAsBoolean();
                Minecraft.getInstance().execute(() -> CaptureWindow.apply(w, h, fit));
            }
            default -> {
            }
        }
    }
}
