package rttt.client.game;

import rttt.RTTT;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class SaveState {
    public long wins = 0;
    public double distance = 0;
    public long runs = 1;
    public long totalMeters = 0;
    public java.util.List<String> greetedWorlds = new java.util.ArrayList<>();

    public static SaveState load() {
        Path p = Config.dir().resolve("state.json");
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                SaveState s = Config.GSON.fromJson(r, SaveState.class);
                if (s != null) {
                    if (s.greetedWorlds == null) s.greetedWorlds = new java.util.ArrayList<>();
                    return s;
                }
            } catch (Exception e) {
                RTTT.LOG.error("Could not read rttt state", e);
            }
        }
        return new SaveState();
    }

    public synchronized void save() {
        Path dir = Config.dir();
        Path tmp = dir.resolve("state.json.tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            Config.GSON.toJson(this, w);
        } catch (Exception e) {
            RTTT.LOG.error("Could not save rttt state", e);
            return;
        }
        try {
            Files.move(tmp, dir.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.move(tmp, dir.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
        }
    }
}
