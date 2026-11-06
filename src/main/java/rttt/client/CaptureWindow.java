package rttt.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
import rttt.RTTT;

import java.nio.IntBuffer;

/** Configures the real SDL client area used by Live Studio's capture source. */
public final class CaptureWindow {
    private static volatile int requestedWidth;
    private static volatile int requestedHeight;
    private static volatile boolean fitDisplay = true;
    private static volatile int usableWidth;
    private static volatile int usableHeight;
    private static volatile String error = "";

    private CaptureWindow() {}

    public static void apply(int width, int height) {
        apply(width, height, true);
    }

    /** Must run on Minecraft's client thread. Width and height are capture pixels. */
    public static void apply(int width, int height, boolean fit) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        requestedWidth = Math.max(320, Math.min(3840, width));
        requestedHeight = Math.max(240, Math.min(3840, height));
        fitDisplay = fit;
        error = "";
        try {
            // setWindowed exits either borderless or exclusive fullscreen.
            window.setFullscreen(false);
            window.updateFullscreenIfChanged();
            SDLVideo.SDL_RestoreWindow(window.handle());
            SDLVideo.SDL_SyncWindow(window.handle());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_Rect bounds = SDL_Rect.malloc(stack);
                int display = SDLVideo.SDL_GetDisplayForWindow(window.handle());
                if (!SDLVideo.SDL_GetDisplayUsableBounds(display, bounds)) {
                    Monitor monitor = window.findBestMonitor();
                    if (monitor == null) throw new IllegalStateException("Display bounds unavailable");
                    bounds.set(monitor.x(), monitor.y(), monitor.w(), monitor.h());
                }

                IntBuffer top = stack.callocInt(1), left = stack.callocInt(1);
                IntBuffer bottom = stack.callocInt(1), right = stack.callocInt(1);
                if (!SDLVideo.SDL_GetWindowBordersSize(window.handle(), top, left, bottom, right)) {
                    // Keep enough room for the title bar if the platform cannot report it.
                    top.put(0, 40);
                    left.put(0, 8);
                    right.put(0, 8);
                    bottom.put(0, 8);
                }

                int usableW = Math.max(320, bounds.w() - left.get(0) - right.get(0) - 4);
                int usableH = Math.max(240, bounds.h() - top.get(0) - bottom.get(0) - 4);
                usableWidth = usableW;
                usableHeight = usableH;
                float density = SDLVideo.SDL_GetWindowPixelDensity(window.handle());
                if (!Float.isFinite(density) || density <= 0) density = 1;
                // Native high-DPI displays need fewer logical pixels for the same capture size.
                int logicalW = Math.max(320, Math.round(requestedWidth / density));
                int logicalH = Math.max(240, Math.round(requestedHeight / density));
                if (fit) {
                    double scale = Math.min(1, Math.min(usableW / (double) logicalW, usableH / (double) logicalH));
                    int divisor = gcd(requestedWidth, requestedHeight);
                    int ratioW = requestedWidth / divisor, ratioH = requestedHeight / divisor;
                    int units = (int) Math.floor(Math.min(logicalW * scale / ratioW, logicalH * scale / ratioH));
                    // Preserve exact 9:16 / 16:9 ratios, with the game's minimum window size.
                    if (ratioW * units >= 320 && ratioH * units >= 240) {
                        logicalW = ratioW * units;
                        logicalH = ratioH * units;
                    } else {
                        logicalW = Math.min(usableW, Math.max(320, (int) Math.floor(logicalW * scale)));
                        logicalH = Math.min(usableH, Math.max(240, (int) Math.floor(logicalH * scale)));
                    }
                }
                window.setWindowed(logicalW, logicalH);
                int windowX = bounds.x() + left.get(0) + Math.max(2, (usableW - logicalW) / 2);
                int windowY = bounds.y() + top.get(0) + Math.max(2, (usableH - logicalH) / 2);
                SDLVideo.SDL_SetWindowPosition(window.handle(), windowX, windowY);
                mc.framebufferSizeChanged();
                RTTT.LOG.info("Capture requested {}x{}, client window {}x{}, fit display: {}", requestedWidth, requestedHeight, logicalW, logicalH, fit);
            }
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.toString() : t.getMessage();
            RTTT.LOG.warn("Capture window resize failed: {}", t.toString());
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return a;
    }

    /** A snapshot of the actual framebuffer, never the requested preset presented as reality. */
    public static JsonObject status() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int width = window.getWidth(), height = window.getHeight();
        JsonObject result = new JsonObject();
        result.addProperty("width", width);
        result.addProperty("height", height);
        result.addProperty("windowWidth", window.getScreenWidth());
        result.addProperty("windowHeight", window.getScreenHeight());
        result.addProperty("requestedWidth", requestedWidth);
        result.addProperty("requestedHeight", requestedHeight);
        result.addProperty("fitDisplay", fitDisplay);
        result.addProperty("limited", requestedWidth > 0 && (width < requestedWidth - 2 || height < requestedHeight - 2));
        result.addProperty("extendsBeyondDisplay", usableWidth > 0 && (window.getScreenWidth() > usableWidth || window.getScreenHeight() > usableHeight));
        result.addProperty("fps", mc.getFps());
        result.addProperty("aspectRatio", height > 0 ? width / (double) height : 0);
        result.addProperty("portrait", height > width);
        result.addProperty("error", error);
        return result;
    }
}
