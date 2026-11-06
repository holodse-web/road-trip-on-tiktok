package rttt.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class Palette {
    public enum Tree { OAK, BIRCH, POPLAR, SPRUCE, ACACIA, JUNGLE, CHERRY, DARK_OAK, PALE_OAK, CACTUS, MUSHROOM, GIANT_SPRUCE, ICE_SPIKE, SWAMP_OAK, BAMBOO, PALM, BOULDER, NONE }
    public enum House { OAK, SPRUCE, ACACIA, SANDSTONE, JUNGLE, CHERRY, TERRACOTTA, BIRCH, DARK_OAK, PALE_OAK, POPLAR, MUSHROOM, IGLOO }

    public Block surface = Blocks.GRASS_BLOCK;
    public Block sub = Blocks.DIRT;
    public Block road = Blocks.POLISHED_ANDESITE;
    public Block roadSlab = Blocks.POLISHED_ANDESITE_SLAB;
    public Block mountain = Blocks.STONE;
    public Block mountainTop = Blocks.GRASS_BLOCK;
    public Block peak = Blocks.SNOW_BLOCK;
    public boolean snowy = false;
    public boolean mesa = false;
    public Tree tree = Tree.OAK;
    public Tree tree2 = Tree.NONE;
    public double treeDensity = 0.5;
    public House house = House.OAK;
    public Block[] flowers = {Blocks.SHORT_GRASS};
    public double floraDensity = 0.5;
    public int peakLine = 38;
    public boolean bareMountain = false;

    private static Palette p() { return new Palette(); }

    public static final Palette[] BY_BAND = new Palette[Track.BIOME_ORDER.length];

    static {
        for (int i = 0; i < BY_BAND.length; i++) BY_BAND[i] = build(Track.BIOME_ORDER[i]);
    }

    private static Palette build(String id) {
        Block[] grass = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.BUSH};
        Block[] meadowFlowers = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER,
                Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.WILDFLOWERS};
        Palette a = p();
        switch (id) {
            case "minecraft:plains" -> {
                a.tree = Tree.OAK; a.treeDensity = 0.18; a.floraDensity = 0.6; a.house = House.OAK;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.DANDELION,
                        Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.BUSH, Blocks.WILDFLOWERS};
            }
            case "minecraft:flower_forest" -> {
                a.tree = Tree.OAK; a.tree2 = Tree.BIRCH; a.treeDensity = 0.55; a.floraDensity = 0.85; a.house = House.OAK;
                a.flowers = new Block[]{Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP,
                        Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.LILAC, Blocks.ROSE_BUSH,
                        Blocks.PEONY, Blocks.SHORT_GRASS, Blocks.WILDFLOWERS};
                a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB;
            }
            case "minecraft:birch_forest" -> {
                a.tree = Tree.BIRCH; a.treeDensity = 0.7; a.floraDensity = 0.65; a.house = House.BIRCH;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.WILDFLOWERS, Blocks.BUSH};
            }
            case "minecraft:dappled_forest" -> {
                a.tree = Tree.POPLAR; a.tree2 = Tree.OAK; a.treeDensity = 0.65; a.floraDensity = 0.6; a.house = House.POPLAR;
                a.flowers = new Block[]{Blocks.LEAF_LITTER, Blocks.LEAF_LITTER, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.PUMPKIN,
                        Blocks.SWEET_BERRY_BUSH, Blocks.BUSH};
                a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB;
            }
            case "minecraft:taiga" -> {
                a.tree = Tree.SPRUCE; a.treeDensity = 0.65; a.floraDensity = 0.55; a.house = House.SPRUCE;
                a.flowers = new Block[]{Blocks.FERN, Blocks.LARGE_FERN, Blocks.SWEET_BERRY_BUSH, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.MOSSY_COBBLESTONE};
                a.road = Blocks.STONE_BRICKS; a.roadSlab = Blocks.STONE_BRICK_SLAB; a.peakLine = 30;
            }
            case "minecraft:old_growth_spruce_taiga" -> {
                a.tree = Tree.GIANT_SPRUCE; a.tree2 = Tree.SPRUCE; a.treeDensity = 0.7; a.surface = Blocks.PODZOL; a.floraDensity = 0.6;
                a.flowers = new Block[]{Blocks.FERN, Blocks.LARGE_FERN, Blocks.FERN, Blocks.BROWN_MUSHROOM, Blocks.MOSSY_COBBLESTONE, Blocks.SWEET_BERRY_BUSH};
                a.house = House.SPRUCE; a.road = Blocks.COBBLESTONE; a.roadSlab = Blocks.COBBLESTONE_SLAB; a.peakLine = 28;
            }
            case "minecraft:snowy_taiga" -> {
                a.tree = Tree.SPRUCE; a.treeDensity = 0.55; a.snowy = true; a.surface = Blocks.SNOW_BLOCK; a.mountainTop = Blocks.SNOW_BLOCK;
                a.flowers = new Block[]{Blocks.FERN, Blocks.SWEET_BERRY_BUSH}; a.floraDensity = 0.2; a.house = House.SPRUCE;
                a.road = Blocks.STONE_BRICKS; a.roadSlab = Blocks.STONE_BRICK_SLAB; a.peakLine = 14; a.bareMountain = true;
            }
            case "minecraft:ice_spikes" -> {
                a.tree = Tree.ICE_SPIKE; a.treeDensity = 0.28; a.snowy = true; a.surface = Blocks.SNOW_BLOCK; a.mountainTop = Blocks.SNOW_BLOCK;
                a.mountain = Blocks.PACKED_ICE; a.peak = Blocks.BLUE_ICE; a.flowers = new Block[]{Blocks.SHORT_GRASS}; a.floraDensity = 0.02;
                a.house = House.IGLOO; a.road = Blocks.PACKED_ICE; a.roadSlab = Blocks.STONE_BRICK_SLAB; a.peakLine = 4; a.bareMountain = true;
            }
            case "minecraft:snowy_plains" -> {
                a.tree = Tree.SPRUCE; a.treeDensity = 0.08; a.snowy = true; a.surface = Blocks.SNOW_BLOCK; a.mountainTop = Blocks.SNOW_BLOCK;
                a.mountain = Blocks.PACKED_ICE; a.flowers = new Block[]{Blocks.SHORT_GRASS}; a.floraDensity = 0.05; a.house = House.IGLOO;
                a.road = Blocks.STONE_BRICKS; a.roadSlab = Blocks.STONE_BRICK_SLAB; a.peakLine = 6; a.bareMountain = true;
            }
            case "minecraft:windswept_hills" -> {
                a.tree = Tree.SPRUCE; a.tree2 = Tree.BOULDER; a.treeDensity = 0.3; a.floraDensity = 0.35; a.house = House.SPRUCE;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.FERN};
                a.mountainTop = Blocks.STONE; a.peakLine = 18; a.road = Blocks.COBBLESTONE; a.roadSlab = Blocks.COBBLESTONE_SLAB;
            }
            case "minecraft:sunflower_plains" -> {
                a.tree = Tree.OAK; a.treeDensity = 0.08; a.floraDensity = 0.75; a.house = House.OAK;
                a.flowers = new Block[]{Blocks.SUNFLOWER, Blocks.SUNFLOWER, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.DANDELION,
                        Blocks.SUNFLOWER, Blocks.WILDFLOWERS};
            }
            case "minecraft:savanna" -> {
                a.tree = Tree.ACACIA; a.treeDensity = 0.22; a.floraDensity = 0.6; a.house = House.ACACIA;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS};
                a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB; a.peakLine = 60;
            }
            case "minecraft:desert" -> {
                a.tree = Tree.CACTUS; a.treeDensity = 0.22; a.surface = Blocks.SAND; a.sub = Blocks.SANDSTONE; a.mountain = Blocks.SANDSTONE;
                a.mountainTop = Blocks.SAND; a.peak = Blocks.SMOOTH_SANDSTONE; a.floraDensity = 0.14;
                a.flowers = new Block[]{Blocks.DEAD_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.SHORT_DRY_GRASS};
                a.house = House.SANDSTONE; a.road = Blocks.SMOOTH_SANDSTONE; a.roadSlab = Blocks.SMOOTH_SANDSTONE_SLAB; a.peakLine = 999;
            }
            case "minecraft:badlands" -> {
                a.tree = Tree.CACTUS; a.treeDensity = 0.08; a.surface = Blocks.RED_SAND; a.sub = Blocks.TERRACOTTA; a.mountain = Blocks.TERRACOTTA;
                a.mountainTop = Blocks.RED_SAND; a.mesa = true; a.peak = Blocks.TERRACOTTA; a.flowers = new Block[]{Blocks.DEAD_BUSH, Blocks.SHORT_DRY_GRASS};
                a.floraDensity = 0.08; a.house = House.TERRACOTTA; a.road = Blocks.SMOOTH_RED_SANDSTONE; a.roadSlab = Blocks.SMOOTH_RED_SANDSTONE_SLAB;
                a.peakLine = 999;
            }
            case "minecraft:wooded_badlands" -> {
                a.tree = Tree.OAK; a.tree2 = Tree.CACTUS; a.treeDensity = 0.2; a.surface = Blocks.COARSE_DIRT; a.sub = Blocks.TERRACOTTA;
                a.mountain = Blocks.TERRACOTTA; a.mountainTop = Blocks.GRASS_BLOCK; a.mesa = true; a.peak = Blocks.COARSE_DIRT;
                a.flowers = new Block[]{Blocks.DEAD_BUSH, Blocks.SHORT_GRASS, Blocks.SHORT_DRY_GRASS, Blocks.BUSH}; a.floraDensity = 0.3;
                a.house = House.TERRACOTTA; a.road = Blocks.SMOOTH_RED_SANDSTONE; a.roadSlab = Blocks.SMOOTH_RED_SANDSTONE_SLAB; a.peakLine = 999;
            }
            case "minecraft:beach" -> {
                a.tree = Tree.PALM; a.treeDensity = 0.2; a.surface = Blocks.SAND; a.sub = Blocks.SAND; a.mountain = Blocks.SANDSTONE;
                a.mountainTop = Blocks.SAND; a.peak = Blocks.SMOOTH_SANDSTONE; a.peakLine = 999;
                a.flowers = new Block[]{Blocks.SHORT_DRY_GRASS, Blocks.SHORT_GRASS, Blocks.DEAD_BUSH}; a.floraDensity = 0.1;
                a.house = House.OAK; a.road = Blocks.SMOOTH_SANDSTONE; a.roadSlab = Blocks.SMOOTH_SANDSTONE_SLAB;
            }
            case "minecraft:sparse_jungle" -> {
                a.tree = Tree.JUNGLE; a.tree2 = Tree.OAK; a.treeDensity = 0.3; a.floraDensity = 0.7; a.house = House.JUNGLE;
                a.flowers = new Block[]{Blocks.FERN, Blocks.SHORT_GRASS, Blocks.MELON, Blocks.TALL_GRASS, Blocks.BUSH, Blocks.DANDELION};
                a.road = Blocks.MOSSY_COBBLESTONE; a.roadSlab = Blocks.MOSSY_COBBLESTONE_SLAB; a.peakLine = 999;
            }
            case "minecraft:jungle" -> {
                a.tree = Tree.JUNGLE; a.tree2 = Tree.OAK; a.treeDensity = 0.8; a.floraDensity = 0.75; a.house = House.JUNGLE;
                a.flowers = new Block[]{Blocks.FERN, Blocks.LARGE_FERN, Blocks.SHORT_GRASS, Blocks.MELON, Blocks.TALL_GRASS, Blocks.BUSH, Blocks.FERN};
                a.road = Blocks.MOSSY_COBBLESTONE; a.roadSlab = Blocks.MOSSY_COBBLESTONE_SLAB; a.mountainTop = Blocks.MOSS_BLOCK; a.peakLine = 999;
            }
            case "minecraft:bamboo_jungle" -> {
                a.tree = Tree.BAMBOO; a.tree2 = Tree.JUNGLE; a.treeDensity = 0.85; a.surface = Blocks.PODZOL; a.floraDensity = 0.55;
                a.flowers = new Block[]{Blocks.FERN, Blocks.SHORT_GRASS, Blocks.LARGE_FERN, Blocks.MELON};
                a.house = House.JUNGLE; a.road = Blocks.MOSSY_STONE_BRICKS; a.roadSlab = Blocks.MOSSY_STONE_BRICK_SLAB; a.mountainTop = Blocks.MOSS_BLOCK;
                a.peakLine = 999;
            }
            case "minecraft:swamp" -> {
                a.tree = Tree.SWAMP_OAK; a.treeDensity = 0.45; a.surface = Blocks.GRASS_BLOCK; a.sub = Blocks.MUD; a.floraDensity = 0.6;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.BLUE_ORCHID, Blocks.FIREFLY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.TALL_GRASS,
                        Blocks.FERN, Blocks.MUD};
                a.house = House.DARK_OAK; a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB; a.mountainTop = Blocks.GRASS_BLOCK;
                a.peakLine = 999;
            }
            case "minecraft:cherry_grove" -> {
                a.tree = Tree.CHERRY; a.treeDensity = 0.45; a.floraDensity = 0.75; a.house = House.CHERRY;
                a.flowers = new Block[]{Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.SHORT_GRASS, Blocks.PINK_TULIP, Blocks.PEONY, Blocks.PINK_PETALS};
                a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB; a.peakLine = 26;
            }
            case "minecraft:meadow" -> {
                a.tree = Tree.OAK; a.tree2 = Tree.BIRCH; a.treeDensity = 0.07; a.flowers = meadowFlowers; a.floraDensity = 0.85;
                a.house = House.OAK; a.peakLine = 24;
            }
            case "minecraft:dark_forest" -> {
                a.tree = Tree.DARK_OAK; a.tree2 = Tree.MUSHROOM; a.treeDensity = 0.75; a.floraDensity = 0.45; a.house = House.DARK_OAK;
                a.flowers = new Block[]{Blocks.SHORT_GRASS, Blocks.FERN, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.ROSE_BUSH, Blocks.LEAF_LITTER};
                a.road = Blocks.COBBLESTONE; a.roadSlab = Blocks.COBBLESTONE_SLAB;
            }
            case "minecraft:mushroom_fields" -> {
                a.tree = Tree.MUSHROOM; a.treeDensity = 0.3; a.surface = Blocks.MYCELIUM; a.mountainTop = Blocks.MYCELIUM;
                a.flowers = new Block[]{Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM}; a.floraDensity = 0.25; a.house = House.MUSHROOM;
                a.road = Blocks.MUD_BRICKS; a.roadSlab = Blocks.MUD_BRICK_SLAB; a.peakLine = 999;
            }
            case "minecraft:pale_garden" -> {
                a.tree = Tree.PALE_OAK; a.treeDensity = 0.6; a.surface = Blocks.PALE_MOSS_BLOCK; a.mountainTop = Blocks.PALE_MOSS_BLOCK;
                a.flowers = new Block[]{Blocks.PALE_MOSS_CARPET, Blocks.PALE_MOSS_CARPET, Blocks.CLOSED_EYEBLOSSOM, Blocks.OPEN_EYEBLOSSOM, Blocks.SHORT_GRASS};
                a.floraDensity = 0.5; a.house = House.PALE_OAK; a.road = Blocks.POLISHED_DEEPSLATE; a.roadSlab = Blocks.POLISHED_DEEPSLATE_SLAB;
            }
            default -> {
                a.tree = Tree.OAK; a.tree2 = Tree.BIRCH; a.treeDensity = 0.6; a.flowers = grass; a.floraDensity = 0.55; a.house = House.OAK;
                a.road = Blocks.COBBLESTONE; a.roadSlab = Blocks.COBBLESTONE_SLAB;
            }
        }
        return a;
    }

    public static Palette at(int x, int z) {
        return BY_BAND[Track.decorBiome(x, z)];
    }
}
