package rttt.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import rttt.RTTT;
import rttt.client.game.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Owns only the bridge launched by this Minecraft instance. Never invokes a shell. */
public final class BridgeProcess {
    public static final int PORT = 21213;
    public static final String URL = "ws://127.0.0.1:" + PORT + "/";
    private static final String[] FILES = {"bridge.js", "events.js", "package.json", "package-lock.json"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "RTTT-Bridge-Launcher"));
    private final Runnable onReady;
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private String state = "stopped", username = "", message = "Мост остановлен";
    private Process process;
    private long generation;
    private boolean closed;

    public BridgeProcess(Runnable onReady) {
        this.onReady = onReady;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process owned;
            synchronized (this) { owned = process; }
            terminate(owned);
        }, "RTTT-Bridge-Exit"));
    }

    public static String normalizeUsername(String value) {
        String name = value == null ? "" : value.trim().replaceFirst("^@", "");
        if (!name.matches("[A-Za-z0-9_.]{1,64}")) {
            throw new IllegalArgumentException("Укажите ник TikTok без ссылки: буквы, цифры, точка и подчёркивание");
        }
        return name;
    }

    public synchronized void start(String value) {
        String name = normalizeUsername(value);
        if (closed) throw new IllegalStateException("Панель закрывается");
        if (!(state.equals("stopped") || state.equals("error"))) {
            throw new IllegalStateException("Мост уже запускается или работает. Сначала остановите его");
        }
        long token = ++generation;
        username = name;
        logs.clear();
        update("starting", "Подготовка моста для @" + name);
        worker.execute(() -> launch(token, name));
    }

    public void stop() {
        final Process owned;
        final long token;
        synchronized (this) {
            token = ++generation;
            owned = process;
            update(owned == null ? "stopped" : "stopping", owned == null ? "Мост остановлен" : "Остановка моста…");
        }
        if (owned != null) owned.destroy();
        // Stop can cancel npm installation as well as a running relay, without waiting in an HTTP/game thread.
        daemon(() -> {
            terminate(owned);
            synchronized (this) {
                if (generation == token) {
                    process = null;
                    update("stopped", "Мост остановлен");
                }
            }
        }, "RTTT-Bridge-Stop").start();
    }

    public synchronized void shutdown() {
        closed = true;
        stop();
        worker.shutdown();
    }

    public synchronized JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("state", state);
        o.addProperty("running", state.equals("running") && process != null && process.isAlive());
        o.addProperty("username", username);
        o.addProperty("message", message);
        JsonArray recent = new JsonArray();
        logs.forEach(recent::add);
        o.add("logs", recent);
        return o;
    }

    private void launch(long token, String name) {
        Process started = null;
        try {
            requireCurrent(token);
            checkPort();
            Path node = findNode();
            Path dir = Config.dir().resolve("bridge").toAbsolutePath();
            Files.createDirectories(dir);
            extract(dir);
            String fingerprint = fingerprint(dir);
            Path installed = dir.resolve(".rttt-dependencies");
            boolean dependenciesReady = Files.isRegularFile(installed)
                    && Files.readString(installed).equals(fingerprint)
                    && Files.isRegularFile(dir.resolve("node_modules/tiktok-live-connector/package.json"))
                    && Files.isRegularFile(dir.resolve("node_modules/ws/package.json"));
            if (!dependenciesReady) {
                Path npm = findNpm(node);
                synchronized (this) {
                    requireCurrent(token);
                    update("installing", "Первая установка зависимостей моста; дождитесь завершения…");
                }
                Process install = run(List.of(node.toString(), npm.toString(), "ci", "--omit=dev", "--ignore-scripts", "--no-audit", "--no-fund"), dir, token);
                Thread output = readOutput(install, token, false);
                if (!install.waitFor(240, TimeUnit.SECONDS)) {
                    terminate(install);
                    throw new IOException("Установка заняла больше 4 минут. Проверьте интернет и запустите мост снова");
                }
                output.join(1000);
                requireCurrent(token);
                if (install.exitValue() != 0) throw new IOException("Не удалось установить зависимости (npm: " + install.exitValue() + "). Подробности в журнале моста");
                Files.writeString(installed, fingerprint, StandardCharsets.UTF_8);
            }
            synchronized (this) {
                requireCurrent(token);
                update("starting", "Запуск моста для @" + name + "…");
            }
            checkPort();
            started = run(List.of(node.toString(), dir.resolve("bridge.js").toString(), name, Integer.toString(PORT)), dir, token);
            Thread output = readOutput(started, token, true);
            Process relay = started;
            daemon(() -> watch(relay, output, token), "RTTT-Bridge-Watch").start();
            // Listening normally takes under a second; failure never leaves the panel stuck on "starting".
            for (int i = 0; i < 150; i++) {
                synchronized (this) {
                    requireCurrent(token);
                    if (state.equals("running") || state.equals("error")) return;
                }
                if (!relay.isAlive()) return;
                Thread.sleep(100);
            }
            throw new IOException("Мост не открыл WebSocket за 15 секунд. Подробности в журнале моста");
        } catch (Cancelled ignored) {
            terminate(started);
        } catch (Throwable e) {
            terminate(started);
            synchronized (this) {
                if (generation != token) return;
                process = null;
                update("error", e.getMessage() == null ? e.toString() : e.getMessage());
                log(message);
            }
            RTTT.LOG.warn("TikTok bridge launch failed: {}", e.toString());
        }
    }

    private synchronized Process run(List<String> command, Path dir, long token) throws IOException, Cancelled {
        requireCurrent(token);
        // node.exe + npm-cli.js avoids cmd.exe, username shell expansion and visible console helpers.
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().put("NO_COLOR", "1");
        builder.environment().put("npm_config_update_notifier", "false");
        process = builder.start();
        return process;
    }

    private Thread readOutput(Process child, long token, boolean relay) {
        Thread reader = daemon(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    boolean ready = false;
                    synchronized (this) {
                        if (generation != token) return;
                        if (relay && line.equals("[RTTT_BRIDGE_READY]") && state.equals("starting")) {
                            update("running", "Мост запущен для @" + username + ". Статус TikTok — рядом с подключением LIVE");
                            ready = true;
                        } else {
                            log(line);
                        }
                    }
                    if (ready) onReady.run();
                }
            } catch (IOException e) {
                synchronized (this) {
                    if (generation == token) log(e.toString());
                }
            }
        }, "RTTT-Bridge-Output");
        reader.start();
        return reader;
    }

    private void watch(Process child, Thread output, long token) {
        try {
            int code = child.waitFor();
            output.join(1000);
            synchronized (this) {
                if (generation != token) return;
                process = null;
                if (state.equals("error")) return;
                String last = logs.peekLast();
                update("error", "Мост завершился (код " + code + ")" + (last == null ? "" : ": " + last));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void requireCurrent(long token) throws Cancelled {
        if (generation != token || closed) throw new Cancelled();
    }

    private synchronized void update(String next, String text) {
        state = next;
        message = text;
    }

    private synchronized void log(String line) {
        String text = line.replaceAll("\\x1b\\[[0-9;]*[A-Za-z]", "").trim();
        if (text.isEmpty()) return;
        logs.addLast(text.length() > 600 ? text.substring(0, 600) + "…" : text);
        while (logs.size() > 40) logs.removeFirst();
    }

    private static Thread daemon(Runnable task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        return t;
    }

    private static void checkPort() throws IOException {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress("127.0.0.1", PORT));
        } catch (IOException e) {
            throw new IOException("Порт " + PORT + " занят другим мостом или TikFinity. Остановите его перед запуском из панели", e);
        }
    }

    private static void extract(Path dir) throws IOException {
        for (String name : FILES) {
            try (InputStream in = BridgeProcess.class.getResourceAsStream("/assets/rttt/bridge/" + name)) {
                if (in == null) throw new IOException("В моде отсутствует файл моста " + name + ". Пересоберите мод");
                byte[] content = in.readAllBytes();
                Path target = dir.resolve(name);
                if (!Files.isRegularFile(target) || !Arrays.equals(content, Files.readAllBytes(target))) {
                    Path tmp = dir.resolve(name + ".tmp");
                    Files.write(tmp, content);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String fingerprint(Path dir) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(Files.readAllBytes(dir.resolve("package.json")));
        hash.update(Files.readAllBytes(dir.resolve("package-lock.json")));
        return HexFormat.of().formatHex(hash.digest());
    }

    private static Path findNode() throws IOException {
        List<Path> candidates = new ArrayList<>();
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        String executable = win ? "node.exe" : "node";
        for (String entry : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (!entry.isBlank()) candidates.add(Path.of(entry.replace("\"", "")).resolve(executable));
        }
        for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA", "APPDATA"}) {
            String base = System.getenv(env);
            if (base != null) {
                candidates.add(Path.of(base, "nodejs", executable));
                candidates.add(Path.of(base, "Programs", "nodejs", executable));
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate.toRealPath();
        }
        throw new IOException("Node.js не найден. Установите Node.js LTS с nodejs.org и перезапустите Minecraft");
    }

    private static Path findNpm(Path node) throws IOException {
        List<Path> candidates = new ArrayList<>();
        candidates.add(node.getParent().resolve("node_modules/npm/bin/npm-cli.js"));
        candidates.add(node.getParent().resolve("../lib/node_modules/npm/bin/npm-cli.js").normalize());
        candidates.add(Path.of("/usr/share/nodejs/npm/bin/npm-cli.js"));
        for (String entry : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path folder = Path.of(entry.replace("\"", ""));
            candidates.add(folder.resolve("node_modules/npm/bin/npm-cli.js"));
            Path npm = folder.resolve("npm");
            if (Files.isSymbolicLink(npm)) candidates.add(npm.toRealPath());
        }
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        throw new IOException("npm не найден рядом с Node.js. Переустановите Node.js LTS с компонентом npm");
    }

    private static void terminate(Process owned) {
        if (owned == null) return;
        try {
            List<ProcessHandle> children = owned.descendants().toList();
            children.forEach(ProcessHandle::destroy);
            owned.destroy();
            if (!owned.waitFor(2, TimeUnit.SECONDS)) owned.destroyForcibly();
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        } catch (InterruptedException e) {
            owned.destroyForcibly();
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            owned.destroyForcibly();
        }
    }

    private static final class Cancelled extends Exception {}
}
