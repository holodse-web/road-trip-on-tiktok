package rttt.client.render;

public final class Shaders {
    private Shaders() {}

    private static java.lang.reflect.Method inUse;
    private static Object api;
    private static boolean failed;
    private static boolean cached;
    private static long checkedAt;

    public static boolean active() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 1000) return cached;
        checkedAt = now;
        if (failed) return cached = false;
        try {
            if (api == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            }
            return cached = (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            failed = true;
            return cached = false;
        }
    }
}
