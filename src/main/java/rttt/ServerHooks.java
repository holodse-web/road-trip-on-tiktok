package rttt;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerHooks {
    private ServerHooks() {}

    private record Saved(ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, GameType mode) {}

    private static final Map<UUID, Saved> SAVED = new HashMap<>();
    private static final ResourceKey<WorldClock> ROAD_CLOCK = ResourceKey.create(Registries.WORLD_CLOCK, RTTT.id("road"));

    public static void init() {
    }

    public static boolean enter(MinecraftServer server, UUID playerId, double x, double y, double z, float dayRate, String timeOfDay) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        if (p == null) return false;
        ServerLevel road = server.getLevel(RTTT.ROAD_LEVEL);
        if (road == null) {
            p.sendSystemMessage(Component.literal("§c[RTTT] Измерение rttt:road не найдено. Создайте новый мир с включённым модом."));
            return false;
        }
        if (!p.level().dimension().equals(RTTT.ROAD_LEVEL) && !SAVED.containsKey(playerId)) {
            SAVED.put(playerId, new Saved(p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), p.gameMode()));
        }
        p.setGameMode(GameType.SPECTATOR);
        p.teleportTo(road, x, y, z, Set.of(), 180f, 8f, false);
        setTimeOfDay(server, timeOfDay, dayRate);
        return true;
    }

    public static void setDayRate(MinecraftServer server, float rate) {
        setTimeOfDay(server, "AUTO", rate);
    }

    public static void setTimeOfDay(MinecraftServer server, String mode, float rate) {
        try {
            ServerLevel road = server.getLevel(RTTT.ROAD_LEVEL);
            if (road == null) return;
            // Never fall back to the shared Overworld clock: its time must stay
            // independent from the stream's road dimension.
            if (road.dimensionType().defaultClock().filter((clock) -> clock.is(ROAD_CLOCK)).isEmpty()) {
                RTTT.LOG.warn("RTTT road clock unavailable; reload the world to apply the updated dimension data");
                return;
            }
            Holder<WorldClock> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(ROAD_CLOCK);
            server.clockManager().setRate(clock, Math.max(0.05f, Math.min(50f, rate)));
            boolean manual = "DAY".equals(mode) || "NIGHT".equals(mode);
            if (manual) server.clockManager().setTotalTicks(clock, "NIGHT".equals(mode) ? 18000L : 6000L);
            server.clockManager().setPaused(clock, manual);
        } catch (Throwable t) {
            RTTT.LOG.warn("Could not change road time: {}", t.toString());
        }
    }

    public static void leave(MinecraftServer server, UUID playerId) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        if (p == null) return;
        setDayRate(server, 1f);
        Saved s = SAVED.remove(playerId);
        ServerLevel target = s != null ? server.getLevel(s.dim()) : server.overworld();
        if (target == null) target = server.overworld();
        Vec3 pos = s != null ? s.pos() : Vec3.atBottomCenterOf(target.getRespawnData().pos());
        p.teleportTo(target, pos.x, pos.y, pos.z, Set.of(), s != null ? s.yRot() : 0f, s != null ? s.xRot() : 0f, false);
        p.setGameMode(s != null ? s.mode() : GameType.CREATIVE);
    }

    public static boolean isInRoad(MinecraftServer server, UUID playerId) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        return p != null && p.level().dimension().equals(RTTT.ROAD_LEVEL);
    }
}
