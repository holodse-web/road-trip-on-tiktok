package rttt.client.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import rttt.ServerHooks;
import rttt.RTTT;
import rttt.world.Track;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

public final class Game {
    public static Game I;

    public Config cfg;
    public final SaveState state;
    public final CarPhysics car = new CarPhysics();
    public final CameraRig cam = new CameraRig();
    public final MobDirector mobs;
    public final Effects fx;
    public final Headlights lights = new Headlights();
    public final Sfx sfx;
    public final Weather weather;

    public boolean active = false;
    public boolean entering = false;
    private int enterTicks = 0;
    private String autoStartedFor = null;
    private Boolean savedPauseOnLostFocus = null;
    private Integer savedSimDistance = null;
    private Integer savedRenderDistance = null;
    private String appliedTimeOfDay;
    private double appliedDayLength = Double.NaN;
    private IntegratedServer timeServer;

    private final EventInbox incoming = new EventInbox(4096);
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final ArrayList<QItem> rewards = new ArrayList<>();
    private long rewardSeq = 0;
    private double spotlight = 0;
    private double giftCooldown = 0;
    private long lastColorChange = 0;
    private final ArrayDeque<MobReq> mobQueue = new ArrayDeque<>();
    private final Map<String, Long> lastChat = new HashMap<>();
    private final Map<String, Integer> pendingByUser = new HashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private long likeAccum = 0;
    private int spawnCooldown = 0, saveTimer = 0, demoTimer = 100;
    private int strongMobCooldown = 0;
    private final Random rnd = new Random();

    public volatile long lastChangeAmount = 0;
    public volatile String lastChangeUser = "";
    public volatile String lastChangeLabel = "";
    public volatile String lastChangeGift = null;
    public volatile long lastChangeTime = 0;
    public int victoryTicks = 0;
    public boolean defeat = false;
    public final Host host = new Host();
    private final Map<String, Long> helped = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> hordeVotes = new HashMap<>();
    private final Map<String, Long> hordeCooldown = new HashMap<>();
    private int hordeTicks = 0;
    private long lastViewerEvent = System.currentTimeMillis();
    private long lastQuietAction = 0;
    private long lastThanks = 0;
    private long lastMilestone = 0;
    private int quietStep = 0;
    public double pendingMeters = 0;
    public long victoryNumber = 0;
    public long tickCount = 0;

    public volatile boolean liveConnected = false;
    public volatile String liveStatus = "не подключено";

    private final Deque<String> log = new ArrayDeque<>();
    public final Map<String, Long> unknownGifts = new LinkedHashMap<>();

    public interface Scheduled {
        boolean tick();
    }

    record Reward(long meters, String user, String label, String effect, String gift, long feel) {
        Reward(long meters, String user, String label, String effect) {
            this(meters, user, label, effect, null, meters);
        }

        Reward(long meters, String user, String label, String effect, String gift) {
            this(meters, user, label, effect, gift, meters);
        }
    }

    static final class QItem {
        Reward r;
        final int tier;
        final long seq;
        final String baseLabel;
        long count;
        final java.util.LinkedHashSet<String> users = new java.util.LinkedHashSet<>();

        QItem(Reward r, int tier, long seq, String baseLabel, long count) {
            this.r = r;
            this.tier = tier;
            this.seq = seq;
            this.baseLabel = baseLabel;
            this.count = count;
            users.add(r.user());
        }
    }

    record MobReq(MobCatalog.Entry entry, String user, boolean test) {}

    public Game(Config cfg) {
        I = this;
        this.cfg = cfg;
        this.state = SaveState.load();
        this.mobs = new MobDirector(this);
        this.fx = new Effects(this);
        this.sfx = new Sfx(this);
        this.weather = new Weather(this);
    }

    public void submit(LiveEvent e) {
        if (e == null) return;
        if (!incoming.offer(e)) {
            log("⚠ Очередь занята; тестовое действие не добавлено");
            return;
        }
        logChat(e);
    }

