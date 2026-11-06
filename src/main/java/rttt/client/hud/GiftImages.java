package rttt.client.hud;

import rttt.RTTT;
import rttt.client.game.Config;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GiftImages {
    private GiftImages() {}

    private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "RTTT-GiftImages");
        t.setDaemon(true);
        return t;
    });

    private static BufferedImage shrink(BufferedImage b) {
        if (b == null || (b.getWidth() <= 128 && b.getHeight() <= 128)) return b;
        return Sprites.icon(b, 128);
    }

    public static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9а-яё]", "");
    }

    private static Path dirCache;
    private static final java.util.Set<String> DISK_TRIED = ConcurrentHashMap.newKeySet();

    private static Path dir() {
        if (dirCache != null) return dirCache;
        Path base = null;
        try { base = Config.dir(); } catch (Throwable ignored) {}
        if (base == null) base = Path.of("config", "rttt");
        Path d = base.resolve("gift_images");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return dirCache = d;
    }

    public static BufferedImage get(String giftName) {
        String k = key(giftName);
        if (k.isEmpty()) return null;
        BufferedImage img = IMAGES.get(k);
        if (img != null) return img;
        if (IMAGES.size() > 300) {
            IMAGES.clear();
            DISK_TRIED.clear();
            PENDING.clear();
        }
        if (DISK_TRIED.add(k)) {
            if (loadBundled(k)) return IMAGES.get(k);
            POOL.submit(() -> loadDisk(k));
        }
        return null;
    }

    private static boolean loadBundled(String k) {
        try (InputStream in = GiftImages.class.getResourceAsStream("/assets/rttt/gifts/" + k + ".png")) {
            if (in == null) return false;
            BufferedImage b = ImageIO.read(in);
            if (b == null) return false;
            IMAGES.put(k, shrink(b));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean loadDisk(String k) {
        try {
            Path p = dir().resolve(k + ".png");
            if (!Files.exists(p)) return false;
            BufferedImage b = ImageIO.read(p.toFile());
            if (b == null) return false;
            IMAGES.put(k, shrink(b));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void request(String giftName, String url) {
        String k = key(giftName);
        if (k.isEmpty() || url == null || url.isBlank() || IMAGES.containsKey(k)) return;
        Long last = PENDING.get(k);
        if (last != null && System.currentTimeMillis() - last < 300_000) return;
        PENDING.put(k, System.currentTimeMillis());
        DISK_TRIED.add(k);
        if (loadBundled(k)) return;
        POOL.submit(() -> {
            if (!loadDisk(k)) download(k, url);
        });
    }

    private static void download(String k, String url) {
        List<String> tries = new ArrayList<>();
        String base = url.trim();
        if (base.contains("~tplv")) {
            String stem = base.substring(0, base.indexOf("~tplv"));
            tries.add(stem + "~tplv-obj.png");
            tries.add(stem + "~tplv-obj.image");
            tries.add(stem + "~tplv-resize:200:200.png");
        }
        tries.add(base.replace(".webp", ".png"));
        tries.add(base);
        for (String u : tries) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(u).toURL().openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                c.setRequestProperty("Accept", "image/png,image/jpeg,image/*;q=0.8");
                if (c.getResponseCode() != 200) continue;
                BufferedImage img;
                try (InputStream in = c.getInputStream()) {
                    img = ImageIO.read(in);
                }
                if (img == null) continue;
                img = shrink(img);
                IMAGES.put(k, img);
                ImageIO.write(img, "png", dir().resolve(k + ".png").toFile());
                return;
            } catch (Throwable ignored) {
            }
        }
        RTTT.LOG.info("Gift icon not available for {}", k);
    }
}
