package rttt.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import static rttt.world.Scenery.footprintBase;
import static rttt.world.Scenery.foundation;
import static rttt.world.Scenery.groundTop;
import static rttt.world.Scenery.put;
import static rttt.world.Scenery.r;

/**
 * Set pieces beside the road. Each one fits the slot the road generator reserves for a structure:
 * X from {@code ax} to {@code ax + 10}, Z from {@code az - 7} to {@code az}, so trees and ponds keep clear.
 */
final class Landmarks {
    private Landmarks() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    private static void box(Scenery.Sink out, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) put(out, x, y, z, s);
    }

    /** Level ground for a footprint and return the Y of the first free layer above it. */
    private static int pad(Scenery.Sink out, int x0, int x1, int z0, int z1, BlockState fill) {
        int y = footprintBase(x0, x1, z0, z1);
        foundation(out, x0, x1, z0, z1, y, fill);
        return y;
    }

    private static int floor(int x, int z) {
        return groundTop(x, z) + 1;
    }

    static void build(Scenery.Sink out, Palette.Landmark mark, Palette pal, int ax, int az, long seed) {
        switch (mark) {
            case WELL -> well(out, pal, ax, az);
            case CAMP -> camp(out, pal, ax, az, seed);
            case LIGHTHOUSE -> lighthouse(out, ax, az);
            case SNOWMEN -> snowmen(out, ax, az, seed);
            case PYRAMID -> pyramid(out, ax, az);
            case TEMPLE -> temple(out, ax, az, seed);
            case WITCH_HUT -> witchHut(out, pal, ax, az);
            case TORII -> torii(out, ax, az);
            case STONEHENGE -> stonehenge(out, ax, az, seed);
            case GRAVES -> graves(out, pal, ax, az, seed);
            case BEES -> bees(out, pal, ax, az, seed);
            case PUMPKINS -> pumpkins(out, ax, az, seed);
            case TRESTLE -> trestle(out, ax, az);
            case END_TOWER -> endTower(out, ax, az);
            case BASTION -> bastion(out, pal, ax, az, seed);
            case SCULK_GATE -> sculkGate(out, ax, az);
            case HAYSTACKS -> haystacks(out, ax, az, seed);
            case TOTEMS -> totems(out, ax, az, seed);
            case ICE_ARCH -> iceArch(out, ax, az, seed);
        }
    }

    private static void well(Scenery.Sink out, Palette pal, int ax, int az) {
        boolean sandy = pal.surface == Blocks.SAND;
        BlockState wall = st(sandy ? Blocks.SANDSTONE : Blocks.MOSSY_COBBLESTONE), stone = st(sandy ? Blocks.CUT_SANDSTONE : Blocks.COBBLESTONE);
        BlockState post = st(sandy ? Blocks.SANDSTONE_WALL : Blocks.OAK_FENCE);
        BlockState roof = (sandy ? Blocks.SANDSTONE_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        int cx = ax + 4, cz = az - 3;
        int y = pad(out, cx - 2, cx + 2, cz - 2, cz + 2, stone);
        box(out, cx - 2, y - 1, cz - 2, cx + 2, y - 1, cz + 2, stone);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) put(out, cx + dx, y, cz + dz, dx == 0 && dz == 0 ? st(Blocks.WATER) : ((dx + dz) % 2 == 0 ? wall : stone));
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) {
                put(out, cx + dx, y + 1, cz + dz, post);
                put(out, cx + dx, y + 2, cz + dz, post);
            }
        box(out, cx - 1, y + 3, cz - 1, cx + 1, y + 3, cz + 1, roof);
        put(out, cx, y + 3, cz, stone);
        put(out, cx, y + 2, cz, st(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
    }

    private static void camp(Scenery.Sink out, Palette pal, int ax, int az, long seed) {
        Block[] cloth = {Blocks.WOOL.white(), Blocks.WOOL.red(), Blocks.WOOL.orange(), Blocks.WOOL.lightBlue(), Blocks.WOOL.green(), Blocks.WOOL.yellow()};
        BlockState tent = st(cloth[(int) (r(seed, 1) * cloth.length)]);
        int tx = ax + 6, tz = az - 2;
        int ty = pad(out, tx - 1, tx + 3, tz - 4, tz, st(pal.sub));
        for (int z = tz - 3; z <= tz; z++) {
            put(out, tx, ty, z, tent);
            put(out, tx + 2, ty, z, tent);
            put(out, tx + 1, ty + 1, z, tent);
            put(out, tx + 1, ty, z, z == tz - 3 ? tent : AIR);
        }
        put(out, tx + 1, ty, tz - 2, st(Blocks.CARPET.red()));
        int fx = ax + 2, fz = az - 1;
        put(out, fx, floor(fx, fz), fz, st(pal.snowy || pal.house == Palette.House.DEEPSLATE ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE));
        BlockState log = st(Blocks.SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        put(out, fx - 1, floor(fx - 1, fz - 2), fz - 2, log);
        put(out, fx, floor(fx, fz - 2), fz - 2, log);
        put(out, fx + 2, floor(fx + 2, fz - 1), fz - 1, st(Blocks.SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        put(out, ax + 4, floor(ax + 4, az - 4), az - 4, st(Blocks.BARREL));
        put(out, ax + 4, floor(ax + 4, az - 4) + 1, az - 4, st(Blocks.LANTERN));
    }

    private static void lighthouse(Scenery.Sink out, int ax, int az) {
        int cx = ax + 4, cz = az - 3;
        int y = pad(out, cx - 2, cx + 2, cz - 2, cz + 2, st(Blocks.STONE_BRICKS));
        BlockState white = st(Blocks.CONCRETE.white()), red = st(Blocks.CONCRETE.red());
        int h = 15;
        for (int i = 0; i < h; i++) {
            BlockState s = (i / 3) % 2 == 0 ? white : red;
            int rad = i < 4 ? 2 : 1;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    put(out, cx + dx, y + i, cz + dz, i < 4 && rad == 2 ? st(Blocks.STONE_BRICKS) : s);
                }
        }
        put(out, cx, y + 1, cz + 2, st(Blocks.DARK_OAK_PLANKS));
        put(out, cx, y + 2, cz + 2, st(Blocks.DARK_OAK_PLANKS));
        put(out, cx, y + 9, cz + 1, st(Blocks.GLASS_PANE));
        box(out, cx - 2, y + h, cz - 2, cx + 2, y + h, cz + 2, st(Blocks.SMOOTH_STONE_SLAB));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                boolean centre = dx == 0 && dz == 0;
                for (int i = 1; i <= 2; i++) put(out, cx + dx, y + h + i, cz + dz, centre ? st(Blocks.SEA_LANTERN) : st(Blocks.GLASS));
            }
        box(out, cx - 2, y + h + 3, cz - 2, cx + 2, y + h + 3, cz + 2, st(Blocks.RED_NETHER_BRICK_SLAB));
        box(out, cx - 1, y + h + 4, cz - 1, cx + 1, y + h + 4, cz + 1, red);
        put(out, cx, y + h + 5, cz, st(Blocks.END_ROD));
    }

    private static void snowmen(Scenery.Sink out, int ax, int az, long seed) {
        BlockState snow = st(Blocks.SNOW_BLOCK);
        BlockState head = st(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        int n = 2 + (int) (r(seed, 1) * 3);
        for (int k = 0; k < n; k++) {
            int x = ax + 1 + k * 3, z = az - 1 - (int) (r(seed, 5 + k) * 4);
            int y = floor(x, z);
            boolean tall = r(seed, 9 + k) < 0.5;
            put(out, x, y, z, snow);
            if (tall) put(out, x, y + 1, z, snow);
            put(out, x, y + (tall ? 2 : 1), z, head);
        }
        int fx = ax + 9, fz = az - 3;
        int fy = floor(fx, fz);
        box(out, fx - 1, fy, fz - 1, fx, fy + 1, fz, snow);
        put(out, fx - 1, fy + 2, fz, snow);
        put(out, fx, fy + 2, fz - 1, st(Blocks.LANTERN));
    }

    private static void pyramid(Scenery.Sink out, int ax, int az) {
        int cx = ax + 5, cz = az - 4;
        int y = pad(out, cx - 5, cx + 5, cz - 4, cz + 4, st(Blocks.SANDSTONE));
        BlockState sand = st(Blocks.SANDSTONE), cut = st(Blocks.CUT_SANDSTONE), accent = st(Blocks.DYED_TERRACOTTA.orange());
        for (int i = 0; i < 5; i++) {
            int rx = 5 - i, rz = 4 - Math.min(i, 4);
            for (int dx = -rx; dx <= rx; dx++)
                for (int dz = -rz; dz <= rz; dz++) {
                    boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
                    put(out, cx + dx, y + i, cz + dz, edge ? (i == 2 ? accent : (i % 2 == 0 ? sand : cut)) : sand);
                }
        }
        put(out, cx, y + 5, cz, st(Blocks.CHISELED_SANDSTONE));
        put(out, cx, y + 6, cz, st(Blocks.GOLD_BLOCK));
        put(out, cx, y, cz + 4, st(Blocks.DYED_TERRACOTTA.blue()));
        put(out, cx, y + 1, cz + 4, st(Blocks.DYED_TERRACOTTA.blue()));
        for (int side = -1; side <= 1; side += 2) {
            box(out, cx + side * 5, y, cz + 4, cx + side * 5, y + 4, cz + 4, cut);
            put(out, cx + side * 5, y + 5, cz + 4, st(Blocks.TORCH));
        }
    }

    private static void temple(Scenery.Sink out, int ax, int az, long seed) {
        int x0 = ax + 1, x1 = ax + 9, z0 = az - 6, z1 = az - 1;
        int y = pad(out, x0, x1, z0, z1, st(Blocks.MOSSY_COBBLESTONE));
        for (int tier = 0; tier < 3; tier++) {
            int a = x0 + tier, b = x1 - tier, c = z0 + tier, d = z1 - tier;
            for (int x = a; x <= b; x++)
                for (int z = c; z <= d; z++)
                    for (int i = 0; i < 2; i++) {
                        double q = r(seed, x * 31 + z * 7 + tier * 3 + i);
                        Block bl = q < 0.45 ? Blocks.MOSSY_COBBLESTONE : q < 0.8 ? Blocks.COBBLESTONE : Blocks.MOSSY_STONE_BRICKS;
                        put(out, x, y + tier * 2 + i, z, st(bl));
                    }
        }
        int cx = (x0 + x1) / 2;
        put(out, cx, y, z1, AIR);
        put(out, cx, y + 1, z1, AIR);
        put(out, cx, y + 6, az - 3, st(Blocks.CHISELED_STONE_BRICKS));
        put(out, cx, y + 7, az - 3, st(Blocks.EMERALD_BLOCK));
        for (int side = -1; side <= 1; side += 2) {
            put(out, cx + side, y, z1 + 1, st(Blocks.COBBLESTONE_WALL));
            put(out, cx + side, y + 1, z1 + 1, st(Blocks.TORCH));
            Scenery.blob(out, cx + side * 3, y + 6, az - 4, 1.6, 1.0, 1.6, Scenery.leaves(Blocks.JUNGLE_LEAVES), seed + side);
        }
    }

    private static void witchHut(Scenery.Sink out, Palette pal, int ax, int az) {
        int x0 = ax + 2, x1 = ax + 7, z0 = az - 5, z1 = az - 1;
        int ground = footprintBase(x0, x1, z0, z1);
        int y = ground + 3;
        BlockState plank = st(Blocks.SPRUCE_PLANKS), log = st(Blocks.OAK_LOG);
        for (int x = x0; x <= x1; x += x1 - x0)
            for (int z = z0; z <= z1; z += z1 - z0)
                for (int yy = groundTop(x, z) - 2; yy < y + 4; yy++) put(out, x, yy, z, log);
        box(out, x0, y, z0, x1, y, z1, plank);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
                if (!(edgeX || edgeZ) || (edgeX && edgeZ)) continue;
                for (int i = 1; i <= 3; i++) {
                    boolean window = i == 2 && ((edgeZ && x == x0 + 2) || (edgeX && z == z0 + 2));
                    boolean door = edgeZ && z == z1 && x == x1 - 2 && i <= 2;
                    put(out, x, y + i, z, door ? AIR : window ? st(Blocks.STAINED_GLASS_PANE.purple()) : plank);
                }
            }
        box(out, x0 - 1, y + 4, z0 - 1, x1 + 1, y + 4, z1 + 1, st(Blocks.SPRUCE_SLAB));
        box(out, x0, y + 4, z0, x1, y + 4, z1, plank);
        box(out, x0 + 1, y + 5, z0 + 1, x1 - 1, y + 5, z1 - 1, st(Blocks.SPRUCE_SLAB));
        box(out, x1 - 3, y, z1 + 1, x1 - 1, y, z1 + 1, plank);
        put(out, x1 - 3, y + 1, z1 + 1, st(Blocks.OAK_FENCE));
        put(out, x1 - 3, y + 2, z1 + 1, st(Blocks.SOUL_LANTERN));
        put(out, x0 + 1, y + 1, z0 + 1, st(Blocks.CAULDRON));
        put(out, x0 + 2, y + 2, z1 + 1, st(Blocks.RED_MUSHROOM));
    }

    private static void torii(Scenery.Sink out, int ax, int az) {
        int xa = ax + 2, xb = ax + 8, z = az - 2;
        int y = Math.max(floor(xa, z), floor(xb, z));
        BlockState red = st(Blocks.CONCRETE.red()), dark = st(Blocks.BLACKSTONE), beam = st(Blocks.MANGROVE_PLANKS);
        for (int x : new int[]{xa, xb}) {
            for (int yy = groundTop(x, z); yy < y; yy++) put(out, x, yy, z, dark);
            put(out, x, y, z, dark);
            box(out, x, y + 1, z, x, y + 5, z, red);
        }
        box(out, xa - 1, y + 4, z, xb + 1, y + 4, z, beam);
        box(out, xa - 2, y + 6, z, xb + 2, y + 6, z, red);
        box(out, xa - 2, y + 7, z, xb + 2, y + 7, z, st(Blocks.BLACKSTONE_SLAB));
        put(out, (xa + xb) / 2, y + 5, z, st(Blocks.GOLD_BLOCK));
        for (int x : new int[]{xa + 1, xb - 1}) put(out, x, y + 3, z, st(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
        for (int k = 0; k < 4; k++) {
            int px = (xa + xb) / 2, pz = z + 1 + k;
            if (pz <= az + 4) put(out, px, groundTop(px, pz), pz, st(k % 2 == 0 ? Blocks.SMOOTH_STONE : Blocks.CHERRY_PLANKS));
        }
        int sx = ax + 5, sz = az - 6;
        int sy = floor(sx, sz);
        put(out, sx, sy, sz, st(Blocks.STONE_BRICK_WALL));
        put(out, sx, sy + 1, sz, st(Blocks.CHISELED_STONE_BRICKS));
        put(out, sx, sy + 2, sz, st(Blocks.LANTERN));
    }

    private static void stonehenge(Scenery.Sink out, int ax, int az, long seed) {
        int cx = ax + 5, cz = az - 4;
        Block[] rock = {Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE};
        int[][] ring = {{-4, 0}, {-3, 2}, {-1, 3}, {1, 3}, {3, 2}, {4, 0}, {3, -2}, {1, -3}, {-1, -3}, {-3, -2}};
        int top = Integer.MIN_VALUE;
        for (int[] p : ring) top = Math.max(top, floor(cx + p[0], cz + p[1]));
        for (int k = 0; k < ring.length; k++) {
            int x = cx + ring[k][0], z = cz + ring[k][1];
            boolean fallen = r(seed, k) < 0.2;
            BlockState s = st(rock[(int) (r(seed, 20 + k) * rock.length)]);
            for (int yy = groundTop(x, z); yy < top + (fallen ? 1 : 3); yy++) put(out, x, yy, z, s);
            if (!fallen && k % 2 == 0) {
                int nx = cx + ring[(k + 1) % ring.length][0], nz = cz + ring[(k + 1) % ring.length][1];
                put(out, x, top + 3, z, s);
                put(out, (x + nx) / 2, top + 3, (z + nz) / 2, s);
                put(out, nx, top + 3, nz, s);
            }
        }
        put(out, cx, floor(cx, cz), cz, st(Blocks.CHISELED_STONE_BRICKS));
    }

    private static void graves(Scenery.Sink out, Palette pal, int ax, int az, long seed) {
        boolean pale = pal.house == Palette.House.PALE_OAK;
        for (int k = 0; k < 6; k++) {
            int x = ax + 1 + (k % 3) * 3, z = az - 1 - (k / 3) * 3;
            int g = groundTop(x, z);
            put(out, x, g, z, st(Blocks.COARSE_DIRT));
            put(out, x, g, z + 1, st(Blocks.PODZOL));
            double q = r(seed, k);
            BlockState stone = st(q < 0.4 ? Blocks.MOSSY_COBBLESTONE_WALL : q < 0.75 ? Blocks.COBBLESTONE_WALL : Blocks.STONE_BRICK_WALL);
            put(out, x, g + 1, z, stone);
            if (q > 0.6) put(out, x, g + 2, z, st(Blocks.STONE_BRICK_SLAB));
            if (r(seed, 10 + k) < 0.3) put(out, x, g + 1, z + 1, st(pale ? Blocks.CLOSED_EYEBLOSSOM : Blocks.WITHER_ROSE));
        }
        int lx = ax + 10, lz = az - 3;
        int ly = floor(lx, lz);
        put(out, lx, ly, lz, st(Blocks.DARK_OAK_FENCE));
        put(out, lx, ly + 1, lz, st(Blocks.DARK_OAK_FENCE));
        put(out, lx, ly + 2, lz, st(Blocks.SOUL_LANTERN));
        for (int x = ax; x <= ax + 9; x += 3) {
            int z = az + 1;
            if (r(seed, 40 + x) < 0.7) put(out, x, floor(x, z), z, st(Blocks.IRON_BARS));
        }
    }

    private static void bees(Scenery.Sink out, Palette pal, int ax, int az, long seed) {
        Block[] bloom = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.DANDELION, Blocks.AZURE_BLUET};
        for (int k = 0; k < 4; k++) {
            int x = ax + 1 + k * 3, z = az - 4;
            int y = floor(x, z);
            put(out, x, y, z, st(Blocks.OAK_FENCE));
            put(out, x, y + 1, z, st(k == 3 ? Blocks.BEE_NEST : Blocks.BEEHIVE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            if (k % 2 == 0) put(out, x, y + 2, z, st(Blocks.OAK_SLAB));
        }
        for (int x = ax; x <= ax + 10; x++)
            for (int z = az - 2; z <= az; z++) {
                if (r(seed, x * 13 + z) < 0.75) put(out, x, floor(x, z), z, st(bloom[(int) (r(seed, x * 7 + z * 3) * bloom.length)]));
            }
    }

    private static void pumpkins(Scenery.Sink out, int ax, int az, long seed) {
        for (int x = ax; x <= ax + 9; x++)
            for (int z = az - 4; z <= az; z++) {
                double q = r(seed, x * 17 + z * 5);
                int g = groundTop(x, z);
                if (q < 0.22) put(out, x, g + 1, z, st(Blocks.PUMPKIN));
                else if (q < 0.27) put(out, x, g + 1, z, st(Blocks.JACK_O_LANTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                else if (q < 0.5) put(out, x, g + 1, z, st(Blocks.LEAF_LITTER));
                else if (q < 0.56) put(out, x, g + 1, z, st(Blocks.HAY_BLOCK));
            }
        scarecrow(out, ax + 5, az - 5);
    }

    private static void scarecrow(Scenery.Sink out, int x, int z) {
        int y = floor(x, z);
        put(out, x, y, z, st(Blocks.OAK_FENCE));
        put(out, x, y + 1, z, st(Blocks.HAY_BLOCK));
        put(out, x - 1, y + 1, z, st(Blocks.OAK_FENCE));
        put(out, x + 1, y + 1, z, st(Blocks.OAK_FENCE));
        put(out, x, y + 2, z, st(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
    }

    private static void haystacks(Scenery.Sink out, int ax, int az, long seed) {
        BlockState hay = st(Blocks.HAY_BLOCK);
        for (int k = 0; k < 3; k++) {
            int x = ax + 1 + k * 4, z = az - 2 - (int) (r(seed, k) * 3);
            int y = footprintBase(x, x + 1, z - 1, z);
            foundation(out, x, x + 1, z - 1, z, y, st(Blocks.DIRT));
            box(out, x, y, z - 1, x + 1, y + 1, z, hay);
            put(out, x, y + 2, z - 1, hay);
            if (r(seed, 5 + k) < 0.5) put(out, x + 1, y + 2, z, hay.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        }
        scarecrow(out, ax + 10, az - 1);
    }

    private static void trestle(Scenery.Sink out, int ax, int az) {
        int z = az - 3;
        int deck = footprintBase(ax, ax + 10, z, z) + 3;
        for (int x = ax; x <= ax + 10; x++) {
            put(out, x, deck, z, st(Blocks.OAK_PLANKS));
            put(out, x, deck + 1, z, st(Blocks.RAIL));
            if ((x - ax) % 5 == 0) {
                for (int yy = groundTop(x, z) + 1; yy < deck; yy++) put(out, x, yy, z, st(Blocks.OAK_FENCE));
                for (int yy = groundTop(x, z - 1) + 1; yy < deck; yy++) put(out, x, yy, z - 1, st(Blocks.OAK_LOG));
                put(out, x, deck, z - 1, st(Blocks.OAK_PLANKS));
                put(out, x, deck - 1, z + 1, st(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true));
                put(out, x, deck, z + 1, st(Blocks.OAK_SLAB));
            }
        }
        int cx = ax + 7, cy = floor(cx, az);
        put(out, cx, cy, az, st(Blocks.BARREL));
        put(out, cx + 1, cy, az, st(Blocks.RAW_GOLD_BLOCK));
        put(out, cx + 1, floor(cx + 1, az - 1), az - 1, st(Blocks.BARREL));
    }

    private static void endTower(Scenery.Sink out, int ax, int az) {
        int cx = ax + 5, cz = az - 3;
        int y = pad(out, cx - 1, cx + 1, cz - 1, cz + 1, st(Blocks.END_STONE_BRICKS));
        BlockState purpur = st(Blocks.PURPUR_BLOCK), pillar = st(Blocks.PURPUR_PILLAR), glass = st(Blocks.STAINED_GLASS.magenta());
        int h = 11;
        for (int i = 0; i < h; i++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    put(out, cx + dx, y + i, cz + dz, corner ? pillar : (i % 4 == 2 && (dx != 0 || dz != 0) ? glass : purpur));
                }
        for (int i = 0; i < 4; i++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (i == 0 || i == 3) put(out, cx + dx, y + h + i, cz + dz, i == 3 && !edge ? st(Blocks.PURPUR_SLAB) : purpur);
                    else if (edge) put(out, cx + dx, y + h + i, cz + dz, Math.abs(dx) == 2 && Math.abs(dz) == 2 ? pillar : glass);
                }
        for (int dx = -2; dx <= 2; dx += 4)
            for (int dz = -2; dz <= 2; dz += 4) put(out, cx + dx, y + h + 4, cz + dz, st(Blocks.END_ROD));
        put(out, cx, y + h + 1, cz, st(Blocks.SHULKER_BOX));
        put(out, cx, y + h + 4, cz, st(Blocks.DRAGON_HEAD));
    }

    private static void bastion(Scenery.Sink out, Palette pal, int ax, int az, long seed) {
        int z = az - 4;
        int y = pad(out, ax, ax + 10, z - 1, z + 1, st(Blocks.BLACKSTONE));
        Block[] wall = {Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE};
        for (int x = ax; x <= ax + 10; x++) {
            int h = 2 + (int) (4.5 * Math.abs(Math.sin((x - ax) * 0.55 + r(seed, 1) * 3)));
            for (int i = 0; i < h; i++) {
                double q = r(seed, x * 11 + i);
                put(out, x, y + i, z, st(wall[q < 0.5 ? 0 : q < 0.72 ? 1 : q < 0.93 ? 2 : 3]));
            }
            if ((x - ax) % 5 == 0) {
                box(out, x, y, z + 1, x, y + h, z + 1, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
                put(out, x, y + h + 1, z + 1, st(pal.house == Palette.House.WARPED ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
            }
        }
        int cx = ax + 5;
        box(out, cx - 1, y, z + 1, cx + 1, y, z + 1, st(Blocks.GOLD_BLOCK));
        put(out, cx, y + 1, z + 1, st(Blocks.GOLD_BLOCK));
        put(out, cx - 2, y, z + 2, st(Blocks.MAGMA_BLOCK));
        put(out, cx + 2, y, z + 2, st(Blocks.NETHER_GOLD_ORE));
    }

    private static void sculkGate(Scenery.Sink out, int ax, int az) {
        int x0 = ax + 1, x1 = ax + 9, z = az - 4;
        int y = pad(out, x0 - 1, x1 + 1, z - 1, z + 2, st(Blocks.DEEPSLATE_BRICKS));
        box(out, x0 - 1, y - 1, z - 1, x1 + 1, y - 1, z + 2, st(Blocks.SCULK));
        BlockState frame = st(Blocks.REINFORCED_DEEPSLATE);
        for (int x = x0; x <= x1; x++)
            for (int i = 0; i <= 6; i++) {
                boolean edge = x == x0 || x == x1 || i == 6;
                if (edge) put(out, x, y + i, z, frame);
            }
        for (int x = x0 - 1; x <= x1 + 1; x += x1 - x0 + 2) {
            box(out, x, y, z + 1, x, y + 2, z + 1, st(Blocks.DEEPSLATE_TILES));
            put(out, x, y + 3, z + 1, st(Blocks.SOUL_LANTERN));
        }
        put(out, (x0 + x1) / 2, y, z + 1, st(Blocks.SCULK_SHRIEKER));
        put(out, x0 + 2, y, z + 2, st(Blocks.SCULK_CATALYST));
        put(out, x1 - 2, y, z + 2, st(Blocks.SCULK_SENSOR));
    }

    private static void totems(Scenery.Sink out, int ax, int az, long seed) {
        Block[] paint = {Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.cyan(), Blocks.DYED_TERRACOTTA.white()};
        for (int k = 0; k < 3; k++) {
            int x = ax + 2 + k * 3, z = az - 2 - (k % 2) * 2;
            int y = floor(x, z);
            int h = 3 + (int) (r(seed, k) * 3);
            for (int i = 0; i < h; i++) put(out, x, y + i, z, i % 2 == 0 ? st(Blocks.ACACIA_LOG) : st(paint[(int) (r(seed, 10 + k * 5 + i) * paint.length)]));
            put(out, x, y + h, z, st(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            put(out, x, y + h + 1, z, st(Blocks.TORCH));
        }
        put(out, ax + 9, floor(ax + 9, az - 1), az - 1, st(Blocks.CAMPFIRE));
    }

    private static void iceArch(Scenery.Sink out, int ax, int az, long seed) {
        int z = az - 3;
        BlockState ice = st(Blocks.PACKED_ICE), blue = st(Blocks.BLUE_ICE);
        int base = Math.min(groundTop(ax + 1, z), groundTop(ax + 9, z));
        for (int x = ax + 1; x <= ax + 9; x++) {
            double t = (x - (ax + 5)) / 4.0;
            int top = base + 1 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - t * t)));
            int thick = Math.abs(x - (ax + 5)) >= 3 ? 99 : 2;
            for (int yy = top; yy > Math.max(base - 1, top - thick); yy--)
                for (int dz = 0; dz <= 1; dz++) put(out, x, yy, z - dz, r(seed, x * 7 + yy) < 0.3 ? blue : ice);
        }
        put(out, ax + 5, base + 8, z, blue);
        put(out, ax + 5, base + 5, z, st(Blocks.SEA_LANTERN));
    }
}