    /** WebSocket demand pauses at capacity instead of discarding live gifts. */
    public void submitLive(LiveEvent e, BooleanSupplier connected) {
        if (e == null) return;
        while (connected.getAsBoolean()) {
            if (incoming.offer(e)) {
                logChat(e);
                return;
            }
            try {
                incoming.awaitCapacity();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void logChat(LiveEvent e) {
        if (e.type == LiveEvent.Type.CHAT && !e.text.isBlank())
            log("💬 " + e.user + ": «" + e.text + "»" + (e.test ? " [тест]" : ""));
    }

    public void runOnGameThread(Runnable r) {
        tasks.add(r);
    }

    public int mobQueueSize() {
        return mobQueue.size();
    }

    public boolean testMode() {
        return !liveConnected;
    }

    public synchronized void log(String s) {
        String t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.addFirst(t + "  " + s);
        while (log.size() > 60) log.removeLast();
    }

    public synchronized List<String> recentLog() {
        return new ArrayList<>(log);
    }

    public void schedule(Scheduled s) {
        scheduled.add(s);
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            message("§cИгра работает только в одиночном мире.");
            return;
        }
        active = true;
        spotlight = 0;
        giftCooldown = 0;
        strongMobCooldown = 0;
        entering = true;
        enterTicks = 0;
        car.cruise = cfg.cruiseSpeed;
        car.reset(Track.START_X + 40);
        cam.snap(car, cfg);
        fx.clear();
        if (savedPauseOnLostFocus == null) savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;
        try {
            int rd = mc.options.renderDistance().get();
            if (rd != 10) {
                if (savedRenderDistance == null) savedRenderDistance = rd;
                mc.options.renderDistance().set(10);
            }
            int sd = mc.options.simulationDistance().get();
            if (sd > 5) {
                if (savedSimDistance == null) savedSimDistance = sd;
                mc.options.simulationDistance().set(5);
            }
        } catch (Throwable ignored) {
        }
        try {
            mc.gameRenderer.setRenderBlockOutline(false);
        } catch (Throwable ignored) {
        }
        final java.util.UUID id = mc.player.getUUID();
        final double cx = cam.x, cy = cam.y, cz = cam.z;
        final float rate = (float) (20.0 / Math.max(1, cfg.dayLengthMinutes));
        final String mode = Config.normalizeTimeOfDay(cfg.timeOfDay);
        appliedTimeOfDay = mode;
        appliedDayLength = cfg.dayLengthMinutes;
        timeServer = server;
        server.execute(() -> ServerHooks.enter(server, id, cx, cy, cz, rate, mode));
        log("▶ Игра запущена");
        runOnGameThread(() -> {
            helped.clear();
            host.clear();
            lastViewerEvent = System.currentTimeMillis();
            lastQuietAction = 0;
            lastMilestone = (long) Math.max(0, Math.floor(state.distance / 100_000.0));
        });
        message("§aИгра Road Trip on TikTok запущена. Пульт — клавиша F7.");
    }

    public void stop() {
        Minecraft mc = Minecraft.getInstance();
        boolean was = active;
        active = false;
        spotlight = 0;
        giftCooldown = 0;
        strongMobCooldown = 0;
        entering = false;
        appliedTimeOfDay = null;
        timeServer = null;
        mobs.clearAll();
        lights.clear();
        sfx.stopLoops();
        try {
            rttt.client.hud.Hud.reset();
        } catch (Throwable ignored) {
        }
        fx.clear();
        if (savedPauseOnLostFocus != null) {
            mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
            savedPauseOnLostFocus = null;
        }
        if (savedRenderDistance != null) {
            try {
                mc.options.renderDistance().set(savedRenderDistance);
            } catch (Throwable ignored) {
            }
            savedRenderDistance = null;
        }
        if (savedSimDistance != null) {
            try {
                mc.options.simulationDistance().set(savedSimDistance);
            } catch (Throwable ignored) {
            }
            savedSimDistance = null;
        }
        try {
            mc.gameRenderer.setRenderBlockOutline(true);
        } catch (Throwable ignored) {
        }
        state.save();
        IntegratedServer server = mc.getSingleplayerServer();
        if (was && server != null && mc.player != null) {
            final java.util.UUID id = mc.player.getUUID();
            server.execute(() -> ServerHooks.leave(server, id));
        }
        log("■ Игра остановлена");
    }

    private void hardStop() {
        if (active) state.save();
        active = false;
        spotlight = 0;
        giftCooldown = 0;
        strongMobCooldown = 0;
        entering = false;
        appliedTimeOfDay = null;
        timeServer = null;
        sfx.stopLoops();
        mobs.active.clear();
        autoStartedFor = null;
        if (savedPauseOnLostFocus != null) {
            Minecraft.getInstance().options.pauseOnLostFocus = savedPauseOnLostFocus;
            savedPauseOnLostFocus = null;
        }
    }

    public void message(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[RTTT] " + s));
    }

    private String greetedFor = null;
    private int greetHoldUntil = 0;

    private void greet(Minecraft mc) {
        if (mc.player.tickCount < 30) return;
        String world;
        IntegratedServer server = mc.getSingleplayerServer();
        try {
            world = server != null ? server.getWorldData().getLevelName() : (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "server");
        } catch (Throwable t) {
            world = "world";
        }
        if (world == null || world.equals(greetedFor)) return;
        greetedFor = world;
        if (state.greetedWorlds.contains(world)) return;
        state.greetedWorlds.add(world);
        state.save();
        greetHoldUntil = mc.player.tickCount + 160;
        message("§6Горячие клавиши / Hotkeys:");
        message("§eF6§r — старт/стоп игры · start/stop the game");
        message("§eF7§r — пульт управления · control panel");
        message("§eF8§r — окно 9:16 для эфира · 9:16 stream window");
        message("§7Команды / commands: /rttt start | stop | restart | panel");
    }

    private void checkAutoStart(Minecraft mc) {
        if (!cfg.autoStart) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        String name;
        try {
            name = server.getWorldData().getLevelName();
        } catch (Throwable t) {
            return;
        }
        if (name == null || name.equals(autoStartedFor)) return;
        String n = name.toLowerCase(Locale.ROOT);
        boolean match = false;
        for (String k : cfg.autoStartWorldMatch.split(",")) {
            String kk = k.trim().toLowerCase(Locale.ROOT);
            if (!kk.isEmpty() && n.contains(kk)) match = true;
        }
        if (!match) return;
        if (mc.player.tickCount < 40 || mc.player.tickCount < greetHoldUntil) return;
        autoStartedFor = name;
        start();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Runnable task;
        int taskBudget = 64;
        while (taskBudget-- > 0 && (task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                RTTT.LOG.error("RTTT task failed", t);
            }
        }
        if (mc.level == null || mc.player == null) {
            if (active) hardStop();
            autoStartedFor = null;
            greetedFor = null;
            greetHoldUntil = 0;
            incoming.clear();
            return;
        }
        greet(mc);
        if (!active) {
            checkAutoStart(mc);
            return;
        }
        syncTimeOfDay(mc);
        if (mc.isPaused()) return;
        tickCount++;

        if (entering) {
            enterTicks++;
            syncPlayer(mc);
            boolean inRoad = mc.level.dimension().equals(RTTT.ROAD_LEVEL);
            BlockPos probe = BlockPos.containing(car.x + 8, Track.top((int) car.x), 0);
            boolean loaded = inRoad && mc.level.isLoaded(probe) && mc.level.getChunk(probe).getHighestFilledSectionIndex() >= 0;
            if ((loaded && enterTicks > 30) || enterTicks > 400) {
                entering = false;
                car.reset(car.x);
                cam.snap(car, cfg);
                if (!inRoad) message("§eНе удалось войти в измерение дороги. Проверьте, что мир создан с модом.");
            }
            return;
        }

        LiveEvent ev;
        int guard = 0;
        long eventDeadline = System.nanoTime() + 3_000_000L;
        while (guard++ < 128 && System.nanoTime() < eventDeadline && (ev = incoming.poll()) != null) {
            try {
                handle(ev);
            } catch (Throwable t) {
                RTTT.LOG.warn("Bad live event: {}", t.toString());
            }
        }

        car.cruise = cfg.cruiseSpeed;
        double oldX = car.x;
        car.tick();
        if (victoryTicks == 0) {
            drainPending();
            double d = (car.x - oldX) * cfg.metersPerBlock;
            state.distance += d;
            if (d > 0) state.totalMeters += (long) Math.round(d);
        }

        Iterator<Scheduled> it = scheduled.iterator();
        List<Scheduled> snapshot = new ArrayList<>(scheduled);
        scheduled.clear();
        for (Scheduled s : snapshot) {
            boolean keep;
            try {
                keep = s.tick();
            } catch (Throwable t) {
                keep = false;
            }
            if (keep) scheduled.add(s);
        }

        if (victoryTicks == 0) {
            processRewards();
            spawnMobs();
            quietTick();
            long km = (long) Math.floor(state.distance / 100_000.0);
            if (km > lastMilestone && km > 0 && cfg.hostEnabled && pendingMeters == 0 && km - lastMilestone == 1) {
                host.say(Host.Who.ALEX, 2, 3600, "Уже " + fmtMeters(km * 100_000) + " м! Вперёд!", fmtMeters(km * 100_000) + " m already!");
            }
            lastMilestone = Math.max(km, 0);
        }
        host.enabled = cfg.hostEnabled;
        host.tick();
        mobs.tick();
        fx.tick();
        try {
            lights.tick(mc.level, car);
        } catch (Throwable t) {
            lights.clear();
        }
        weather.tick(mc.level);
        sfx.tick(car, true);
        cam.tick(car, cfg);
        syncPlayer(mc);
        physicsSounds();
        checkVictory();

        if (++saveTimer >= 200) {
            saveTimer = 0;
            state.save();
        }
        if (cfg.demoMode) demo();
    }

    private void syncPlayer(Minecraft mc) {
        try {
            mc.player.setPos(cam.x, cam.y, cam.z);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setYRot(180f);
            mc.player.setXRot(cam.pitch);
        } catch (Throwable ignored) {
        }
    }

    public void setTimeOfDay(String value) {
        cfg.timeOfDay = Config.normalizeTimeOfDay(value);
        cfg.save();
        log("☀ Время суток: " + switch (cfg.timeOfDay) {
            case "DAY" -> "день";
            case "NIGHT" -> "ночь";
            default -> "автоматическая смена";
        });
    }

    private void syncTimeOfDay(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || !mc.level.dimension().equals(RTTT.ROAD_LEVEL)) return;
        String mode = Config.normalizeTimeOfDay(cfg.timeOfDay);
        double minutes = Double.isFinite(cfg.dayLengthMinutes) ? Math.max(1, cfg.dayLengthMinutes) : 16;
        if (server == timeServer && mode.equals(appliedTimeOfDay) && Double.compare(minutes, appliedDayLength) == 0) return;
        timeServer = server;
        appliedTimeOfDay = mode;
        appliedDayLength = minutes;
        float rate = (float) (20.0 / minutes);
        server.execute(() -> ServerHooks.setTimeOfDay(server, mode, rate));
    }

    private void physicsSounds() {
        if (car.landImpact > 0.3) {
            if (car.landImpact > 0.9) {
                sfx.play(Sfx.Bus.IMPACT, "entity.iron_golem.step", (float) Math.min(0.55, 0.15 + car.landImpact * 0.15), 0.62f + rnd.nextFloat() * 0.1f, 450);
            }
            cam.addTrauma(Math.min(0.3, car.landImpact * 0.2));
        }
        if (car.scrapeImpact > 0.35) {
            sfx.play(Sfx.Bus.IMPACT, "rttt:car.scrape", (float) Math.min(1, car.scrapeImpact * 0.6), 1f, 220);
        }
        if ((car.braking > 0.4 || car.slip > 0.45) && (car.grounded[1]) && Math.abs(car.vx) > 3) {
            sfx.play(Sfx.Bus.ROAD, "rttt:car.skid", 0.5f, 0.95f + rnd.nextFloat() * 0.1f, 2200);
        }
        if (car.recoveringNow) {
            sfx.play(Sfx.Bus.IMPACT, "entity.breeze.jump", 0.8f, 1.2f, 300);
            log("↻ Машина перевернулась и восстановилась");
        }
    }

    private void handle(LiveEvent e) {
        switch (e.type) {
            case LIKE, GIFT, CHAT, FOLLOW, SUBSCRIBE, SHARE -> lastViewerEvent = System.currentTimeMillis();
            default -> {
            }
        }
        switch (e.type) {
            case LIKE -> {
                likeAccum += e.amount;
                int n = Math.max(1, cfg.likesPerTrigger);
                long fired = likeAccum / n;
                likeAccum %= n;
                long total = fired * cfg.metersPerLikeTrigger;
                if (total != 0) {
                    enqueue(new Reward(total, e.user, "лайки", "HEARTS"), "лайки", 1);
                    log("❤ " + e.user + ": лайки → +" + total + " м" + (e.test ? " [тест]" : ""));
                }
            }
            case GIFT -> onGift(e);
            case CHAT -> onChat(e);
            case MOB -> {
                MobCatalog.Entry m = MobCatalog.BY_ID.get(e.text);
                if (m == null) m = MobCatalog.match(e.text, false);
                if (m != null) {
                    mobQueue.add(new MobReq(m, e.user, true));
                    log("🧟 " + e.user + " → " + m.ru + (e.test ? " [тест]" : ""));
                }
            }
            case METERS -> {
                if (e.amount != 0) enqueue(new Reward(e.amount, e.user, e.text.isEmpty() ? "тест" : e.text,
                        e.effect != null ? e.effect : (e.amount > 0 ? "BOOST" : "KNOCK")), null, 1);
            }
            case ULTRA_PLUS -> enqueue(new Reward(cfg.ultraAmount, e.user, "УЛЬТРА", "ULTRA_PLUS", firstName(cfg.ultraPlusGift)), null, 1);
            case ULTRA_MINUS -> enqueue(new Reward(-cfg.ultraAmount, e.user, "УЛЬТРА", "ULTRA_MINUS", firstName(cfg.ultraMinusGift)), null, 1);
            case FOLLOW, SUBSCRIBE, SHARE -> onSocial(e);
            case VICTORY -> {
                if (victoryTicks == 0) state.distance = cfg.goal;
            }
            case RESTART -> restartRun();
            case RESET_STATS -> resetStats();
        }
    }

    private static String giftKey(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }

    private static boolean matchesAny(String key, String list) {
        if (list == null) return false;
        for (String s : list.split(",")) if (!s.isBlank() && key.equals(giftKey(s.trim()))) return true;
        return false;
    }

    private void onGift(LiveEvent e) {
        String key = giftKey(e.text);
        String tag = e.test ? " [тест]" : "";
        if (!key.isEmpty() && matchesAny(key, cfg.ultraPlusGift)) {
            long total = cfg.ultraAmount * e.amount;
            enqueue(new Reward(total, e.user, e.text, "ULTRA_PLUS", e.text, cfg.ultraAmount), giftKey(e.text), e.amount);
            log("🌌 " + e.user + ": " + e.text + " ×" + e.amount + " → +" + total + " м" + tag);
            return;
        }
        if (!key.isEmpty() && matchesAny(key, cfg.ultraMinusGift)) {
            long total = cfg.ultraAmount * e.amount;
            enqueue(new Reward(-total, e.user, e.text, "ULTRA_MINUS", e.text, -cfg.ultraAmount), giftKey(e.text), e.amount);
            log("☄ " + e.user + ": " + e.text + " ×" + e.amount + " → −" + total + " м" + tag);
            return;
        }
        Config.GiftRule rule = null;
        for (Config.GiftRule r : cfg.gifts) {
            if (r == null || r.name == null) continue;
            String rk = giftKey(r.name);
            if (rk.equals(key) || (e.giftId != 0 && rk.equals(Long.toString(e.giftId)))) {
                rule = r;
                break;
            }
        }
        long meters;
        String effect;
        String note = "";
        if (rule != null) {
            meters = rule.meters * e.amount;
            effect = rule.effect;
        } else {
            synchronized (unknownGifts) {
                unknownGifts.put(e.text + (e.giftId != 0 ? " (id " + e.giftId + ")" : ""), e.diamonds);
                if (unknownGifts.size() > 40) unknownGifts.remove(unknownGifts.keySet().iterator().next());
            }
            Config.GiftRule like = cfg.unknownGiftFallback ? samePrice(key, e.diamonds) : null;
            if (like == null) {
                log("🎁 " + e.user + ": " + e.text + " — не настроен" + (e.diamonds > 0 ? " (" + e.diamonds + " 💎)" : "") + tag);
                return;
            }
            long per = like.coins > 0 && like.coins != e.diamonds ? Math.round(like.meters * (double) e.diamonds / like.coins) : like.meters;
            meters = per * e.amount;
            effect = like.effect;
            note = " (как " + like.name + ")";
        }
        if (meters == 0) return;
        String gift = rule != null ? rule.name : e.text;
        enqueue(new Reward(meters, e.user, e.text, effect, gift), giftKey(gift), e.amount);
        log("🎁 " + e.user + ": " + e.text + " ×" + e.amount + " → " + (meters > 0 ? "+" : "−") + Math.abs(meters) + " м" + note + tag);
    }

    private static String firstName(String list) {
        return list == null ? "" : list.split(",")[0].trim();
    }

    private Config.GiftRule samePrice(String key, long diamonds) {
        if (diamonds <= 0) return null;
        List<Config.GiftRule> same = new ArrayList<>();
        Config.GiftRule best = null;
        double bestD = Double.MAX_VALUE;
        for (Config.GiftRule r : cfg.gifts) {
            if (r == null || r.coins <= 0 || r.meters == 0) continue;
            if (r.coins == diamonds) same.add(r);
            double d = Math.abs(Math.log((double) r.coins / diamonds));
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        if (!same.isEmpty()) return same.get(Math.floorMod(key.hashCode(), same.size()));
        return best;
    }

    static int tier(Reward r) {
        String eff = r.effect() == null ? "" : r.effect();
        if (eff.startsWith("ULTRA")) return 6;
        if ("лайки".equals(r.label())) return 0;
        long a = Math.abs(r.meters());
        if (a >= 50000) return 5;
        if (a >= 10000) return 4;
        if (a >= 2000) return 3;
        if (a >= 500) return 2;
        return 1;
    }

    static double spotlightSeconds(int tier) {
        return switch (tier) {
            case 6 -> 9;
            case 5 -> 6;
            case 4 -> 4.5;
            case 3 -> 3;
            case 2 -> 1.6;
            case 1 -> 0.9;
            default -> 1.2;
        };
    }

    private void enqueue(Reward r, String mergeKey, long count) {
        int t = tier(r);
        if (mergeKey != null && (t <= 2 || rewards.size() >= Math.max(5, cfg.maxQueuedGifts))) {
            for (QItem q : rewards) {
                if (q.tier == t && mergeKey.equals(q.baseLabel) && Long.signum(q.r.meters()) == Long.signum(r.meters())) {
                    q.count += count;
                    if (q.users.size() < 32) q.users.add(r.user());
                    q.r = new Reward(q.r.meters() + r.meters(), q.r.user(), q.r.label(), q.r.effect(), q.r.gift(), q.r.feel());
                    return;
                }
            }
        }
        rewards.add(new QItem(r, t, rewardSeq++, mergeKey, count));
        while (rewards.size() > Math.max(5, cfg.maxQueuedGifts)) {
            QItem a = null, b = null;
            for (QItem q : rewards) {
                if (a == null || q.tier < a.tier) a = q;
            }
            if (a == null) break;
            for (QItem q : rewards) {
                if (q != a && q.tier <= a.tier + 1 && Long.signum(q.r.meters()) == Long.signum(a.r.meters())) {
                    b = q;
                    break;
                }
            }
            if (b == null) {
                rewards.remove(a);
                state.distance += a.r.meters();
                continue;
            }
            rewards.remove(a);
            b.count += a.count;
            for (String name : a.users) if (b.users.size() < 32) b.users.add(name);
            b.r = new Reward(b.r.meters() + a.r.meters(), b.r.user(), b.r.label(), b.r.effect(), b.r.gift(), (Math.abs(b.r.feel()) >= Math.abs(a.r.feel()) ? b.r.feel() : a.r.feel()));
        }
    }

    public int giftQueueSize() {
        return rewards.size();
    }

    private void onSocial(LiveEvent e) {
        boolean on = switch (e.type) {
            case FOLLOW -> cfg.colorOnFollow;
            case SUBSCRIBE -> cfg.colorOnSubscribe;
            default -> cfg.colorOnShare;
        };
        String what = switch (e.type) {
            case FOLLOW -> "подписка";
            case SUBSCRIBE -> "платная подписка";
            default -> "репост";
        };
        log("👤 " + e.user + ": " + what + (e.test ? " [тест]" : ""));
        if (!on) return;
        long now = System.currentTimeMillis();
        if (e.type != LiveEvent.Type.SUBSCRIBE && now - lastColorChange < cfg.colorCooldownSeconds * 1000L) return;
        lastColorChange = now;
        float h = rnd.nextFloat();
        float sat = 0.65f + rnd.nextFloat() * 0.3f, val = 0.75f + rnd.nextFloat() * 0.2f;
        int rgb = java.awt.Color.HSBtoRGB(h, sat, val) & 0xFFFFFF;
        cfg.carColor = rgb;
        runOnGameThread(() -> {
            fx.carRepaint(rgb);
            sfx.play(Sfx.Bus.GIFT, "block.amethyst_block.chime", 0.9f, 1.2f, 100);
        });
        setLastChange(0, e.user, "новый цвет машины — " + what + " · " + e.user, null);
        host.say(Host.Who.ALEX, 2, 3200, Host.nick(e.user) + ", спасибо! Новый цвет!", "Thanks, " + Host.nick(e.user) + "! New paint!");
        log("🎨 " + e.user + ": " + what + " → новый цвет машины" + (e.test ? " [тест]" : ""));
    }

    private void onChat(LiveEvent e) {
        MobCatalog.Entry m = MobCatalog.match(e.text, cfg.chatMatchInsideMessage);
        if (m == null) return;
        Config.MobSetting s = cfg.mobs.get(m.id);
        if (s != null && !s.enabled) return;
        long now = System.currentTimeMillis();
        if (hordeVote(m, e.user, now, e.test)) return;
        if (!e.test) {
            Long last = lastChat.get(e.user);
            if (last != null && now - last < cfg.chatCooldownSeconds * 1000L) return;
            if (pendingByUser.getOrDefault(e.user, 0) >= Math.max(1, cfg.maxPendingPerViewer)) return;
        }
        if (mobQueue.size() >= cfg.maxQueuedMobs) return;
        lastChat.put(e.user, now);
        if (lastChat.size() > 5000) lastChat.clear();
        pendingByUser.merge(e.user, 1, Integer::sum);
        mobQueue.add(new MobReq(m, e.user, e.test));
        log("🧟 " + e.user + " → " + m.ru + (e.test ? " [тест]" : ""));
    }

    private void credit(String user, long meters) {
        if (meters <= 0 || user == null || user.isBlank()) return;
        String u = user.strip();
        if (u.equals("дорога") || u.equals("орда") || u.equals("команда") || u.startsWith("demo_")) return;
        helped.merge(u, meters, Long::sum);
        if (helped.size() > 5000) helped.clear();
    }

    public List<Map.Entry<String, Long>> topHelpers(int n) {
        List<Map.Entry<String, Long>> list = new ArrayList<>(helped.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    public void resetTop() {
        runOnGameThread(helped::clear);
    }

    private boolean hordeVote(MobCatalog.Entry m, String user, long now, boolean test) {
        if (!cfg.hordeEnabled || cfg.hordeViewers < 2) return false;
        Map<String, Long> votes = hordeVotes.computeIfAbsent(m.id, k -> new LinkedHashMap<>());
        long window = Math.max(3, cfg.hordeWindowSeconds) * 1000L;
        votes.values().removeIf(t -> now - t > window);
        votes.put(user == null ? "" : user, now);
        if (hordeVotes.size() > 200) hordeVotes.clear();
        if (votes.size() < cfg.hordeViewers) return false;
        Long cd = hordeCooldown.get(m.id);
        if (cd != null && now - cd < 60_000) return false;
        hordeCooldown.put(m.id, now);
        votes.clear();
        boolean boss = m.size == MobCatalog.Size.BOSS;
        int n = boss ? 1 : Math.max(2, Math.min(6, cfg.hordeSize));
        for (int i = 0; i < n; i++) mobQueue.addFirst(new MobReq(m, "орда", test));
        hordeTicks = 20 * 30;
        String what = boss ? m.ru : m.ru + " ×" + n;
        setLastChange(0, "", "ОРДА · HORDE: " + what + " — " + cfg.hordeViewers + " зрителей");
        host.say(Host.Who.STEVE, 4, 4200, boss ? "Вы вызвали " + m.ru + " вместе?!" : "Это целая орда! " + what + "!",
                boss ? "You summoned it together?!" : "It's a horde! " + mobEn(m) + " ×" + n + "!");
        sfx.play(Sfx.Bus.MOB, "event.raid.horn", 0.9f, 1f, 0);
        log("🧟‍♂️ ОРДА: " + what + " (" + cfg.hordeViewers + " зрителей написали «" + m.ru + "»)");
        return true;
    }

    static String mobEn(MobCatalog.Entry m) {
        String id = m.id.replace('_', ' ');
        return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private void hostOnReward(Reward r, String eff) {
        if (!cfg.hostEnabled) return;
        String u = Host.nick(r.user());
        long m = r.meters();
        if ("лайки".equals(r.label())) {
            host.say(Host.Who.ALEX, 1, 2600, host.pick("Лайки нас разгоняют!", "Спасибо за лайки!", "Ещё лайков — ещё быстрее!"), "Likes push us forward!");
            return;
        }
        long now = System.currentTimeMillis();
        if (eff.equals("ULTRA_PLUS")) {
            host.say(Host.Who.ALEX, 4, 5000, u + ", ЭТО ЛЕГЕНДАРНО!!!", u + ", THAT'S LEGENDARY!");
            host.say(Host.Who.STEVE, 4, 5000, "Держитесь крепче!", "Hold on tight!");
        } else if (eff.equals("ULTRA_MINUS")) {
            host.say(Host.Who.STEVE, 4, 5000, u + ", НЕЕЕТ!!!", u + ", NOOO!");
            host.say(Host.Who.ALEX, 4, 5000, "Мы летим назад!", "We're flying back!");
        } else if (m >= 2000) {
            host.say(Host.Who.ALEX, 3, 3600, host.pick(u + ", спасибо! НИТРО!", "Полетели! Спасибо, " + u + "!", u + ", ты легенда!"), "Thanks, " + u + "! Nitro!");
        } else if (m > 0) {
            if (now - lastThanks < 2500) return;
            lastThanks = now;
            host.say(Host.Who.ALEX, 1, 2600, host.pick("Спасибо, " + u + "!", u + ", ты лучший!", "Ура, " + u + "!"), "Thanks, " + u + "!");
        } else if (m <= -10000) {
            host.say(Host.Who.STEVE, 3, 3600, host.pick("ААА! " + u + ", за что?!", u + ", мы же почти доехали!"), "AAA! " + u + ", why?!");
        } else if (m < 0) {
            host.say(Host.Who.STEVE, 2, 2800, host.pick("Эй, " + u + "!", u + ", ну за что?!", "Ой, " + u + " мешает!"), "Hey, " + u + "!");
        }
    }

    private void hostOnSpawn(MobReq req) {
        if (!cfg.hostEnabled) return;
        MobCatalog.Entry e = req.entry;
        if ("орда".equals(req.user)) return;
        if (e.helper) {
            host.say(Host.Who.ALEX, 1, 2800, host.pick(e.ru + " нам поможет!", "Смотри, " + e.ru + "!", "Ура, " + e.ru + "!"), mobEn(e) + " will help us!");
        } else {
            boolean big = e.size.ordinal() >= MobCatalog.Size.HUGE.ordinal();
            host.say(Host.Who.STEVE, big ? 3 : 1, big ? 3600 : 2600,
                    big ? "Это " + e.ru + "!!! Бежим!" : host.pick("Осторожно, " + e.ru + "!", "Ой, " + e.ru + "!", e.ru + "? Только не это!"),
                    big ? "It's a " + mobEn(e) + "! Run!" : "Watch out, " + mobEn(e) + "!");
        }
    }

    private static final String[][] PROMPTS = {
            {"Напишите «корова» — она нас подтолкнёт!", "Type \"cow\" — it pushes us forward!"},
            {"Кто вызовет дракона? Напишите «дракон»!", "Who summons the dragon? Type \"dragon\"!"},
            {"Нам скучно… напишите имя любого моба!", "We're bored… type any mob name!"},
            {"5 человек напишут одного моба — будет орда!", "5 viewers, same mob — a horde!"},
            {"Лайки разгоняют машину! Жмите на экран!", "Likes speed us up! Tap the screen!"},
            {"Волк или крипер? Пишите в чат!", "Wolf or creeper? Tell us in chat!"},
            {"Подписка — и машина сменит цвет!", "Follow — and the car changes colour!"},
            {"Сколько ещё до дома? Угадайте в чате!", "How far to home? Guess in chat!"}
    };

    private void quietTick() {
        if (!cfg.quietChat || victoryTicks > 0) return;
        long now = System.currentTimeMillis();
        if (now - lastViewerEvent < Math.max(30, cfg.quietSeconds) * 1000L) return;
        if (now - lastQuietAction < Math.max(20, cfg.quietIntervalSeconds) * 1000L) return;
        lastQuietAction = now;
        int step = quietStep++;
        String[] p = PROMPTS[(step + host.pickIndex(PROMPTS.length)) % PROMPTS.length];
        Host.Who who = (step % 2 == 0) ? Host.Who.ALEX : Host.Who.STEVE;
        double r = rnd.nextDouble();
        if (r < 0.3) {
            Weather.State[] all = Weather.State.values();
            Weather.State next = all[rnd.nextInt(all.length)];
            weather.force(next);
            String[] w = switch (next) {
                case RAIN -> new String[]{"Ой, начинается дождь!", "Oh, it's raining!"};
                case THUNDER -> new String[]{"Гроза! Держись!", "A thunderstorm! Hold on!"};
                default -> new String[]{"Солнышко вышло!", "The sun is out!"};
            };
            host.say(who, 2, 4200, w[0], w[1]);
            log("🌦 Тихий чат: смена погоды");
        } else if (r < 0.55 && cfg.quietAmbientMobs && mobQueue.isEmpty()) {
            List<MobCatalog.Entry> helpers = new ArrayList<>();
            for (MobCatalog.Entry e : MobCatalog.BY_ID.values()) {
                Config.MobSetting s = cfg.mobs.get(e.id);
                if (e.helper && e.size.ordinal() <= MobCatalog.Size.LARGE.ordinal() && (s == null || s.enabled)) helpers.add(e);
            }
            if (!helpers.isEmpty()) {
                MobCatalog.Entry e = helpers.get(rnd.nextInt(helpers.size()));
                mobQueue.add(new MobReq(e, "дорога", true));
                host.say(Host.Who.ALEX, 2, 4200, "Смотрите, " + e.ru + " сама к нам бежит!", "Look, a " + mobEn(e) + " is coming!");
                log("🌿 Тихий чат: " + e.ru + " пришёл сам");
            }
        } else {
            host.say(who, 2, 5200, p[0], p[1]);
        }
    }

    private void processRewards() {
        spotlight = Math.max(0, spotlight - 0.05);
        giftCooldown = Math.max(0, giftCooldown - 0.05);
        if (rewards.isEmpty()) return;
        boolean instant = !cfg.giftQueue;
        if (!instant && spotlight > 0) return;
        int effectBudget = 8;
        do {
            QItem best = null;
            for (QItem q : rewards) {
                if (!instant && q.r.gift() != null && giftCooldown > 0.000001) continue;
                if (best == null || q.tier > best.tier || (q.tier == best.tier && q.seq < best.seq)) best = q;
            }
            if (best == null) return;
            rewards.remove(best);
            Reward r = best.r;
            if (best.count > 1 || best.users.size() > 1) {
                String who = best.users.iterator().next() + (best.users.size() > 1 ? " +" + (best.users.size() - 1) : "");
                String label = (r.label() == null ? "" : r.label()) + " ×" + best.count;
                long feel = (long) (Math.signum(r.meters()) * Math.min(Math.abs(r.meters()), Math.abs(r.feel()) * Math.sqrt(best.count) * 1.5));
                r = new Reward(r.meters(), who, label, r.effect(), r.gift(), feel);
            }
            apply(r);
            spotlight = spotlightSeconds(best.tier) * Math.max(0.1, cfg.giftSpotlightScale);
            if (r.gift() != null) {
                double pause = Double.isFinite(cfg.giftCooldownSeconds) ? Math.max(0, Math.min(30, cfg.giftCooldownSeconds)) : 0.3;
                giftCooldown = instant ? 0 : spotlight + pause;
            }
        } while (instant && --effectBudget > 0 && !rewards.isEmpty());
    }

    static double strength(long meters) {
        return Math.max(0.15, Math.min(2.2, Math.log10(1 + Math.abs(meters)) / 3.0));
    }

    private void apply(Reward r) {
        if (r.meters() == 0) return;
        String eff = r.effect() == null ? "" : r.effect().toUpperCase(Locale.ROOT);
        if (r.meters() < 0 && r.feel() > -10000 && (eff.equals("ANVIL") || eff.equals("METEOR"))) {
            mobs.dropProp(eff, () -> applyNow(r));
        } else {
            applyNow(r);
        }
    }

    private void addMeters(long m) {
        if (Math.abs(m) >= 2000) pendingMeters += m;
        else state.distance += m;
    }

    private void drainPending() {
        if (pendingMeters == 0) return;
        double rem = pendingMeters;
        double step = Math.signum(rem) * Math.min(Math.abs(rem), Math.max(Math.abs(rem) * 0.08, 400));
        pendingMeters -= step;
        state.distance += step;
        if (Math.abs(pendingMeters) < 0.5) pendingMeters = 0;
    }

    private void applyNow(Reward r) {
        addMeters(r.meters());
        setLastChange(r.meters(), r.user(), r.label(), r.gift());
        double st = strength(r.feel());
        String eff = r.effect() == null ? "" : r.effect().toUpperCase(Locale.ROOT);
        if (r.meters() > 0 && !"лайки".equals(r.label())) credit(r.user(), r.meters());
        hostOnReward(r, eff);
        if (r.meters() > 0) {
            boolean ultra = eff.equals("ULTRA_PLUS");
            boolean big = ultra || eff.equals("NITRO") || r.feel() >= 2000;
            if (big) {
                double speed = ultra ? 40 : Math.min(38, 24 + Math.max(0, st - 1.0) * 22);
                double secs = ultra ? 7.5 : Math.min(7, 3 + Math.max(0, st - 1.0) * 5);
                car.nitro(speed, secs);
                car.launch(ultra ? 2.2 : Math.min(1.2, 0.2 + (st - 1.0) * 0.8));
                fx.nitro(secs);
                if (ultra) {
                    fx.flash(0xFFF3C0, 0.6f);
                    cam.addTrauma(0.6);
                }
                sfx.rev(1.2);
                sfx.play(Sfx.Bus.GIFT, "entity.firework_rocket.launch", 1f, 0.7f, 300);
                sfx.play(Sfx.Bus.GIFT, "entity.breeze.wind_burst", 0.7f, 0.8f, 400);
                cam.addTrauma(0.25);
                fx.positive(eff.equals("NITRO") ? "TOTEM" : eff, st);
            } else {
                car.push(st, 1.6 + 1.4 * st);
                sfx.rev(0.4 + st * 0.35);
                fx.positive(eff, st);
            }
        } else {
            boolean ultra = eff.equals("ULTRA_MINUS");
            boolean big = ultra || r.feel() <= -10000;
            if (big) blastBack(ultra ? 2.4 : st);
            else car.knockBack(0.9 + st * 1.0, 0.55 + st * 0.35, st > 1.55);
            fx.negativeImpact(eff, st);
            if (!big) sfx.play(Sfx.Bus.IMPACT, "rttt:car.crash", (float) Math.min(1, 0.5 + st * 0.25), 0.95f + rnd.nextFloat() * 0.1f, 150);
        }
    }

    private void blastBack(double power) {
        car.blast(power);
        cam.addTrauma(0.9);
        fx.blastTrail(power);
        sfx.blastImpact(power);
    }

    public void onHelperPush(MobCatalog.Entry e, String user) {
        Config.MobSetting s = cfg.mobs.get(e.id);
        long meters = s != null ? Math.abs(s.meters) : e.size.meters;
        double power = s != null ? s.power : 0.8;
        state.distance += meters;
        setLastChange(meters, user, e.ru, null);
        credit(user, meters);
        if (!car.inBigMotion()) car.push(power, 1.0 + power);
        double st = strength(meters);
        fx.positive("HEARTS", st);
        sfx.rev(0.5);
        log("💚 " + e.ru + " (" + user + ") толкает → +" + meters + " м");
    }

    public void onMobHit(MobCatalog.Entry e, String user, double upBias) {
        Config.MobSetting s = cfg.mobs.get(e.id);
        long meters = s != null ? s.meters : e.size.meters;
        double power = s != null ? s.power : e.size.power;
        meters = Math.round(meters * cfg.mobPenaltyMultiplier);
        state.distance -= meters;
        setLastChange(-meters, user, e.ru);
        car.knockBack(power, 0.4 + upBias * 0.5, power >= 2.0);
        double st = strength(meters);
        sfx.play(Sfx.Bus.IMPACT, "entity.zombie.attack_iron_door", (float) Math.min(1, 0.4 + st * 0.3), 0.9f + rnd.nextFloat() * 0.2f, 80);
        sfx.play(Sfx.Bus.IMPACT, power > 1.5 ? "entity.player.attack.knockback" : "entity.player.attack.crit", 0.8f, 1f, 80);
        if (power >= 2.0) sfx.play(Sfx.Bus.IMPACT, "item.mace.smash_ground_heavy", 0.9f, 0.9f, 200);
        cam.addTrauma(Math.min(1, 0.2 + power * 0.25));
        fx.flash(0xFF5040, (float) Math.min(0.45, 0.1 + power * 0.12));
        log("💥 " + e.ru + " (" + user + ") → −" + meters + " м");
        host.say(Host.Who.STEVE, 1, 2600, host.pick("Ай!", "Ой-ой!", "Ну зачем?!", "Держись, Алекс!"), host.pick("Ouch!", "Whoa!", "Hold on, Alex!"));
    }

    private void spawnMobs() {
        if (strongMobCooldown > 0) strongMobCooldown--;
        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }
        if (hordeTicks > 0) hordeTicks--;
        int maxActive = Math.max(1, cfg.maxActiveMobs);
        if (hordeTicks > 0) maxActive = Math.max(maxActive, Math.max(2, cfg.hordeSize));
        if (mobQueue.isEmpty() || mobs.activeCount() >= maxActive) return;
        MobReq req = nextMobRequest();
        if (req == null) return;
        if (!mobs.spawn(req.entry, req.user)) {
            if (mobs.levelReady()) {
                mobQueue.remove(req);
                pendingByUser.computeIfPresent(req.user, (k, v) -> v <= 1 ? null : v - 1);
                log("⚠ " + req.entry.ru + " не смог появиться, пропущен");
            }
            return;
        }
        boolean boss = req.entry.size == MobCatalog.Size.BOSS;
        mobQueue.remove(req);
        hostOnSpawn(req);
        pendingByUser.computeIfPresent(req.user, (k, v) -> v <= 1 ? null : v - 1);
        spawnCooldown = boss ? 40 : 14;
        if (isStrongMob(req.entry)) strongMobCooldown = Math.max(0, Math.min(300, cfg.strongMobCooldownSeconds)) * 20;
    }

    private static boolean isStrongMob(MobCatalog.Entry entry) {
        return !entry.helper && (entry.size == MobCatalog.Size.BOSS || entry.kind == MobCatalog.Kind.GIANT);
    }

    private MobReq nextMobRequest() {
        boolean activeBoss = false;
        for (MobDirector.Enc enc : mobs.active)
            if (enc.e.size == MobCatalog.Size.BOSS && !enc.hit) { activeBoss = true; break; }
        for (MobReq req : mobQueue) {
            if (isStrongMob(req.entry) && cfg.strongMobCooldownSeconds > 0 && strongMobCooldown > 0) continue;
            if (req.entry.size == MobCatalog.Size.BOSS && activeBoss) continue;
            return req;
        }
        return null;
    }

    private void setLastChange(long amount, String user, String label) {
        setLastChange(amount, user, label, null);
    }

    private void setLastChange(long amount, String user, String label, String gift) {
        lastChangeGift = gift;
        lastChangeAmount = amount;
        lastChangeUser = user == null ? "" : user;
        lastChangeLabel = label == null ? "" : label;
        lastChangeTime = System.currentTimeMillis();
    }

    private void checkVictory() {
        if (victoryTicks > 0) {
            victoryTicks--;
            if (victoryTicks == 100) sfx.play(Sfx.Bus.VICTORY, "ui.toast.challenge_complete", 1f, 0.8f, 0);
            if (victoryTicks == 0) {
                defeat = false;
                pendingMeters = 0;
                state.distance = 0;
                state.runs++;
                state.save();
                setLastChange(0, "", "Новый заезд!");
                log("🏁 Новый заезд #" + state.runs);
            }
            return;
        }
        if (state.distance >= cfg.goal) {
            state.distance = cfg.goal;
            victoryTicks = 160;
            state.wins++;
            victoryNumber = state.wins;
            state.save();
            fx.victory(true);
            fx.flash(0xFFE27A, 0.7f);
            car.push(2.0, 4);
            sfx.play(Sfx.Bus.VICTORY, "ui.toast.challenge_complete", 1f, 1f, 0);
            sfx.play(Sfx.Bus.VICTORY, "entity.player.levelup", 1f, 0.8f, 0);
            sfx.play(Sfx.Bus.VICTORY, "entity.firework_rocket.launch", 1f, 1f, 0);
            log("🏆 ПОБЕДА #" + state.wins + " — Алекс дома!");
            host.say(Host.Who.ALEX, 5, 6000, "МЫ ДОМА!!! Спасибо всем!", "WE'RE HOME! Thank you all!");
            host.say(Host.Who.STEVE, 5, 6000, "Ура! Победа №" + state.wins + "!", "Win #" + state.wins + "!");
        } else if (state.distance <= -cfg.goal) {
            state.distance = -cfg.goal;
            pendingMeters = 0;
            victoryTicks = 160;
            defeat = true;
            state.wins--;
            victoryNumber = state.wins;
            state.save();
            fx.flash(0xFF3030, 0.7f);
            cam.addTrauma(0.8);
            sfx.play(Sfx.Bus.VICTORY, "block.beacon.deactivate", 1f, 0.7f, 0);
            sfx.play(Sfx.Bus.VICTORY, "entity.villager.no", 1f, 0.8f, 200);
            sfx.play(Sfx.Bus.VICTORY, "block.respawn_anchor.deplete", 1f, 0.7f, 400);
            host.say(Host.Who.STEVE, 5, 6000, "Мы потерялись… Начинаем заново!", "We got lost… Starting over!");
            log("💀 ПОРАЖЕНИЕ — машину отбросило на −" + fmtMeters(cfg.goal) + " м. Победы: " + state.wins);
        }
    }

    private static String fmtMeters(long m) {
        return String.format(Locale.ROOT, "%,d", m).replace(',', ' ');
    }

    public void restartRun() {
        spotlight = 0;
        giftCooldown = 0;
        strongMobCooldown = 0;
        state.distance = 0;
        state.runs++;
        victoryTicks = 0;
        defeat = false;
        pendingMeters = 0;
        rewards.clear();
        mobQueue.clear();
        pendingByUser.clear();
        mobs.clearAll();
        lights.clear();
        setLastChange(0, "", "Новый заезд!");
        state.save();
        log("↺ Заезд перезапущен (победы сохранены)");
    }

    public void resetStats() {
        state.wins = 0;
        state.runs = 1;
        state.totalMeters = 0;
        restartRun();
        log("⟲ Статистика сброшена");
    }

    private void demo() {
        if (--demoTimer > 0) return;
        demoTimer = 30 + rnd.nextInt(90);
        String user = "demo_" + (100 + rnd.nextInt(900));
        double r = rnd.nextDouble();
        if (r < 0.45) {
            List<MobCatalog.Entry> list = new ArrayList<>(MobCatalog.BY_ID.values());
            MobCatalog.Entry e = list.get(rnd.nextInt(list.size()));
            if (e.size == MobCatalog.Size.BOSS && rnd.nextDouble() < 0.8) e = MobCatalog.BY_ID.get("zombie");
            submit(LiveEvent.chat(user, e.ru, true));
        } else if (r < 0.8 && !cfg.gifts.isEmpty()) {
            Config.GiftRule g = cfg.gifts.get(rnd.nextInt(cfg.gifts.size()));
            submit(LiveEvent.gift(user, g.name, 1 + rnd.nextInt(3), 0, 1, true));
        } else {
            submit(LiveEvent.like(user, 10 + rnd.nextInt(80), true));
        }
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("active", active);
        o.addProperty("entering", entering);
        o.addProperty("distance", Math.round(state.distance));
        o.addProperty("wins", state.wins);
        o.addProperty("runs", state.runs);
        o.addProperty("goal", cfg.goal);
        o.addProperty("testMode", testMode());
        o.addProperty("liveConnected", liveConnected);
        o.addProperty("liveStatus", liveStatus);
        o.addProperty("eventQueue", incoming.size());
        o.addProperty("rewardQueue", rewards.size());
        o.addProperty("mobQueue", mobQueue.size());
        com.google.gson.JsonArray top = new com.google.gson.JsonArray();
        for (Map.Entry<String, Long> t : topHelpers(3)) {
            JsonObject row = new JsonObject();
            row.addProperty("user", t.getKey());
            row.addProperty("meters", t.getValue());
            top.add(row);
        }
        o.add("topHelpers", top);
        o.addProperty("quietSeconds", (System.currentTimeMillis() - lastViewerEvent) / 1000);
        o.addProperty("activeMobs", mobs.activeCount());
        o.addProperty("speed", Math.round(car.vx * 10) / 10.0);
        o.addProperty("weather", weather.state.name());
        o.addProperty("timeOfDay", Config.normalizeTimeOfDay(cfg.timeOfDay));
        JsonArray l = new JsonArray();
        for (String s : recentLog()) l.add(s);
        o.add("log", l);
        JsonArray u = new JsonArray();
        synchronized (unknownGifts) {
            for (Map.Entry<String, Long> en : unknownGifts.entrySet()) u.add(en.getKey() + " — " + en.getValue() + "💎");
        }
        o.add("unknownGifts", u);
        return o;
    }
}
