package rttt.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static rttt.world.Track.*;

public final class Scenery {
    private Scenery() {}

    public interface Sink {
        void set(int x, int y, int z, BlockState s);
        boolean contains(int x, int z);
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static boolean isRoad(int z) {
        return z >= ROAD_Z0 && z <= ROAD_Z1;
    }

    /** Top solid block of the column, ponds included. */
    public static int groundTop(int x, int z) {
        int g = baseGround(x, z);
        Pond p = pondAt(x, z);
        if (p == null) return g;
        double e = p.reach(x, z);
        if (e < 1) return Math.min(g, p.level - 1 - (int) Math.round(2 * (1 - e)));
        // The rim is never lower than the surface, so the pond cannot spill.
        return e < 1.6 ? Math.max(g, p.level) : g;
    }

    /** Surface level of the pond over this column, or {@code Integer.MIN_VALUE} on dry land. */
    public static int pondSurface(int x, int z) {
        Pond p = pondAt(x, z);
        return p != null && p.reach(x, z) < 1 ? p.level : Integer.MIN_VALUE;
    }

    private static final int POND_CELL = 64;
    private static final Object NO_POND = new Object();
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Object> PONDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class Pond {
        final int cx, cz, level;
        final double rx, rz;

        Pond(int cx, int cz, double rx, double rz, int level) {
            this.cx = cx;
            this.cz = cz;
            this.rx = rx;
            this.rz = rz;
            this.level = level;
        }

        /** 0 at the centre, 1 at the waterline. */
        double reach(int x, int z) {
            double dx = (x - cx) / rx, dz = (z - cz) / rz;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static Pond pondAt(int x, int z) {
        if (z > -6 || z < -40) return null;
        int cell = Math.floorDiv(x, POND_CELL);
        for (int g = cell - 1; g <= cell + 1; g++) {
            Object o = PONDS.get(g);
            if (o == null) {
                o = makePond(g);
                if (PONDS.size() > 2048) PONDS.clear();
                PONDS.put(g, o);
            }
            if (o != NO_POND && ((Pond) o).reach(x, z) < 1.6) return (Pond) o;
        }
        return null;
    }

    private static Object makePond(int g) {
        int cx = g * POND_CELL + 16 + (int) (rand(g, 1, 501) * (POND_CELL - 32));
        int cz = -17 - (int) (rand(g, 2, 501) * 9);
        if (cx < START_X + 70) return NO_POND;
        Palette pal = Palette.at(cx, cz);
        if (rand(g, 0, 501) >= pal.pond) return NO_POND;
        double rx = 6 + rand(g, 3, 501) * 4, rz = 4 + rand(g, 4, 501) * 2;
        for (int dx = -14; dx <= 14; dx += 7) if (nearStructure(cx + dx, -12)) return NO_POND;
        int level = baseGround(cx, cz);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level = Math.min(level, baseGround(cx + (int) Math.round(Math.cos(a) * rx * 1.3), cz + (int) Math.round(Math.sin(a) * rz * 1.3)));
        }
        return new Pond(cx, cz, rx, rz, level);
    }

    private static int baseGround(int x, int z) {
        if (isRoad(z)) return (roadHalf(x) >> 1) - 1;
        int rf = (int) Math.floor(top(x));
        if (z > ROAD_Z1) {
            int t = rf - 2;
            if (z == FRONT_Z && noise1(x / 11.0, 41) > 0.45) t -= 1;
            return t;
        }
        int d = ROAD_Z0 - z;
        double road = top(x);
        double smooth = BASE_Y + smoothHeight(x);
        double w = clamp((d - 2) / 10.0, 0, 1);
        double base = road * (1 - w) + smooth * w;
        double hill = 0;
        if (d > 6) hill = Math.pow((d - 6) / 40.0, 1.25) * (9 + 9 * fbm2(x / 48.0, z / 30.0, 5, 3));
        double mount = 0;
        if (d > 36) {
            double t = clamp((d - 36) / 28.0, 0, 1);
            double r = 1 - Math.abs(fbm2(x / 70.0, z / 45.0, 9, 4));
            mount = t * t * (16 + 58 * r * r);
            Palette pal = Palette.at(x, z);
            if (pal.mesa) mount = Math.floor(mount / 5.0) * 5.0 + 2;
        }
        double small = d > 3 ? 1.3 * noise2(x / 7.0, z / 7.0, 3) : 0;
        return (int) Math.floor(base + hill + mount + small) - 1;
    }

    public static void buildChunk(int cx0, int cz0, Sink out) {
        if (cz0 > FRONT_Z || cz0 + 15 < BACK_Z) return;
        int z0 = Math.max(cz0, BACK_Z), z1 = Math.min(cz0 + 15, FRONT_Z);
        // Every column needs its own height and its neighbours'; sampling the noise once per column
        // instead of four times is the bulk of the saving when new chunks stream in at speed.
        Heights heights = new Heights(cx0 - 1, z0 - 1, 18, z1 - z0 + 3);
        for (int x = cx0; x < cx0 + 16; x++) {
            for (int z = z0; z <= z1; z++) {
                column(x, z, out, heights);
            }
        }
        stampObjects(cx0, cz0, out);
    }

    static BlockState leaves(Block b) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.PERSISTENT)) s = s.setValue(BlockStateProperties.PERSISTENT, true);
        return s;
    }

    private static final class Heights {
        final int x0, z0, depth;
        final int[] h;

        Heights(int x0, int z0, int width, int depth) {
            this.x0 = x0;
            this.z0 = z0;
            this.depth = depth;
            h = new int[width * depth];
            for (int i = 0; i < width; i++) for (int j = 0; j < depth; j++) h[i * depth + j] = groundTop(x0 + i, z0 + j);
        }

        int at(int x, int z) {
            return h[(x - x0) * depth + (z - z0)];
        }
    }

    private static void column(int x, int z, Sink out, Heights heights) {
        Palette pal = Palette.at(x, z);
        int top = heights.at(x, z);
        boolean road = isRoad(z);
        int d = ROAD_Z0 - z;
        int bottom;
        if (z >= FRONT_Z - 2) {
            bottom = BASE_Y - 46;
        } else {
            int n = Math.min(heights.at(x, z + 1), Math.min(heights.at(x - 1, z), heights.at(x + 1, z)));
            bottom = Math.min(top - 1, n - 3);
        }
        int subDepth = 3 + (int) (rand(x, z, 3) * 2);
        boolean mountain = d > 36;
        int rel = top - BASE_Y;

        for (int y = bottom; y <= top; y++) {
            BlockState s;
            int depth = top - y;
            if (road) {
                if (depth == 0) s = pal.road.defaultBlockState();
                else if (depth <= subDepth) s = Blocks.DIRT.defaultBlockState();
                else s = underground(x, y, z, depth);
            } else if (mountain) {
                if (depth == 0) {
                    boolean peak = rel > pal.peakLine + noise1(x / 9.0, 51) * 4;
                    s = (peak ? pal.peak : pal.mountainTop).defaultBlockState();
                } else if (pal.mesa) {
                    s = mesaBand(y);
                } else if (depth <= 2 && pal.mountainTop == Blocks.GRASS_BLOCK) {
                    s = Blocks.DIRT.defaultBlockState();
                } else {
                    s = pal.mountain == Blocks.STONE ? stoneVariant(x, y, z) : pal.mountain.defaultBlockState();
                }
            } else {
                if (depth == 0) s = pal.surface.defaultBlockState();
                else if (depth <= subDepth) s = (pal.mesa && depth > 1) ? mesaBand(y) : pal.sub.defaultBlockState();
                else s = underground(x, y, z, depth);
            }
            if (z >= FRONT_Z - 2 && !road) {
                BlockState p = pocket(x, y, z, top);
                if (p != null) s = p;
            }
            out.set(x, y, z, s);
        }

        if (road) {
            if ((roadHalf(x) & 1) == 1) {
                out.set(x, top + 1, z, pal.roadSlab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
            }
            return;
        }

        double fr = rand(x, z, 17);
        int fy = top + 1;
        if (z <= -6 && z >= -40) {
            Pond pond = pondAt(x, z);
            if (pond != null) {
                double reach = pond.reach(x, z);
                if (reach < 1) {
                    fillPond(x, z, top, pond.level, pal, fr, out);
                    return;
                }
                if (reach < 1.3 && pal.reeds && fr < 0.3 && pal.pondFluid == Blocks.WATER && top == pond.level) {
                    int h = 1 + (int) (rand(x, z, 31) * 3);
                    for (int i = 0; i < h; i++) out.set(x, fy + i, z, Blocks.SUGAR_CANE.defaultBlockState());
                    return;
                }
            }
        }
        if (!mountain && (z == ROAD_Z0 - 3 || z == ROAD_Z0 - 4) && pal.props.length > 0 && rand(x, z, 37) < pal.propDensity && !nearStructure(x, z)) {
            placeProp(x, fy, z, pal.props[(int) (rand(x, z, 39) * pal.props.length)], out);
            return;
        }
        if (pal.snowy && !mountain) {
            if (pal.surface != Blocks.SNOW_BLOCK) out.set(x, fy, z, Blocks.SNOW.defaultBlockState());
            if (fr < pal.floraDensity * 0.5 && z > -40) placeFlora(x, fy, z, pal, out);
            return;
        }
        if (mountain) {
            if (pal.bareMountain) return;
            if (fr < 0.15 && rel < pal.peakLine && pal.mountainTop == Blocks.GRASS_BLOCK) out.set(x, fy, z, Blocks.SHORT_GRASS.defaultBlockState());
            return;
        }
        double dens = pal.floraDensity;
        if (z > ROAD_Z1) dens *= 0.7;
        if (z == ROAD_Z1 + 1 || z == ROAD_Z0 - 1) dens *= 0.4;
        if (fr < dens) placeFlora(x, fy, z, pal, out);
    }

    private static void fillPond(int x, int z, int top, int level, Palette pal, double fr, Sink out) {
        out.set(x, top, z, pal.pondBed.defaultBlockState());
        boolean ice = pal.pondFluid == Blocks.ICE;
        for (int y = top + 1; y <= level; y++) {
            Block b = ice ? (y == level ? Blocks.ICE : Blocks.WATER) : pal.pondFluid;
            out.set(x, y, z, b.defaultBlockState());
        }
        if (pal.lilies && pal.pondFluid == Blocks.WATER && fr < 0.14) out.set(x, level + 1, z, Blocks.LILY_PAD.defaultBlockState());
        else if (pal.snowy && ice && fr < 0.12) out.set(x, level + 1, z, Blocks.SNOW.defaultBlockState());
    }

    private static void placeProp(int x, int y, int z, Block b, Sink out) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        if (b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.END_ROD || b == Blocks.COBWEB || b == Blocks.SHROOMLIGHT || b == Blocks.VERDANT_FROGLIGHT) {
            // Lights sit on a short post so they read as lamps, not as litter.
            Block post = b == Blocks.END_ROD ? Blocks.PURPUR_PILLAR : b == Blocks.SOUL_LANTERN ? Blocks.DEEPSLATE_BRICK_WALL : Blocks.COBBLESTONE_WALL;
            if (b != Blocks.COBWEB && b != Blocks.SHROOMLIGHT && b != Blocks.VERDANT_FROGLIGHT) {
                out.set(x, y, z, post.defaultBlockState());
                y++;
            }
        }
        out.set(x, y, z, s);
        if ((b == Blocks.HAY_BLOCK || b == Blocks.PUMPKIN || b == Blocks.SNOW_BLOCK || b == Blocks.PACKED_ICE || b == Blocks.BASALT || b == Blocks.BONE_BLOCK)
                && rand(x, z, 41) < 0.35) out.set(x, y + 1, z, s);
    }

    private static void placeFlora(int x, int y, int z, Palette pal, Sink out) {
        Block b = pal.flowers[(int) (rand(x, z, 19) * pal.flowers.length)];
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            out.set(x, y, z, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            out.set(x, y + 1, z, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            return;
        }
        if (s.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
            s = s.setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + (int) (rand(x, z, 23) * 4));
        }
        if (s.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) {
            s = s.setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) (rand(x, z, 23) * 4));
        }
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((int) (rand(x, z, 29) * 4)));
        }
        if (b == Blocks.SWEET_BERRY_BUSH) s = s.setValue(BlockStateProperties.AGE_3, 3);
        out.set(x, y, z, s);
    }

    private static final Block[] MESA = {
            Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.TERRACOTTA,
            Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.white(), Blocks.TERRACOTTA,
            Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.lightGray(), Blocks.DYED_TERRACOTTA.red(), Blocks.TERRACOTTA,
            Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.orange()
    };

    private static BlockState mesaBand(int y) {
        return MESA[Math.floorMod(y, MESA.length)].defaultBlockState();
    }

    private static BlockState stoneVariant(int x, int y, int z) {
        double n = noise2(x / 9.0 + z * 0.13, y / 6.0, 61);
        if (n > 0.62) return Blocks.ANDESITE.defaultBlockState();
        if (n < -0.66) return Blocks.GRANITE.defaultBlockState();
        double m = noise2(x / 8.0 - z * 0.1, y / 5.0, 62);
        if (m > 0.7) return Blocks.DIORITE.defaultBlockState();
        if (m < -0.75) return Blocks.TUFF.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockState underground(int x, int y, int z, int depth) {
        double deepLine = 13 + noise1(x / 6.0, 71) * 2.5;
        boolean deep = depth > deepLine;
        double r = rand(x * 3 + z, y, 81);
        if (deep) {
            if (r < 0.010) return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
            if (r < 0.022) return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
            if (r < 0.032) return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
            if (r < 0.040) return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
            if (r < 0.055) return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            double n = noise2(x / 7.0, y / 5.0, 72);
            if (n > 0.7) return Blocks.TUFF.defaultBlockState();
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (r < 0.030) return Blocks.COAL_ORE.defaultBlockState();
        if (r < 0.048) return Blocks.COPPER_ORE.defaultBlockState();
        if (r < 0.064) return Blocks.IRON_ORE.defaultBlockState();
        if (depth > 8 && r < 0.070) return Blocks.GOLD_ORE.defaultBlockState();
        if (depth > 8 && r < 0.073) return Blocks.EMERALD_ORE.defaultBlockState();
        double g = noise2(x / 5.0, y / 4.0, 73);
        if (depth < 7 && g > 0.72) return Blocks.GRAVEL.defaultBlockState();
        if (depth < 6 && g < -0.8) return Blocks.CLAY.defaultBlockState();
        return stoneVariant(x, y, z);
    }

    private static final int POCKET_SEG = 26;

    private static BlockState pocket(int x, int y, int z, int top) {
        int seg = Math.floorDiv(x, POCKET_SEG);
        int type = (int) (rand(seg, 0, 91) * 9);
        if (type == 0) return null;
        int w = 6 + (int) (rand(seg, 1, 91) * 5);
        int h = 3 + (int) (rand(seg, 2, 91) * 3);
        int cx = seg * POCKET_SEG + 3 + (int) (rand(seg, 3, 91) * (POCKET_SEG - w - 6));
        int roadF = (int) Math.floor(top(cx));
        int by = roadF - 7 - (int) (rand(seg, 4, 91) * 6);
        if (type == 3) { by -= 2; }
        int lx = x - cx, ly = y - by;
        if (lx < -1 || lx > w || ly < -1 || ly > h) return null;
        double ex = (lx - (w - 1) / 2.0) / (w / 2.0 + 0.4), ey = (ly - (h - 1) / 2.0) / (h / 2.0 + 0.4);
        double e = ex * ex + ey * ey;
        boolean inside = e < 1.0;
        boolean shell = e < 1.55;
        boolean back = z == FRONT_Z - 2;
        final int MID = FRONT_Z - 1, FRONT = FRONT_Z;
        switch (type) {
            case 1: {
                if (!inside) return null;
                if (back) return null;
                if (ly == 0 && lx == w / 2 && z == MID) return Blocks.TORCH.defaultBlockState();
                if (ly == h - 1 && lx == 1 && z == MID) return Blocks.COBWEB.defaultBlockState();
                return AIR;
            }
            case 2: {
                if (!shell) return null;
                if (e > 1.25) return Blocks.SMOOTH_BASALT.defaultBlockState();
                if (e > 0.95) return Blocks.CALCITE.defaultBlockState();
                if (e > 0.55 || back) return Blocks.AMETHYST_BLOCK.defaultBlockState();
                if (ly == 0 || ey < -0.3) return (rand(x, z, 95) < 0.5)
                        ? Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)
                        : AIR;
                return AIR;
            }
            case 3: {
                if (ly == h / 2 && lx >= 0 && lx < w) return Blocks.BONE_BLOCK.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                if (lx % 2 == 1 && lx < w - 1 && ly >= 0 && ly < h) return Blocks.BONE_BLOCK.defaultBlockState();
                return null;
            }
            case 4: {
                if (lx < 0 || lx >= w || ly < 0 || ly > 3) return null;
                if (back) return (ly == 3) ? Blocks.OAK_PLANKS.defaultBlockState() : null;
                if (ly == 3) return (lx == 0 || lx == w - 1 || z == MID) ? Blocks.OAK_PLANKS.defaultBlockState() : AIR;
                if (lx == 0 || lx == w - 1) return z == MID ? Blocks.OAK_FENCE.defaultBlockState() : AIR;
                if (ly == 0 && z == MID) return Blocks.RAIL.defaultBlockState();
                if (ly == 2 && lx == w / 2 && z == MID) return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
                if (ly == 2 && lx == 1 && z == FRONT) return Blocks.COBWEB.defaultBlockState();
                return AIR;
            }
            case 5: {
                if (!inside) return null;
                if (back) return Blocks.MOSS_BLOCK.defaultBlockState();
                if (ly == 0) {
                    double q = rand(x, z, 96);
                    if (q < 0.25) return Blocks.AZALEA.defaultBlockState();
                    if (q < 0.4) return Blocks.FLOWERING_AZALEA.defaultBlockState();
                    if (q < 0.8) return Blocks.MOSS_CARPET.defaultBlockState();
                    return AIR;
                }
                if (ly == h - 1 && rand(x, z, 97) < 0.4) return Blocks.SHROOMLIGHT.defaultBlockState();
                return AIR;
            }
            case 6: {
                if (!inside) return shell ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : null;
                if (back) return Blocks.DRIPSTONE_BLOCK.defaultBlockState();
                double q = rand(x, z, 98);
                if (ly == h - 1 && q < 0.6) return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN);
                if (ly == 0 && q > 0.6) return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP);
                if (ly == 0 && lx == 1 && z == MID) return Blocks.TORCH.defaultBlockState();
                return AIR;
            }
            case 7: {
                if (!inside) return null;
                if (back) return Blocks.GOLD_BLOCK.defaultBlockState();
                if (ly == 0 && Math.abs(lx - w / 2) <= 1) return (lx == w / 2) ? Blocks.RAW_GOLD_BLOCK.defaultBlockState()
                        : Blocks.GOLD_BLOCK.defaultBlockState();
                if (ly == 0 && lx == 1 && z == MID) return Blocks.TORCH.defaultBlockState();
                if (ly == 1 && lx == w / 2 && z == MID) return Blocks.EMERALD_BLOCK.defaultBlockState();
                return AIR;
            }
            default: {
                if (!inside) return null;
                if (back) return Blocks.BLACKSTONE.defaultBlockState();
                if (ly == 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
                if (ly == h - 1 && rand(x, z, 99) < 0.5) return Blocks.GLOWSTONE.defaultBlockState();
                return AIR;
            }
        }
    }

    static void put(Sink out, int x, int y, int z, BlockState s) {
        if (out.contains(x, z)) out.set(x, y, z, s);
    }

    private static void stampObjects(int cx0, int cz0, Sink out) {
        int x0 = cx0 - 10, x1 = cx0 + 25, z0 = cz0 - 10, z1 = cz0 + 25;
        int cell = 5;
        for (int gx = Math.floorDiv(x0, cell); gx <= Math.floorDiv(x1, cell); gx++) {
            for (int gz = Math.floorDiv(z0, cell); gz <= Math.floorDiv(z1, cell); gz++) {
                int tx = gx * cell + (int) (rand(gx, gz, 201) * cell);
                int tz = gz * cell + (int) (rand(gx, gz, 202) * cell);
                if (tz > -6 || tz < -70) continue;
                if (tx < x0 || tx > x1 || tz < z0 || tz > z1) continue;
                Palette pal = Palette.at(tx, tz);
                double dens = pal.treeDensity * (tz < -18 ? 0.75 : 1.0) * (tz < -40 ? 0.6 : 1.0);
                if (rand(gx, gz, 203) >= dens) continue;
                if (nearStructure(tx, tz)) continue;
                Pond pond = pondAt(tx, tz);
                if (pond != null && pond.reach(tx, tz) < (pal.tree == Palette.Tree.MANGROVE ? 0.85 : 1.25)) continue;
                Palette.Tree t = (pal.tree2 != Palette.Tree.NONE && rand(gx, gz, 204) < 0.35) ? pal.tree2 : pal.tree;
                int ty = groundTop(tx, tz) + 1;
                long seed = hash(tx, tz, 205);
                tree(out, t, tx, ty, tz, seed, pal);
            }
        }
        for (int lx = Math.floorDiv(x0, 40) * 40; lx <= x1; lx += 40) {
            int px = lx + 7;
            if (px < x0 || px > x1) continue;
            int pz = ROAD_Z0 - 1;
            if (!out.contains(px, pz)) continue;
            Palette pal = Palette.at(px, pz);
            int y = groundTop(px, pz) + 1;
            Block fence = fenceFor(pal);
            boolean eerie = pal.house == Palette.House.DEEPSLATE || pal.house == Palette.House.BLACKSTONE || pal.house == Palette.House.WARPED;
            put(out, px, y, pz, fence.defaultBlockState());
            put(out, px, y + 1, pz, fence.defaultBlockState());
            put(out, px, y + 2, pz, (pal.house == Palette.House.PURPUR ? Blocks.END_ROD : eerie ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState());
        }
        int seg = 168;
        for (int s = Math.floorDiv(x0 - 20, seg); s <= Math.floorDiv(x1 + 20, seg); s++) {
            if (rand(s, 0, 301) > 0.82) continue;
            int ax = s * seg + 30 + (int) (rand(s, 1, 301) * (seg - 60));
            if (ax < START_X + 40) continue;
            int az = -12;
            Palette pal = Palette.at(ax, az);
            double kind = rand(s, 2, 301);
            Palette.Landmark mark = pal.landmarks.length == 0 ? null : pal.landmarks[(int) (rand(s, 5, 301) * pal.landmarks.length)];
            if (kind < 0.38) house(out, pal.house, ax, az, hash(s, 3, 301));
            else if (kind < 0.74 && mark != null) Landmarks.build(out, mark, pal, ax, az, hash(s, 6, 301));
            else if (kind < 0.84 && pal.tree == Palette.Tree.OAK) windmill(out, ax, az);
            else if (kind < 0.93 && !pal.snowy && pal.surface == Blocks.GRASS_BLOCK) farm(out, ax, az, hash(s, 4, 301));
            else if (kind < 0.97) ruinedPortal(out, ax, az);
            else house(out, pal.house, ax, az, hash(s, 3, 301));
        }
        int cseg = 700;
        for (int s = Math.floorDiv(x0 - 20, cseg); s <= Math.floorDiv(x1 + 20, cseg); s++) {
            if (rand(s, 0, 401) > 0.7) continue;
            int ax = s * cseg + 100 + (int) (rand(s, 1, 401) * 450);
            castle(out, ax, -64 - (int) (rand(s, 2, 401) * 12));
        }
    }

    public static boolean nearStructure(int x, int z) {
        if (z < -20 || z > -4) return false;
        int seg = 168;
        int s = Math.floorDiv(x, seg);
        for (int k = s - 1; k <= s + 1; k++) {
            if (rand(k, 0, 301) > 0.82) continue;
            int ax = k * seg + 30 + (int) (rand(k, 1, 301) * (seg - 60));
            if (x >= ax - 4 && x <= ax + 13) return true;
        }
        return false;
    }

    private static Block fenceFor(Palette pal) {
        switch (pal.house) {
            case SPRUCE: case IGLOO: return Blocks.SPRUCE_FENCE;
            case ACACIA: return Blocks.ACACIA_FENCE;
            case JUNGLE: return Blocks.JUNGLE_FENCE;
            case CHERRY: return Blocks.CHERRY_FENCE;
            case BIRCH: return Blocks.BIRCH_FENCE;
            case DARK_OAK: case MUSHROOM: return Blocks.DARK_OAK_FENCE;
            case PALE_OAK: return Blocks.PALE_OAK_FENCE;
            case SANDSTONE: case TERRACOTTA: return Blocks.SANDSTONE_WALL;
            case POPLAR: return Blocks.POPLAR_FENCE;
            case MANGROVE: return Blocks.MANGROVE_FENCE;
            case DEEPSLATE: return Blocks.DEEPSLATE_BRICK_WALL;
            case CRIMSON: return Blocks.CRIMSON_FENCE;
            case WARPED: return Blocks.WARPED_FENCE;
            case BLACKSTONE: return Blocks.BLACKSTONE_WALL;
            case PURPUR: return Blocks.END_STONE_BRICK_WALL;
            default: return Blocks.OAK_FENCE;
        }
    }

    static double r(long seed, int k) {
        return (mix(seed + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    static void blob(Sink out, int cx, int cy, int cz, double rx, double ry, double rz, BlockState s, long seed) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++)
            for (int dy = -iy; dy <= iy; dy++)
                for (int dz = -iz; dz <= iz; dz++) {
                    double e = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
                    if (e > 1.0) continue;
                    if (e > 0.72 && r(seed, dx * 31 + dy * 7 + dz * 131) < 0.35) continue;
                    put(out, cx + dx, cy + dy, cz + dz, s);
                }
    }

    static void trunk(Sink out, int x, int y, int z, int h, BlockState log) {
        for (int i = 0; i < h; i++) put(out, x, y + i, z, log);
    }

    private static void tree(Sink out, Palette.Tree t, int x, int y, int z, long seed, Palette pal) {
        switch (t) {
            case OAK: {
                int h = 4 + (int) (r(seed, 1) * 3);
                blob(out, x, y + h, z, 2.6, 2.2, 2.6, leaves(Blocks.OAK_LEAVES), seed);
                trunk(out, x, y, z, h, Blocks.OAK_LOG.defaultBlockState());
                break;
            }
            case BIRCH: {
                int h = 5 + (int) (r(seed, 1) * 3);
                blob(out, x, y + h, z, 2.2, 2.4, 2.2, leaves(Blocks.BIRCH_LEAVES), seed);
                trunk(out, x, y, z, h, Blocks.BIRCH_LOG.defaultBlockState());
                break;
            }
            case POPLAR: {
                int h = 7 + (int) (r(seed, 1) * 4);
                double c = r(seed, 2);
                Block lv = c < 0.34 ? Blocks.RED_POPLAR_LEAVES : (c < 0.67 ? Blocks.ORANGE_POPLAR_LEAVES : Blocks.YELLOW_POPLAR_LEAVES);
                blob(out, x, y + h - 2, z, 1.7, h * 0.48, 1.7, leaves(lv), seed);
                trunk(out, x, y, z, h, Blocks.POPLAR_LOG.defaultBlockState());
                break;
            }
            case SPRUCE: {
                int h = 8 + (int) (r(seed, 1) * 5);
                BlockState lv = leaves(Blocks.SPRUCE_LEAVES);
                int layers = h - 2;
                for (int i = 0; i < layers; i++) {
                    int yy = y + h + 1 - i;
                    int rad = (i == 0) ? 0 : (int) Math.min(3, (i + 1) / 2.4 + ((i % 2 == 0) ? 0 : 0.6));
                    for (int dx = -rad; dx <= rad; dx++)
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) > rad + (rad > 1 ? 1 : 0)) continue;
                            put(out, x + dx, yy, z + dz, lv);
                        }
                }
                trunk(out, x, y, z, h, Blocks.SPRUCE_LOG.defaultBlockState());
                if (pal.snowy) put(out, x, y + h + 2, z, Blocks.SNOW.defaultBlockState());
                break;
            }
            case ACACIA: {
                int h = 4 + (int) (r(seed, 1) * 2);
                BlockState log = Blocks.ACACIA_LOG.defaultBlockState();
                trunk(out, x, y, z, h, log);
                int dir = r(seed, 2) < 0.5 ? -1 : 1;
                int bx = x + dir, by = y + h;
                put(out, bx, by, z, log);
                put(out, bx + dir, by + 1, z, log);
                BlockState lv = leaves(Blocks.ACACIA_LEAVES);
                int cx = bx + dir, cy = by + 2;
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++)
                        if (Math.abs(dx) + Math.abs(dz) <= 4) put(out, cx + dx, cy, z + dz, lv);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        if (Math.abs(dx) + Math.abs(dz) <= 2) put(out, cx + dx, cy + 1, z + dz, lv);
                break;
            }
            case JUNGLE: {
                int h = 9 + (int) (r(seed, 1) * 5);
                blob(out, x, y + h, z, 3.4, 2.3, 3.4, leaves(Blocks.JUNGLE_LEAVES), seed);
                blob(out, x + 2, y + h - 4, z, 1.8, 1.2, 1.8, leaves(Blocks.JUNGLE_LEAVES), seed + 9);
                trunk(out, x, y, z, h, Blocks.JUNGLE_LOG.defaultBlockState());
                break;
            }
            case CHERRY: {
                int h = 5 + (int) (r(seed, 1) * 2);
                blob(out, x, y + h + 1, z, 3.6, 1.9, 3.4, leaves(Blocks.CHERRY_LEAVES), seed);
                trunk(out, x, y, z, h + 1, Blocks.CHERRY_LOG.defaultBlockState());
                break;
            }
            case DARK_OAK: case PALE_OAK: {
                boolean pale = t == Palette.Tree.PALE_OAK;
                int h = 6 + (int) (r(seed, 1) * 3);
                BlockState log = (pale ? Blocks.PALE_OAK_LOG : Blocks.DARK_OAK_LOG).defaultBlockState();
                blob(out, x, y + h, z, 3.6, 2.2, 3.6, leaves(pale ? Blocks.PALE_OAK_LEAVES : Blocks.DARK_OAK_LEAVES), seed);
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) trunk(out, x + dx, y, z + dz, h, log);
                if (pale) {
                    for (int k = 0; k < 5; k++) {
                        int hx = x + (int) (r(seed, 10 + k) * 6) - 3, hz = z + 2;
                        put(out, hx, y + h - 2, hz, Blocks.PALE_HANGING_MOSS.defaultBlockState());
                    }
                }
                break;
            }
            case GIANT_SPRUCE: {
                int h = 15 + (int) (r(seed, 1) * 7);
                BlockState lv = leaves(Blocks.SPRUCE_LEAVES);
                int layers = h - 4;
                for (int i = 0; i < layers; i++) {
                    int yy = y + h + 1 - i;
                    int rad = (i == 0) ? 0 : (int) Math.min(4, (i + 1) / 2.6 + ((i % 3 == 0) ? 0 : 0.7));
                    for (int dx = -rad; dx <= rad + 1; dx++)
                        for (int dz = -rad; dz <= rad + 1; dz++) {
                            double ddx = dx - 0.5, ddz = dz - 0.5;
                            if (ddx * ddx + ddz * ddz > (rad + 0.6) * (rad + 0.6)) continue;
                            put(out, x + dx, yy, z + dz, lv);
                        }
                }
                BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) trunk(out, x + dx, y, z + dz, h, log);
                break;
            }
            case ICE_SPIKE: {
                int h = 5 + (int) (r(seed, 1) * 10);
                boolean big = r(seed, 2) < 0.25;
                if (big) h += 8;
                BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
                for (int i = 0; i < h; i++) {
                    double f = i / (double) h;
                    int rad = big ? (f < 0.35 ? 2 : (f < 0.7 ? 1 : 0)) : (f < 0.3 ? 1 : 0);
                    for (int dx = -rad; dx <= rad; dx++)
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (rad > 0 && Math.abs(dx) == rad && Math.abs(dz) == rad) continue;
                            put(out, x + dx, y + i, z + dz, ice);
                        }
                }
                break;
            }
            case SWAMP_OAK: {
                int h = 4 + (int) (r(seed, 1) * 3);
                BlockState lv = leaves(Blocks.OAK_LEAVES);
                blob(out, x, y + h, z, 3.4, 1.6, 3.4, lv, seed);
                trunk(out, x, y, z, h + 1, Blocks.OAK_LOG.defaultBlockState());
                for (int k = 0; k < 6; k++) {
                    int vx = x + (int) Math.round((r(seed, 20 + k) - 0.5) * 6), vz = z + (int) Math.round((r(seed, 30 + k) - 0.5) * 6);
                    int len = 1 + (int) (r(seed, 40 + k) * 3);
                    for (int i = 1; i <= len; i++) put(out, vx, y + h - 1 - i, vz, lv);
                }
                break;
            }
            case BAMBOO: {
                int h = 7 + (int) (r(seed, 1) * 9);
                for (int k = 0; k < 3; k++) {
                    int bx = x + (k == 0 ? 0 : (int) Math.round((r(seed, 5 + k) - 0.5) * 3));
                    int bz = z + (k == 0 ? 0 : (int) Math.round((r(seed, 8 + k) - 0.5) * 3));
                    int hh = Math.max(4, h - (int) (r(seed, 11 + k) * 5));
                    for (int i = 0; i < hh; i++) {
                        BlockState b = Blocks.BAMBOO.defaultBlockState();
                        if (b.hasProperty(BlockStateProperties.BAMBOO_LEAVES)) {
                            b = b.setValue(BlockStateProperties.BAMBOO_LEAVES, i >= hh - 2 ? BambooLeaves.LARGE : (i >= hh - 4 ? BambooLeaves.SMALL : BambooLeaves.NONE));
                        }
                        put(out, bx, y + i, bz, b);
                    }
                }
                break;
            }
            case PALM: {
                int h = 6 + (int) (r(seed, 1) * 4);
                int lean = r(seed, 2) < 0.5 ? -1 : 1;
                BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
                int tx = x;
                for (int i = 0; i < h; i++) {
                    if (i == h / 2 || i == h - 2) tx += lean;
                    put(out, tx, y + i, z, log);
                }
                BlockState lv = leaves(Blocks.JUNGLE_LEAVES);
                int cy = y + h;
                put(out, tx, cy, z, lv);
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
                for (int[] d : dirs) {
                    int len = (d[0] != 0 && d[1] != 0) ? 2 : 3;
                    for (int i = 1; i <= len; i++) put(out, tx + d[0] * i, cy - (i == len ? 1 : 0), z + d[1] * i, lv);
                }
                                break;
            }
            case BOULDER: {
                BlockState st = (r(seed, 1) < 0.5 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
                blob(out, x, y, z, 1.6 + r(seed, 2), 1.2 + r(seed, 3) * 0.8, 1.6 + r(seed, 4), st, seed);
                break;
            }
            case CACTUS: {
                int h = 2 + (int) (r(seed, 1) * 3);
                trunk(out, x, y, z, h, Blocks.CACTUS.defaultBlockState());
                if (r(seed, 2) < 0.3) put(out, x, y + h, z, Blocks.CACTUS_FLOWER.defaultBlockState());
                break;
            }
            case MUSHROOM: {
                boolean red = r(seed, 1) < 0.5;
                int h = 4 + (int) (r(seed, 2) * 3);
                trunk(out, x, y, z, h, Blocks.MUSHROOM_STEM.defaultBlockState());
                if (red) {
                    BlockState cap = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
                    for (int dy = 0; dy <= 2; dy++) {
                        int rad = dy == 2 ? 1 : 2;
                        for (int dx = -rad; dx <= rad; dx++)
                            for (int dz = -rad; dz <= rad; dz++) {
                                if (dy < 2 && Math.abs(dx) < rad && Math.abs(dz) < rad) continue;
                                put(out, x + dx, y + h - 1 + dy, z + dz, cap);
                            }
                    }
                } else {
                    BlockState cap = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++)
                            if (Math.abs(dx) + Math.abs(dz) <= 5) put(out, x + dx, y + h, z + dz, cap);
                }
                break;
            }
            case MANGROVE: {
                int h = 5 + (int) (r(seed, 1) * 3);
                BlockState roots = Blocks.MANGROVE_ROOTS.defaultBlockState(), log = Blocks.MANGROVE_LOG.defaultBlockState();
                int lift = 2 + (int) (r(seed, 2) * 2);
                int[][] legs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : legs) {
                    for (int i = 0; i < lift; i++) put(out, x + d[0] * (i < lift - 1 ? 2 : 1), y - 1 + i, z + d[1] * (i < lift - 1 ? 2 : 1), roots);
                    put(out, x + d[0], y + lift - 1, z + d[1], roots);
                }
                trunk(out, x, y + lift, z, h, log);
                blob(out, x, y + lift + h, z, 3.2, 2.0, 3.2, leaves(Blocks.MANGROVE_LEAVES), seed);
                blob(out, x + (r(seed, 3) < 0.5 ? 2 : -2), y + lift + h - 2, z, 1.8, 1.2, 1.8, leaves(Blocks.MANGROVE_LEAVES), seed + 5);
                break;
            }
            case AZALEA: {
                int h = 3 + (int) (r(seed, 1) * 2);
                int lean = r(seed, 2) < 0.5 ? -1 : 1;
                BlockState log = Blocks.OAK_LOG.defaultBlockState();
                trunk(out, x, y, z, h, log);
                put(out, x + lean, y + h, z, log);
                blob(out, x + lean, y + h + 1, z, 3.0, 1.3, 3.0, leaves(Blocks.AZALEA_LEAVES), seed);
                blob(out, x + lean, y + h + 2, z, 1.8, 1.0, 1.8, leaves(Blocks.FLOWERING_AZALEA_LEAVES), seed + 3);
                put(out, x, y - 1, z, Blocks.ROOTED_DIRT.defaultBlockState());
                break;
            }
            case CRIMSON: case WARPED: {
                boolean crimson = t == Palette.Tree.CRIMSON;
                int h = 5 + (int) (r(seed, 1) * 6);
                BlockState stem = (crimson ? Blocks.CRIMSON_STEM : Blocks.WARPED_STEM).defaultBlockState();
                BlockState wart = (crimson ? Blocks.NETHER_WART_BLOCK : Blocks.WARPED_WART_BLOCK).defaultBlockState();
                BlockState glow = Blocks.SHROOMLIGHT.defaultBlockState();
                int rad = h > 8 ? 3 : 2;
                for (int dy = 0; dy <= 3; dy++) {
                    int rr = dy == 3 ? rad - 1 : rad;
                    for (int dx = -rr; dx <= rr; dx++)
                        for (int dz = -rr; dz <= rr; dz++) {
                            boolean corner = Math.abs(dx) == rr && Math.abs(dz) == rr;
                            if (corner && (dy == 3 || rr > 1)) continue;
                            boolean rim = Math.abs(dx) == rr || Math.abs(dz) == rr;
                            if (dy == 0 && !(rim && r(seed, dx * 13 + dz * 7) < 0.55)) continue;
                            put(out, x + dx, y + h - 2 + dy, z + dz, r(seed, 100 + dx * 17 + dz * 5 + dy) < 0.09 ? glow : wart);
                        }
                }
                trunk(out, x, y, z, h, stem);
                break;
            }
            case CHORUS: {
                int h = 3 + (int) (r(seed, 1) * 3);
                BlockState up = Blocks.CHORUS_PLANT.defaultBlockState().setValue(BlockStateProperties.UP, true).setValue(BlockStateProperties.DOWN, true);
                trunk(out, x, y, z, h, up);
                int arms = 2 + (int) (r(seed, 2) * 2);
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int k = 0; k < arms; k++) {
                    int[] d = dirs[(k + (int) (r(seed, 3) * 4)) % 4];
                    int ay = y + h - 1 - (k % 2);
                    BlockState side = Blocks.CHORUS_PLANT.defaultBlockState()
                            .setValue(d[0] != 0 ? BlockStateProperties.EAST : BlockStateProperties.SOUTH, true)
                            .setValue(d[0] != 0 ? BlockStateProperties.WEST : BlockStateProperties.NORTH, true).setValue(BlockStateProperties.UP, true);
                    put(out, x + d[0], ay, z + d[1], side);
                    int ah = 1 + (int) (r(seed, 10 + k) * 3);
                    for (int i = 1; i <= ah; i++) put(out, x + d[0], ay + i, z + d[1], up);
                    put(out, x + d[0], ay + ah + 1, z + d[1], Blocks.CHORUS_FLOWER.defaultBlockState());
                }
                put(out, x, y + h, z, Blocks.CHORUS_FLOWER.defaultBlockState());
                break;
            }
            case OBSIDIAN_PILLAR: {
                int h = 7 + (int) (r(seed, 1) * 12);
                int rad = r(seed, 2) < 0.4 ? 2 : 1;
                BlockState ob = Blocks.OBSIDIAN.defaultBlockState();
                for (int i = -2; i < h; i++)
                    for (int dx = -rad; dx <= rad; dx++)
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (rad > 1 && Math.abs(dx) == rad && Math.abs(dz) == rad) continue;
                            put(out, x + dx, y + i, z + dz, i == h - 1 && r(seed, dx * 7 + dz) < 0.3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : ob);
                        }
                put(out, x, y + h, z, Blocks.BEDROCK.defaultBlockState());
                put(out, x, y + h + 1, z, Blocks.END_ROD.defaultBlockState());
                break;
            }
            case FOSSIL: {
                int len = 5 + (int) (r(seed, 1) * 4);
                BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                BlockState rib = Blocks.BONE_BLOCK.defaultBlockState();
                for (int i = 0; i < len; i++) {
                    put(out, x + i, y + 3, z, spine);
                    if (i % 2 == 0) {
                        for (int side = -1; side <= 1; side += 2) {
                            put(out, x + i, y + 3, z + side, rib.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
                            for (int dy = 0; dy <= 2; dy++) put(out, x + i, y + dy, z + side * 2, rib);
                        }
                    }
                }
                break;
            }
            case BASALT_PILLAR: {
                BlockState basalt = Blocks.BASALT.defaultBlockState();
                int n = 2 + (int) (r(seed, 1) * 4);
                for (int k = 0; k < n; k++) {
                    int bx = x + (int) Math.round((r(seed, 5 + k) - 0.5) * 3), bz = z + (int) Math.round((r(seed, 15 + k) - 0.5) * 3);
                    trunk(out, bx, y - 1, bz, 3 + (int) (r(seed, 25 + k) * 9), basalt);
                }
                break;
            }
            case SCULK_PILLAR: {
                int h = 3 + (int) (r(seed, 1) * 5);
                boolean broken = r(seed, 2) < 0.4;
                BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
                for (int i = 0; i < h; i++) put(out, x, y + i, z, r(seed, 10 + i) < 0.3 ? cracked : brick);
                put(out, x, y + h, z, broken ? Blocks.SCULK.defaultBlockState() : Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                if (!broken) put(out, x, y + h + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    if ((dx != 0 || dz != 0) && r(seed, 30 + dx * 3 + dz) < 0.5) put(out, x + dx, y, z + dz, Blocks.SCULK.defaultBlockState());
                break;
            }
            case DEAD: {
                int h = 3 + (int) (r(seed, 1) * 3);
                BlockState log = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
                trunk(out, x, y, z, h, log);
                int dir = r(seed, 2) < 0.5 ? -1 : 1;
                put(out, x + dir, y + h - 1, z, log.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                put(out, x + dir * 2, y + h, z, log);
                if (r(seed, 3) < 0.5) put(out, x - dir, y + h - 2, z, log.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                break;
            }
            default:
                break;
        }
    }

    private static final class Mat {
        BlockState planks, log, stairs, slab, door, glass, foundation;
    }

    private static Mat mat(Palette.House h) {
        Mat m = new Mat();
        Block planks, log, stairs, slab, door;
        switch (h) {
            case SPRUCE: planks = Blocks.SPRUCE_PLANKS; log = Blocks.SPRUCE_LOG; stairs = Blocks.SPRUCE_STAIRS; slab = Blocks.SPRUCE_SLAB; door = Blocks.SPRUCE_DOOR; break;
            case ACACIA: planks = Blocks.ACACIA_PLANKS; log = Blocks.ACACIA_LOG; stairs = Blocks.ACACIA_STAIRS; slab = Blocks.ACACIA_SLAB; door = Blocks.ACACIA_DOOR; break;
            case JUNGLE: planks = Blocks.JUNGLE_PLANKS; log = Blocks.JUNGLE_LOG; stairs = Blocks.JUNGLE_STAIRS; slab = Blocks.JUNGLE_SLAB; door = Blocks.JUNGLE_DOOR; break;
            case CHERRY: planks = Blocks.CHERRY_PLANKS; log = Blocks.CHERRY_LOG; stairs = Blocks.CHERRY_STAIRS; slab = Blocks.CHERRY_SLAB; door = Blocks.CHERRY_DOOR; break;
            case BIRCH: planks = Blocks.BIRCH_PLANKS; log = Blocks.BIRCH_LOG; stairs = Blocks.BIRCH_STAIRS; slab = Blocks.BIRCH_SLAB; door = Blocks.BIRCH_DOOR; break;
            case DARK_OAK: planks = Blocks.DARK_OAK_PLANKS; log = Blocks.DARK_OAK_LOG; stairs = Blocks.DARK_OAK_STAIRS; slab = Blocks.DARK_OAK_SLAB; door = Blocks.DARK_OAK_DOOR; break;
            case PALE_OAK: planks = Blocks.PALE_OAK_PLANKS; log = Blocks.PALE_OAK_LOG; stairs = Blocks.PALE_OAK_STAIRS; slab = Blocks.PALE_OAK_SLAB; door = Blocks.PALE_OAK_DOOR; break;
            case POPLAR: planks = Blocks.POPLAR_PLANKS; log = Blocks.POPLAR_LOG; stairs = Blocks.POPLAR_STAIRS; slab = Blocks.POPLAR_SLAB; door = Blocks.POPLAR_DOOR; break;
            case SANDSTONE: planks = Blocks.SANDSTONE; log = Blocks.CUT_SANDSTONE; stairs = Blocks.SANDSTONE_STAIRS; slab = Blocks.SMOOTH_SANDSTONE_SLAB; door = Blocks.JUNGLE_DOOR; break;
            case TERRACOTTA: planks = Blocks.TERRACOTTA; log = Blocks.DARK_OAK_LOG; stairs = Blocks.RED_SANDSTONE_STAIRS; slab = Blocks.SMOOTH_RED_SANDSTONE_SLAB; door = Blocks.DARK_OAK_DOOR; break;
            case MANGROVE: planks = Blocks.MANGROVE_PLANKS; log = Blocks.MANGROVE_LOG; stairs = Blocks.MANGROVE_STAIRS; slab = Blocks.MANGROVE_SLAB; door = Blocks.MANGROVE_DOOR; break;
            case DEEPSLATE: planks = Blocks.DEEPSLATE_BRICKS; log = Blocks.POLISHED_DEEPSLATE; stairs = Blocks.DEEPSLATE_BRICK_STAIRS; slab = Blocks.DEEPSLATE_BRICK_SLAB; door = Blocks.DARK_OAK_DOOR; break;
            case CRIMSON: planks = Blocks.CRIMSON_PLANKS; log = Blocks.CRIMSON_STEM; stairs = Blocks.CRIMSON_STAIRS; slab = Blocks.CRIMSON_SLAB; door = Blocks.CRIMSON_DOOR; break;
            case WARPED: planks = Blocks.WARPED_PLANKS; log = Blocks.WARPED_STEM; stairs = Blocks.WARPED_STAIRS; slab = Blocks.WARPED_SLAB; door = Blocks.WARPED_DOOR; break;
            case BLACKSTONE: planks = Blocks.POLISHED_BLACKSTONE_BRICKS; log = Blocks.BLACKSTONE; stairs = Blocks.BLACKSTONE_STAIRS; slab = Blocks.BLACKSTONE_SLAB; door = Blocks.CRIMSON_DOOR; break;
            case PURPUR: planks = Blocks.PURPUR_BLOCK; log = Blocks.PURPUR_PILLAR; stairs = Blocks.PURPUR_STAIRS; slab = Blocks.PURPUR_SLAB; door = Blocks.BIRCH_DOOR; break;
            case MUSHROOM: planks = Blocks.MUSHROOM_STEM; log = Blocks.MUSHROOM_STEM; stairs = Blocks.DARK_OAK_STAIRS; slab = Blocks.DARK_OAK_SLAB; door = Blocks.DARK_OAK_DOOR; break;
            default: planks = Blocks.OAK_PLANKS; log = Blocks.OAK_LOG; stairs = Blocks.OAK_STAIRS; slab = Blocks.OAK_SLAB; door = Blocks.OAK_DOOR; break;
        }
        m.planks = planks.defaultBlockState();
        m.log = log.defaultBlockState();
        m.stairs = stairs.defaultBlockState();
        m.slab = slab.defaultBlockState();
        m.door = door.defaultBlockState();
        m.glass = (h == Palette.House.PURPUR ? Blocks.STAINED_GLASS_PANE.magenta() : Blocks.GLASS_PANE).defaultBlockState();
        Block base = switch (h) {
            case DEEPSLATE -> Blocks.COBBLED_DEEPSLATE;
            case CRIMSON, WARPED -> Blocks.NETHER_BRICKS;
            case BLACKSTONE -> Blocks.BLACKSTONE;
            case PURPUR -> Blocks.END_STONE_BRICKS;
            case SANDSTONE -> Blocks.SANDSTONE;
            default -> Blocks.COBBLESTONE;
        };
        m.foundation = base.defaultBlockState();
        return m;
    }

    static int footprintBase(int x0, int x1, int z0, int z1) {
        int max = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) max = Math.max(max, groundTop(x, z));
        return max + 1;
    }

    static void foundation(Sink out, int x0, int x1, int z0, int z1, int y0, BlockState f) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = groundTop(x, z) + 1; y < y0; y++) put(out, x, y, z, f);
    }

    private static void house(Sink out, Palette.House kind, int ax, int az, long seed) {
        if (kind == Palette.House.IGLOO) { igloo(out, ax, az); return; }
        Mat m = mat(kind);
        int w = 7 + (r(seed, 1) < 0.5 ? 0 : 2), dpt = 5, wh = 4;
        int x0 = ax, x1 = ax + w - 1, z0 = az - dpt + 1, z1 = az;
        int y0 = footprintBase(x0 - 1, x1 + 1, z0 - 1, z1 + 1);
        foundation(out, x0 - 1, x1 + 1, z0 - 1, z1 + 1, y0, m.foundation);
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) put(out, x, y0 - 1, z, m.foundation);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                put(out, x, y0, z, m.planks);
                for (int y = y0 + 1; y <= y0 + wh; y++) {
                    boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
                    BlockState s;
                    if (edgeX && edgeZ) s = m.log;
                    else if (edgeX || edgeZ) {
                        s = m.planks;
                        boolean window = y == y0 + 2 && ((edgeZ && (x - x0) % 3 == 1 && x != x0 + w / 2) || (edgeX && z == z0 + dpt / 2));
                        if (window) s = m.glass;
                        if (y == y0 + wh) s = m.log;
                    } else s = AIR;
                    put(out, x, y, z, s);
                }
            }
        }
        int dx = x0 + w / 2;
        put(out, dx, y0 + 1, z1, m.door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        put(out, dx, y0 + 2, z1, m.door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        put(out, dx + 1, y0 + 1, z1 + 1, Blocks.OAK_FENCE.defaultBlockState());
        put(out, dx + 1, y0 + 2, z1 + 1, Blocks.LANTERN.defaultBlockState());
        put(out, x0 + 1, y0 + 1, z0 + 1, Blocks.LANTERN.defaultBlockState());
        int half = (w + 1) / 2;
        for (int k = 0; k <= half; k++) {
            int y = y0 + wh + 1 + k;
            int lx = x0 - 1 + k, rx = x1 + 1 - k;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (lx < rx) {
                    put(out, lx, y, z, m.stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM));
                    put(out, rx, y, z, m.stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM));
                    if (z == z0 || z == z1) for (int x = lx + 1; x < rx; x++) put(out, x, y, z, m.planks);
                } else if (lx == rx) {
                    put(out, lx, y, z, m.slab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
                }
            }
        }
        int cx = x1 - 1;
        for (int y = y0 + wh + 1; y <= y0 + wh + half + 1; y++) put(out, cx, y, z0 + 1, Blocks.BRICKS.defaultBlockState());
        boolean garden = kind.ordinal() <= Palette.House.POPLAR.ordinal() && kind != Palette.House.SANDSTONE && kind != Palette.House.TERRACOTTA;
        for (int x = x0; garden && x <= x1; x++) {
            if (x == dx || x == dx + 1) continue;
            if (r(seed, 40 + x) < 0.5) put(out, x, groundTop(x, z1 + 2) + 1, z1 + 2,
                    (r(seed, 60 + x) < 0.5 ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState());
        }
    }

    private static void igloo(Sink out, int ax, int az) {
        int cx = ax + 3, cz = az - 2;
        int y0 = footprintBase(cx - 3, cx + 3, cz - 3, cz + 3);
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = 0; dy <= 3; dy++)
                for (int dz = -3; dz <= 3; dz++) {
                    double e = (dx * dx) / 12.25 + (dy * dy) / 12.25 + (dz * dz) / 12.25;
                    if (e > 1) continue;
                    put(out, cx + dx, y0 + dy, cz + dz, e > 0.5 ? snow : AIR);
                }
        put(out, cx, y0, cz + 3, AIR);
        put(out, cx, y0 + 1, cz + 3, AIR);
        put(out, cx, y0, cz, Blocks.LANTERN.defaultBlockState());
        foundation(out, cx - 3, cx + 3, cz - 3, cz + 3, y0, snow);
    }

    private static void windmill(Sink out, int ax, int az) {
        int cx = ax + 3, cz = az - 2;
        int y0 = footprintBase(cx - 2, cx + 2, cz - 2, cz + 2);
        foundation(out, cx - 2, cx + 2, cz - 2, cz + 2, y0, Blocks.COBBLESTONE.defaultBlockState());
        int h = 11;
        for (int y = y0; y < y0 + h; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    BlockState s = !edge ? AIR : (y < y0 + 3 ? Blocks.COBBLESTONE.defaultBlockState()
                            : (Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState()));
                    if (y == y0 + 6 && dz == 1 && dx == 0) s = Blocks.GLASS_PANE.defaultBlockState();
                    put(out, cx + dx, y, cz + dz, s);
                }
        put(out, cx, y0 + 1, cz + 1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        put(out, cx, y0 + 2, cz + 1, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) put(out, cx + dx, y0 + h, cz + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(out, cx + dx, y0 + h + 1, cz + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        put(out, cx, y0 + h + 2, cz, Blocks.DARK_OAK_SLAB.defaultBlockState());
        int hy = y0 + h - 3, hz = cz + 2;
        put(out, cx, hy, hz, Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        BlockState wool = Blocks.WOOL.white().defaultBlockState();
        BlockState beam = Blocks.SPRUCE_FENCE.defaultBlockState();
        int[][] dirs = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : dirs) {
            for (int i = 1; i <= 6; i++) {
                int bx = cx + d[0] * i, by = hy + d[1] * i;
                put(out, bx, by, hz, beam);
                if (i >= 2) put(out, bx - d[1], by + d[0], hz, wool);
            }
        }
    }

    private static void farm(Sink out, int ax, int az, long seed) {
        int x0 = ax, x1 = ax + 10, z0 = az - 3, z1 = az;
        int y = footprintBase(x0, x1, z0, z1) - 1;
        Block crop = r(seed, 1) < 0.5 ? Blocks.WHEAT : Blocks.CARROTS;
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean border = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                foundation(out, x, x, z, z, y, Blocks.DIRT.defaultBlockState());
                if (border) {
                    put(out, x, y, z, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,
                            (z == z0 - 1 || z == z1 + 1) ? Direction.Axis.X : Direction.Axis.Z));
                    put(out, x, y + 1, z, AIR);
                } else {
                    put(out, x, y, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                    put(out, x, y + 1, z, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 5 + (int) (r(seed, x * 7 + z) * 3)));
                }
            }
        put(out, x1 + 3, y + 1, z1, Blocks.HAY_BLOCK.defaultBlockState());
        put(out, x1 + 3, y + 2, z1, Blocks.HAY_BLOCK.defaultBlockState());
        put(out, x1 + 4, y + 1, z1, Blocks.HAY_BLOCK.defaultBlockState());
        put(out, x1 + 3, y + 1, z1 - 1, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
    }

    private static void ruinedPortal(Sink out, int ax, int az) {
        int cx = ax + 2, cz = az - 2;
        int y0 = footprintBase(cx - 2, cx + 5, cz - 1, cz + 1);
        foundation(out, cx - 2, cx + 5, cz - 1, cz + 1, y0, Blocks.NETHERRACK.defaultBlockState());
        for (int x = cx - 2; x <= cx + 5; x++) for (int z = cz - 1; z <= cz + 1; z++) put(out, x, y0 - 1, z, Blocks.NETHERRACK.defaultBlockState());
        for (int i = 0; i < 4; i++) {
            put(out, cx + i, y0, cz, (i == 1 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
            if (i != 2) put(out, cx + i, y0 + 4, cz, Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int y = 1; y < 4; y++) {
            put(out, cx, y0 + y, cz, (y == 2 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
            if (y < 3) put(out, cx + 3, y0 + y, cz, Blocks.OBSIDIAN.defaultBlockState());
        }
        put(out, cx + 4, y0, cz + 1, Blocks.MAGMA_BLOCK.defaultBlockState());
        put(out, cx - 1, y0, cz + 1, Blocks.GOLD_BLOCK.defaultBlockState());
    }

    private static void castle(Sink out, int ax, int az) {
        int base = groundTop(ax + 4, az) + 1;
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        int[][] towers = {{0, 16}, {14, 13}, {7, 20}};
        for (int[] t : towers) {
            int tx = ax + t[0], h = t[1];
            int y0 = Math.min(groundTop(tx, az), groundTop(tx + 4, az)) - 2;
            for (int y = y0; y < base + h; y++)
                for (int dx = 0; dx < 5; dx++)
                    for (int dz = 0; dz < 5; dz++) {
                        boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                        if (!edge && y > base) continue;
                        put(out, tx + dx, y, az + dz, rand(tx + dx, y, 411) < 0.2 ? mossy : wall);
                    }
            for (int dx = 0; dx < 5; dx += 2) {
                put(out, tx + dx, base + h, az + 4, wall);
                put(out, tx + dx, base + h, az, wall);
            }
            put(out, tx + 2, base + h - 4, az + 4, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int x = ax + 4; x <= ax + 14; x++)
            for (int y = Math.min(groundTop(x, az + 2), base) - 2; y < base + 9; y++)
                put(out, x, y, az + 2, rand(x, y, 412) < 0.2 ? mossy : wall);
        for (int x = ax + 4; x <= ax + 14; x += 2) put(out, x, base + 9, az + 2, wall);
    }
}
