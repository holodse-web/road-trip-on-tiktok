package rttt.client.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import rttt.RTTT;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final int VERSION = 9;
    public int configVersion = 0;

    public boolean autoStart = true;
    public boolean showInfoCard = true;
    public int infoCardPauseSeconds = 60;
    public String autoStartWorldMatch = "rttt,road trip,алекс,alex,ttmod,довези";
    public int panelPort = 8765;
    public long goal = 999_999;
    public double metersPerBlock = 1.0;
    public double cruiseSpeed = 11.5;
    public double dayLengthMinutes = 16;
    public String timeOfDay = "AUTO";
    public boolean autoWeather = true;
    public boolean demoMode = false;
    public boolean hostEnabled = true;
    public boolean topHelpersEnabled = true;
    public boolean hordeEnabled = true;
    public int hordeViewers = 5;
    public int hordeWindowSeconds = 10;
    public int hordeSize = 3;
    public boolean quietChat = true;
    public int quietSeconds = 120;
    public int quietIntervalSeconds = 60;
    public boolean quietAmbientMobs = true;
    public double cameraDistance = 19;
    public double viewWidth = 16;
    public int carColor = 0xD7263D;
    public double cameraHeight = 0;

    // One transform for the complete upper overlay. Offsets are screen percentages.
    public double hudOffsetX = 0;
    public double hudOffsetY = 0;
    public double hudScale = 1.0;

    /**
     * Free layout: every on-screen element can be moved, resized, faded or hidden on its own.
     * Offsets are percentages of the frame, measured from the element's default position.
     */
    public Map<String, HudElement> hudLayout = new LinkedHashMap<>();
    public int feedRows = 3;
    public int feedSeconds = 14;
    public boolean giftBanner = true;

    public static final String[] HUD_ELEMENTS = {"counter", "toast", "likes", "wins", "test", "queue", "card", "banner", "feed", "top", "bubble"};

    public static class HudElement {
        public double x = 0, y = 0, scale = 1, opacity = 1;
        public boolean visible = true;
    }

    public HudElement hud(String id) {
        HudElement e = hudLayout.get(id);
        return e != null ? e : DEFAULT_ELEMENT;
    }

    private static final HudElement DEFAULT_ELEMENT = new HudElement();

    public boolean colorOnFollow = true;
    public boolean colorOnSubscribe = true;
    public boolean colorOnShare = false;
    public int colorCooldownSeconds = 4;

    public int likesPerTrigger = 500;
    public long metersPerLikeTrigger = 500;

    public List<GiftRule> gifts = new ArrayList<>();
    public String ultraPlusGift = "TikTok Universe,Universe";
    public String ultraMinusGift = "Lion";
    /** Pictures for the ultra gifts, which are not in the gift table. A live event's own picture replaces them. */
    public String ultraPlusImage = "https://cdn.tik.tools/gifts/8aa0babc31d440f9f090cd3b.png";
    public String ultraMinusImage = CDN + "maliva/webcast-va/4fb89af2082a290b37d704e20f4fe729~tplv-obj.png";
    public long ultraAmount = 999_999;
    public boolean unknownGiftFallback = true;
    public long metersPerDiamond = 10;
    public boolean giftQueue = true;
    public double giftSpotlightScale = 1.0;
    public double giftCooldownSeconds = 0.3;
    public int maxQueuedGifts = 60;

    public int chatCooldownSeconds = 8;
    public int maxPendingPerViewer = 2;
    public int maxActiveMobs = 1;
    public int maxQueuedMobs = 40;
    public int strongMobCooldownSeconds = 30;
    public boolean chatMatchInsideMessage = true;
    public double mobPenaltyMultiplier = 1.0;
    public Map<String, MobSetting> mobs = new LinkedHashMap<>();

    public double volMaster = 0.9;
    public double volEngine = 0.55;
    public double volRoad = 0.45;
    public double volImpacts = 0.8;
    public double volMobs = 0.7;
    public double volGifts = 0.8;
    public double volVictory = 1.0;

    public boolean liveEnabled = true;
    public String liveWsUrl = "ws://127.0.0.1:21213/";
    public String liveUsername = "";

    public static class GiftRule {
        public String name;
        public String ru = "";
        public int coins = 1;
        public long meters;
        public String effect;
        public String image = "";
        public boolean showInOverlay = true;

        public GiftRule() {}

        public GiftRule(String name, String ru, int coins, long meters, String effect, String image) {
            this.name = name;
            this.ru = ru;
            this.coins = coins;
            this.meters = meters;
            this.effect = effect;
            this.image = image;
        }
    }

    public static class MobSetting {
        public boolean enabled = true;
        public long meters;
        public double power;
    }

    private static final String CDN = "https://p16-webcast.tiktokcdn.com/img/";

    public static List<GiftRule> defaultGifts() {
        List<GiftRule> g = new ArrayList<>();
        g.add(new GiftRule("Rose", "Роза", 1, 150, "SPARKLE", CDN + "maliva/webcast-va/eba3a9bb85c33e017f3648eaf88d7189~tplv-obj.png"));
        g.add(new GiftRule("TikTok", "ТикТок", 1, 150, "HEARTS", CDN + "maliva/webcast-va/802a21ae29f9fae5abe3693de9f874bd~tplv-obj.png"));
        g.add(new GiftRule("Heart Me", "Сердце мне", 1, 200, "HEARTS", CDN + "maliva/webcast-va/d56945782445b0b8c8658ed44f894c7b~tplv-obj.png"));
        g.add(new GiftRule("GG", "GG", 1, -150, "KNOCK", CDN + "maliva/webcast-va/3f02fa9594bd1495ff4e8aa5ae265eef~tplv-obj.png"));
        g.add(new GiftRule("Ice Cream Cone", "Мороженое", 1, -200, "FREEZE", CDN + "maliva/webcast-va/968820bc85e274713c795a6aef3f7c67~tplv-obj.png"));
        g.add(new GiftRule("Finger Heart", "Сердечко", 5, 700, "HEARTS", CDN + "maliva/webcast-va/a4c4dc437fd3a6632aba149769491f49.png~tplv-obj.png"));
        g.add(new GiftRule("Rosa", "Роза-букет", 10, 1500, "BOOST", CDN + "maliva/webcast-va/eb77ead5c3abb6da6034d3cf6cfeb438~tplv-obj.png"));
        g.add(new GiftRule("Friendship Necklace", "Ожерелье дружбы", 10, -1500, "KNOCK", CDN + "maliva/webcast-va/resource/e033c3f28632e233bebac1668ff66a2f.png~tplv-obj.png"));
        g.add(new GiftRule("Perfume", "Духи", 20, 3000, "RAINBOW", CDN + "maliva/webcast-va/20b8f61246c7b6032777bb81bf4ee055~tplv-obj.png"));
        g.add(new GiftRule("Doughnut", "Пончик", 30, -4000, "ANVIL", CDN + "maliva/webcast-va/4e7ad6bdf0a1d860c538f38026d4e812~tplv-obj.png"));
        g.add(new GiftRule("Paper Crane", "Журавлик", 99, -12000, "METEOR", CDN + "maliva/webcast-va/0f158a08f7886189cdabf496e8a07c21~tplv-obj.png"));
        g.add(new GiftRule("Hand Hearts", "Сердце руками", 100, 15000, "FIREWORKS", CDN + "maliva/webcast-va/6cd022271dc4669d182cad856384870f~tplv-obj.png"));
        g.add(new GiftRule("Confetti", "Конфетти", 100, 15000, "CONFETTI", CDN + "alisg/webcast-sg/resource/472bf660b0804bb37f616d173edb8a9d.png~tplv-obj.png"));
        g.add(new GiftRule("Corgi", "Корги", 299, -40000, "EXPLOSION", CDN + "maliva/webcast-va/148eef0884fdb12058d1c6897d1e02b9~tplv-obj.png"));
        g.add(new GiftRule("Money Gun", "Денежная пушка", 500, 90000, "NITRO", CDN + "maliva/webcast-va/e0589e95a2b41970f0f30f6202f5fce6~tplv-obj.png"));
        g.add(new GiftRule("Galaxy", "Галактика", 1000, 250000, "NITRO", CDN + "maliva/webcast-va/resource/79a02148079526539f7599150da9fd28.png~tplv-obj.png"));
        return g;
    }

    public static Config defaults() {
        Config c = new Config();
        c.gifts = defaultGifts();
        c.configVersion = VERSION;
        c.normalizePresentation();
        return c;
    }

    void migrate() {
        if (configVersion < 2) {
            gifts = defaultGifts();
            mobs = new LinkedHashMap<>();
            maxActiveMobs = 1;
            ultraPlusGift = "TikTok Universe,Universe";
            ultraMinusGift = "Lion";
            showInfoCard = true;
        }
        if (configVersion < 3) {
            if (cruiseSpeed <= 8.5) cruiseSpeed = 11.5;
        }
        if (configVersion < 4) {
            gifts = defaultGifts();
            if (likesPerTrigger < 500) {
                likesPerTrigger = 500;
                metersPerLikeTrigger = 500;
            }
        }
        if (configVersion < 5) {
            Map<String, Long> before = Map.ofEntries(Map.entry("Rose", 100L), Map.entry("TikTok", 100L), Map.entry("Heart Me", 150L), Map.entry("GG", -100L), Map.entry("Ice Cream Cone", -150L), Map.entry("Finger Heart", 500L), Map.entry("Rosa", 1000L), Map.entry("Friendship Necklace", -1000L), Map.entry("Perfume", 2000L), Map.entry("Doughnut", -3000L), Map.entry("Paper Crane", -8000L), Map.entry("Hand Hearts", 10000L), Map.entry("Confetti", 10000L), Map.entry("Corgi", -25000L), Map.entry("Money Gun", 50000L), Map.entry("Galaxy", 100000L));
            Map<String, Long> now = new HashMap<>();
            for (GiftRule r : defaultGifts()) now.put(r.name, r.meters);
            for (GiftRule r : gifts) {
                if (r == null || r.name == null) continue;
                Long b = before.get(r.name);
                if (b != null && b == r.meters && now.containsKey(r.name)) r.meters = now.get(r.name);
            }
        }
        if (configVersion < 6 && "алекс,alex,ttmod,довези".equals(autoStartWorldMatch)) {
            autoStartWorldMatch = "rttt,road trip,алекс,alex,ttmod,довези";
        }
        if (configVersion < 8 && mobs != null && !mobs.isEmpty()) {
            MobCatalog.softenHarmful(this);
        }
        if (configVersion < 9 && (hudOffsetX != 0 || hudOffsetY != 0 || hudScale != 1)) {
            // The old single "move everything" control becomes the same shift on each upper element.
            if (hudLayout == null) hudLayout = new LinkedHashMap<>();
            for (String id : new String[]{"counter", "toast", "likes", "wins", "test", "queue", "card", "banner"}) {
                HudElement e = hudLayout.computeIfAbsent(id, k -> new HudElement());
                e.x += hudOffsetX;
                e.y += hudOffsetY;
                e.scale *= hudScale;
            }
            hudOffsetX = 0;
            hudOffsetY = 0;
            hudScale = 1;
        }
        configVersion = VERSION;
    }

    private static Path dirCache;

    public static Path dir() {
        if (dirCache != null) return dirCache;
        Path base = FabricLoader.getInstance().getConfigDir();
        Path d = base.resolve("rttt");
        Path legacy = base.resolve("ttmod");
        try {
            if (!Files.exists(d) && Files.isDirectory(legacy)) {
                try (var walk = Files.walk(legacy)) {
                    for (Path src : (Iterable<Path>) walk::iterator) {
                        Path dst = d.resolve(legacy.relativize(src).toString());
                        if (Files.isDirectory(src)) Files.createDirectories(dst);
                        else Files.copy(src, dst);
                    }
                }
            }
            Files.createDirectories(d);
        } catch (Exception ignored) {
        }
        return dirCache = d;
    }

    public static Config load() {
        Path p = dir().resolve("config.json");
        Config c = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                c = GSON.fromJson(r, Config.class);
            } catch (Exception e) {
                RTTT.LOG.error("Could not read rttt config, using defaults", e);
            }
        }
        if (c == null) c = defaults();
        if (c.gifts == null) c.gifts = new ArrayList<>();
        if (c.mobs == null) c.mobs = new LinkedHashMap<>();
        if (c.hudLayout == null) c.hudLayout = new LinkedHashMap<>();
        c.migrate();
        c.normalizePresentation();
        MobCatalog.fillDefaults(c);
        c.save();
        return c;
    }

    public synchronized void save() {
        Path p = dir().resolve("config.json");
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            RTTT.LOG.error("Could not save rttt config", e);
        }
    }

    public JsonObject toJson() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public static Config fromJson(String json, Config old) {
        Config c = GSON.fromJson(json, Config.class);
        if (c == null) return old;
        if (c.gifts == null) c.gifts = old.gifts;
        if (c.mobs == null || c.mobs.isEmpty()) c.mobs = old.mobs;
        if (c.hudLayout == null) c.hudLayout = old.hudLayout;
        c.configVersion = VERSION;
        c.normalizePresentation();
        MobCatalog.fillDefaults(c);
        return c;
    }

    private void normalizePresentation() {
        strongMobCooldownSeconds = Math.max(0, Math.min(300, strongMobCooldownSeconds));
        giftCooldownSeconds = finiteClamp(giftCooldownSeconds, 0, 30, 0.3);
        timeOfDay = normalizeTimeOfDay(timeOfDay);
        dayLengthMinutes = finiteClamp(dayLengthMinutes, 1, 400, 16);
        hudOffsetX = finiteClamp(hudOffsetX, -100, 100, 0);
        hudOffsetY = finiteClamp(hudOffsetY, -100, 100, 0);
        hudScale = finiteClamp(hudScale, 0.5, 2, 1);
        liveUsername = liveUsername == null ? "" : liveUsername.trim();
        feedRows = Math.max(0, Math.min(5, feedRows));
        feedSeconds = Math.max(3, Math.min(120, feedSeconds));
        if (hudLayout == null) hudLayout = new LinkedHashMap<>();
        hudLayout.keySet().removeIf(k -> k == null || !java.util.Arrays.asList(HUD_ELEMENTS).contains(k));
        for (String id : HUD_ELEMENTS) {
            HudElement e = hudLayout.get(id);
            if (e == null) hudLayout.put(id, e = new HudElement());
            e.x = finiteClamp(e.x, -100, 100, 0);
            e.y = finiteClamp(e.y, -100, 100, 0);
            e.scale = finiteClamp(e.scale, 0.4, 2.5, 1);
            e.opacity = finiteClamp(e.opacity, 0.15, 1, 1);
        }
    }

    public static String normalizeTimeOfDay(String value) {
        if (value == null) return "AUTO";
        String mode = value.trim().toUpperCase(Locale.ROOT);
        return mode.equals("DAY") || mode.equals("NIGHT") ? mode : "AUTO";
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
