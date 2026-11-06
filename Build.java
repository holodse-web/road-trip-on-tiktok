import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;

public class Build {
    static final String VERSION = "1.0.0-beta.1";
    static final String MC = "26.3";

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        boolean install = true;
        Path mcDir = null;
        for (String a : args) {
            if (a.equals("--no-install")) install = false;
            else mcDir = Paths.get(a);
        }
        if (mcDir == null) {
            String appdata = System.getenv("APPDATA");
            mcDir = appdata != null ? Paths.get(appdata, ".minecraft") : Paths.get(System.getProperty("user.home"), ".minecraft");
        }
        System.out.println("== RTTT build");
        System.out.println(".minecraft: " + mcDir);
        if (!Files.isDirectory(root.resolve("src/main/java"))) die("Запустите сборку из папки RTTT (там, где лежит src).");

        Path build = root.resolve("build");
        Path deps = build.resolve("deps");
        Path classes = build.resolve("classes");
        deleteTree(classes);
        Files.createDirectories(deps);
        Files.createDirectories(classes);

        List<Path> cp = new ArrayList<>();
        Path gameJar = mcDir.resolve("versions/" + MC + "/" + MC + ".jar");
        if (!Files.exists(gameJar)) die("Не найден " + gameJar + " — запустите хотя бы раз Minecraft " + MC + " в лаунчере.");
        cp.add(gameJar);
        Set<String> seen = new HashSet<>();
        for (String json : new String[]{"versions/" + MC + "/" + MC + ".json", "versions/Fabric " + MC + "/Fabric " + MC + ".json"}) {
            Path p = mcDir.resolve(json);
            if (!Files.exists(p)) {
                System.out.println("  (нет " + json + ")");
                continue;
            }
            String text = Files.readString(p);
            Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            while (m.find()) {
                String[] g = m.group(1).split(":");
                if (g.length < 3 || g.length > 3) continue;
                String path = g[0].replace('.', '/') + "/" + g[1] + "/" + g[2] + "/" + g[1] + "-" + g[2] + ".jar";
                Path lib = mcDir.resolve("libraries").resolve(path);
                if (Files.exists(lib) && seen.add(path)) cp.add(lib);
            }
        }
        Path fapi;
        try (Stream<Path> s = Files.list(mcDir.resolve("mods"))) {
            fapi = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("fabric-api-") && n.endsWith(".jar") && n.contains(MC);
            }).findFirst().orElse(null);
        }
        if (fapi == null) die("Не найден fabric-api-*-" + MC + ".jar в папке mods.");
        System.out.println("Fabric API: " + fapi.getFileName());
        try (ZipFile z = new ZipFile(fapi.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().startsWith("META-INF/jars/") && e.getName().endsWith(".jar")) {
                    Path out = deps.resolve(Paths.get(e.getName()).getFileName().toString());
                    if (!Files.exists(out)) try (InputStream in = z.getInputStream(e)) {
                        Files.copy(in, out);
                    }
                    cp.add(out);
                }
            }
        }
        System.out.println("Библиотек в classpath: " + cp.size());

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) die("В этой Java нет компилятора. Используйте build.bat (он берёт Java 25 из лаунчера).");
        List<String> sources;
        try (Stream<Path> s = Files.walk(root.resolve("src/main/java"))) {
            sources = s.filter(p -> p.toString().endsWith(".java")).map(Path::toString).collect(Collectors.toList());
        }
        List<String> opts = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
                "-d", classes.toString(), "-cp", cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
        opts.addAll(sources);
        System.out.println("Компиляция " + sources.size() + " файлов…");
        int rc = javac.run(null, null, null, opts.toArray(new String[0]));
        if (rc != 0) die("Ошибка компиляции (см. выше).");

        Path jar = build.resolve("rttt-" + VERSION + ".jar");
        Files.deleteIfExists(jar);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            Set<String> names = new HashSet<>();
            addTree(out, root.resolve("src/main/resources"), names);
            addTree(out, classes, names);
            Path licenses = root.resolve("LICENSES");
            if (Files.isDirectory(licenses)) try (Stream<Path> files = Files.walk(licenses)) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
                    addFile(out, file, "LICENSES/" + licenses.relativize(file).toString().replace('\\', '/'), names);
                }
            }
            Path license = root.resolve("LICENSE");
            if (Files.isRegularFile(license)) addFile(out, license, "LICENSE", names);
            for (String file : new String[]{"bridge.js", "events.js", "package.json", "package-lock.json"}) {
                Path source = root.resolve("bridge").resolve(file);
                if (!Files.isRegularFile(source)) throw new IOException("Не найден файл моста: " + source);
                String name = "assets/rttt/bridge/" + file;
                if (!names.add(name)) continue;
                out.putNextEntry(new JarEntry(name));
                Files.copy(source, out);
                out.closeEntry();
            }
        }
        System.out.println("Собрано: " + jar + " (" + Files.size(jar) / 1024 + " КБ)");

        if (install) {
            Path dst = mcDir.resolve("mods/rttt-" + VERSION + ".jar");
            try {
                Files.copy(jar, dst, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Установлено: " + dst);
                try (DirectoryStream<Path> old = Files.newDirectoryStream(mcDir.resolve("mods"), "ttmod-*.jar")) {
                    for (Path o : old) {
                        Files.move(o, o.resolveSibling(o.getFileName() + ".disabled"), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Старая версия отключена: " + o.getFileName());
                    }
                }
            } catch (IOException e) {
                System.out.println("Не удалось заменить " + dst + " — закройте Minecraft и запустите сборку ещё раз.");
            }
        }
        System.out.println("Готово.");
    }

    static void addTree(JarOutputStream out, Path dir, Set<String> names) throws IOException {
        if (!Files.isDirectory(dir)) return;
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        for (Path f : files) {
            String name = dir.relativize(f).toString().replace('\\', '/');
            if (!name.startsWith("assets/rttt/gifts/")) addFile(out, f, name, names);
        }
    }

    static void addFile(JarOutputStream out, Path file, String name, Set<String> names) throws IOException {
        if (!names.add(name)) return;
        out.putNextEntry(new JarEntry(name));
        Files.copy(file, out);
        out.closeEntry();
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(x);
        }
    }

    static void die(String msg) {
        System.out.println("ОШИБКА: " + msg);
        System.exit(1);
    }
}
