package rttt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import rttt.RTTT;
import rttt.client.game.Config;
import rttt.client.game.Game;
import rttt.client.game.LiveEvent;
import rttt.client.hud.Hud;
import rttt.client.net.PanelServer;
import rttt.client.net.TikTokConnector;
import rttt.client.render.CarRenderer;

import java.util.Locale;

public class RTTTClient implements ClientModInitializer {
    public static Game game;
    public static PanelServer panel;
    public static TikTokConnector live;
    private static KeyMapping keyToggle, keyPanel, keyWindow;
    private static long lastTickError = 0;

    @Override
    public void onInitializeClient() {
        Config cfg = Config.load();
        game = new Game(cfg);
        live = new TikTokConnector(game);
        live.start();
        panel = new PanelServer(game, live);
        panel.start();

        HudElementRegistry.addLast(RTTT.id("overlay"), Hud::render);
        Identifier[] hidden = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR,
                VanillaHudElements.FOOD_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
                VanillaHudElements.INFO_BAR, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP,
                VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR,
                VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.SCOREBOARD, VanillaHudElements.CHAT,
                VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.TITLE_AND_SUBTITLE};
        for (Identifier id : hidden) {
            try {
                HudElementRegistry.replaceElement(id, orig -> (HudElement) (g, d) -> {
                    if (!CameraHook.active()) orig.extractRenderState(g, d);
                });
            } catch (Throwable t) {
                RTTT.LOG.warn("Could not wrap HUD element {}", id);
            }
        }

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen)) return;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                try {
                    var font = client.font;
                    int sw = s.width, sh = s.height;
                    boolean narrow = sw < 520;
                    String ru = narrow ? "Road Trip on TikTok: создайте мир «RTTT»" : "Чтобы включить Road Trip on TikTok, создайте мир со словом «RTTT» или «Road Trip» в названии";
                    String en = narrow ? "Create a world named \"RTTT\" to play" : "To play Road Trip on TikTok, create a world with \"RTTT\" or \"Road Trip\" in its name";
                    g.centeredText(font, ru, sw / 2, sh - 34, 0xFFFFD86A);
                    g.centeredText(font, en, sw / 2, sh - 23, 0xFFD0D0D0);
                } catch (Throwable ignored) {
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                handleKeys(mc);
                game.tick();
            } catch (Throwable t) {
                if (System.currentTimeMillis() - lastTickError > 10000) {
                    lastTickError = System.currentTimeMillis();
                    RTTT.LOG.error("RTTT tick failed", t);
                }
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> CarRenderer.render(ctx.submitNodeCollector(), ctx.poseStack()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            game.state.save();
            panel.stop();
            live.shutdown();
        });

        KeyMapping.Category cat = KeyMapping.Category.register(RTTT.id("main"));
        keyToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rttt.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, cat));
        keyPanel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rttt.panel", InputConstants.Type.KEYBOARD, InputConstants.KEY_F7, cat));
        keyWindow = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.rttt.window", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, cat));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(ClientCommands.literal("rttt")
                .then(ClientCommands.literal("start").executes(c -> {
                    game.start();
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes(c -> {
                    game.stop();
                    return 1;
                }))
                .then(ClientCommands.literal("panel").executes(c -> {
                    openPanel();
                    return 1;
                }))
                .then(ClientCommands.literal("restart").executes(c -> {
                    game.submit(new LiveEvent(LiveEvent.Type.RESTART, "команда", true));
                    return 1;
                }))
                .then(ClientCommands.literal("window")
                        .executes(c -> {
                            setWindow(1080, 1920);
                            return 1;
                        })
                        .then(ClientCommands.argument("w", IntegerArgumentType.integer(200, 4000))
                                .then(ClientCommands.argument("h", IntegerArgumentType.integer(200, 4000)).executes(c -> {
                                    setWindow(IntegerArgumentType.getInteger(c, "w"), IntegerArgumentType.getInteger(c, "h"));
                                    return 1;
                                }))))
                .then(ClientCommands.literal("mob").then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 5)).executes(c -> {
                    for (int i = 0; i < IntegerArgumentType.getInteger(c, "count"); i++)
                        game.submit(LiveEvent.chat("тест" + i, "зомби", true));
                    return 1;
                })))
        ));
        RTTT.LOG.info("RTTT client ready. Panel on port {}", cfg.panelPort);
    }

    private static void handleKeys(Minecraft mc) {
        while (keyToggle.consumeClick()) {
            if (game.active) game.stop();
            else game.start();
        }
        while (keyPanel.consumeClick()) openPanel();
        while (keyWindow.consumeClick()) setWindow(1080, 1920);
    }

    public static void setWindow(int w, int h) {
        CaptureWindow.apply(w, h);
    }

    public static void openPanel() {
        String url = "http://127.0.0.1:" + panel.port() + "/";
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (Throwable t) {
            RTTT.LOG.warn("Could not open browser: {}", t.toString());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[RTTT] Панель управления: " + url));
    }
}
