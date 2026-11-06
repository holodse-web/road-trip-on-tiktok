package rttt.client.game;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import rttt.client.render.Shaders;
import rttt.world.Track;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Headlights {
    private final Map<BlockPos, BlockState> placed = new HashMap<>();
    private ClientLevel level;
    private boolean on = false;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final Map<BlockPos, Integer> levels = new HashMap<>();
    private static final int SPACING = 4;
    private int skip = 0;

    public void tick(ClientLevel l, CarPhysics car) {
        if (l != level) {
            placed.clear();
            levels.clear();
            level = l;
        }
        if (l == null) return;
        float dark = Math.min(1f, l.getSkyDarken() / 9f);
        float k = Math.max(0f, Math.min(1f, (dark - 0.25f) / 0.4f));
        if (k <= 0f || Math.cos(car.ang) < 0.3 || !Shaders.active()) {
            if (on) clear();
            on = false;
            return;
        }
        on = true;
        if (++skip < 2) return;
        skip = 0;
        Map<BlockPos, Integer> next = new HashMap<>();
        int from = (int) Math.ceil(car.x + 2.5), to = (int) Math.floor(car.x + 13.5);
        int full = Math.max(1, Math.round(15 * k));
        for (int x = from; x <= to; x++) {
            if (Math.floorMod(x, SPACING) != 0) continue;
            add(l, next, x, x - car.x > 10.5 ? Math.max(1, full - 3) : full);
        }
        Iterator<Map.Entry<BlockPos, BlockState>> it = placed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockState> e = it.next();
            if (!next.containsKey(e.getKey())) {
                restore(l, e.getKey(), e.getValue());
                levels.remove(e.getKey());
                it.remove();
            }
        }
        for (Map.Entry<BlockPos, Integer> e : next.entrySet()) {
            BlockPos pos = e.getKey();
            int lv = e.getValue();
            Integer cur = levels.get(pos);
            if (cur != null && cur == lv) continue;
            BlockState here = l.getBlockState(pos);
            if (cur == null && !here.isAir()) continue;
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lv);
            if (l.setBlock(pos, light, FLAGS)) {
                if (cur == null) placed.put(pos.immutable(), here);
                levels.put(pos.immutable(), lv);
            }
        }
    }

    private static void add(ClientLevel l, Map<BlockPos, Integer> out, int x, int level) {
        int z = 1;
        int y0 = (int) Math.floor(Track.groundAt(x + 0.5)) + 3;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y0, z);
        for (int i = 0; i < 8; i++) {
            p.setY(y0 - i);
            BlockState s = l.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.LIGHT)) {
                out.put(new BlockPos(x, p.getY() + 1, z), level);
                return;
            }
        }
    }

    private void restore(ClientLevel l, BlockPos pos, BlockState old) {
        try {
            if (l.getBlockState(pos).is(Blocks.LIGHT)) l.setBlock(pos, old, FLAGS);
        } catch (Throwable ignored) {
        }
    }

    public void clear() {
        if (level != null) {
            for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) restore(level, e.getKey(), e.getValue());
        }
        placed.clear();
        levels.clear();
    }
}
