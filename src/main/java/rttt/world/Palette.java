package rttt.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class Palette {
    public enum Tree { OAK, BIRCH, POPLAR, SPRUCE, ACACIA, JUNGLE, CHERRY, DARK_OAK, PALE_OAK, CACTUS, MUSHROOM, GIANT_SPRUCE, ICE_SPIKE, SWAMP_OAK, BAMBOO, PALM, BOULDER,
        MANGROVE, AZALEA, CRIMSON, WARPED, CHORUS, OBSIDIAN_PILLAR, FOSSIL, BASALT_PILLAR, SCULK_PILLAR, DEAD, NONE }
    public enum House { OAK, SPRUCE, ACACIA, SANDSTONE, JUNGLE, CHERRY, TERRACOTTA, BIRCH, DARK_OAK, PALE_OAK, POPLAR, MUSHROOM, IGLOO, MANGROVE, DEEPSLATE, CRIMSON, WARPED, BLACKSTONE, PURPUR }
    /** Set pieces that appear beside the road, a few per biome. */
    public enum Landmark { WELL, CAMP, LIGHTHOUSE, SNOWMEN, PYRAMID, TEMPLE, WITCH_HUT, TORII, STONEHENGE, GRAVES, BEES, PUMPKINS, TRESTLE, END_TOWER, BASTION, SCULK_GATE, HAYSTACKS, TOTEMS, ICE_ARCH }

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
    /** Chance of a pond per 64 blocks of road, what fills it and what lies on its bed. */
    public double pond = 0;
    public Block pondFluid = Blocks.WATER;
    public Block pondBed = Blocks.GRAVEL;
    public boolean lilies = false, reeds = false;
    public Landmark[] landmarks = {};
    /** Small things dropped along the verge. */
    public Block[] props = {};
    public double propDensity = 0.05;

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
            case "minecraft:mangrove_swamp" -> {
                a.tree = Tree.MANGROVE; a.treeDensity = 0.6; a.surface = Blocks.MUD; a.sub = Blocks.MUD; a.mountainTop = Blocks.MOSS_BLOCK;
                a.flowers = new Block[]{Blocks.MOSS_CARPET, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.MANGROVE_PROPAGULE, Blocks.MOSS_CARPET, Blocks.FIREFLY_BUSH};
                a.floraDensity = 0.45; a.house = House.MANGROVE; a.road = Blocks.PACKED_MUD; a.roadSlab = Blocks.MUD_BRICK_SLAB; a.peakLine = 999;
            }
            case "minecraft:lush_caves" -> {
                a.tree = Tree.AZALEA; a.tree2 = Tree.BOULDER; a.treeDensity = 0.5; a.surface = Blocks.MOSS_BLOCK; a.sub = Blocks.CLAY; a.mountainTop = Blocks.MOSS_BLOCK;
                a.flowers = new Block[]{Blocks.MOSS_CARPET, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.SHORT_GRASS, Blocks.MOSS_CARPET, Blocks.BIG_DRIPLEAF,
                        Blocks.SMALL_DRIPLEAF, Blocks.TALL_GRASS};
                a.floraDensity = 0.8; a.house = House.JUNGLE; a.road = Blocks.MOSSY_STONE_BRICKS; a.roadSlab = Blocks.MOSSY_STONE_BRICK_SLAB; a.peakLine = 999;
            }
            case "minecraft:frozen_peaks" -> {
                a.tree = Tree.BOULDER; a.tree2 = Tree.ICE_SPIKE; a.treeDensity = 0.1; a.snowy = true; a.surface = Blocks.SNOW_BLOCK; a.mountainTop = Blocks.SNOW_BLOCK;
                a.mountain = Blocks.STONE; a.peak = Blocks.PACKED_ICE; a.flowers = new Block[]{Blocks.SHORT_GRASS}; a.floraDensity = 0.02; a.house = House.IGLOO;
                a.road = Blocks.POLISHED_DIORITE; a.roadSlab = Blocks.POLISHED_DIORITE_SLAB; a.peakLine = 2; a.bareMountain = true;
            }
            case "minecraft:deep_dark" -> {
                a.tree = Tree.SCULK_PILLAR; a.treeDensity = 0.22; a.surface = Blocks.SCULK; a.sub = Blocks.DEEPSLATE; a.mountain = Blocks.DEEPSLATE;
                a.mountainTop = Blocks.SCULK; a.peak = Blocks.DEEPSLATE; a.flowers = new Block[]{Blocks.SCULK_SENSOR}; a.floraDensity = 0.012;
                a.house = House.DEEPSLATE; a.road = Blocks.DEEPSLATE_TILES; a.roadSlab = Blocks.DEEPSLATE_TILE_SLAB; a.peakLine = 30; a.bareMountain = true;
            }
            case "minecraft:crimson_forest" -> {
                a.tree = Tree.CRIMSON; a.treeDensity = 0.6; a.surface = Blocks.CRIMSON_NYLIUM; a.sub = Blocks.NETHERRACK; a.mountain = Blocks.NETHERRACK;
                a.mountainTop = Blocks.CRIMSON_NYLIUM; a.peak = Blocks.NETHER_WART_BLOCK; a.peakLine = 34;
                a.flowers = new Block[]{Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_FUNGUS}; a.floraDensity = 0.5; a.house = House.CRIMSON;
                a.road = Blocks.RED_NETHER_BRICKS; a.roadSlab = Blocks.RED_NETHER_BRICK_SLAB;
            }
            case "minecraft:warped_forest" -> {
                a.tree = Tree.WARPED; a.treeDensity = 0.6; a.surface = Blocks.WARPED_NYLIUM; a.sub = Blocks.NETHERRACK; a.mountain = Blocks.NETHERRACK;
                a.mountainTop = Blocks.WARPED_NYLIUM; a.peak = Blocks.WARPED_WART_BLOCK; a.peakLine = 34;
                a.flowers = new Block[]{Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS, Blocks.WARPED_ROOTS, Blocks.WARPED_FUNGUS, Blocks.NETHER_SPROUTS};
                a.floraDensity = 0.55; a.house = House.WARPED; a.road = Blocks.POLISHED_BLACKSTONE_BRICKS; a.roadSlab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
            }
            case "minecraft:soul_sand_valley" -> {
                a.tree = Tree.FOSSIL; a.tree2 = Tree.BASALT_PILLAR; a.treeDensity = 0.16; a.surface = Blocks.SOUL_SAND; a.sub = Blocks.SOUL_SOIL;
                a.mountain = Blocks.BLACKSTONE; a.mountainTop = Blocks.SOUL_SOIL; a.peak = Blocks.BASALT; a.peakLine = 26; a.bareMountain = true;
                a.flowers = new Block[]{Blocks.SOUL_FIRE}; a.floraDensity = 0.02; a.house = House.BLACKSTONE;
                a.road = Blocks.POLISHED_BLACKSTONE; a.roadSlab = Blocks.POLISHED_BLACKSTONE_SLAB;
            }
            case "minecraft:end_highlands" -> {
                a.tree = Tree.CHORUS; a.tree2 = Tree.OBSIDIAN_PILLAR; a.treeDensity = 0.3; a.surface = Blocks.END_STONE; a.sub = Blocks.END_STONE;
                a.mountain = Blocks.END_STONE; a.mountainTop = Blocks.END_STONE; a.peak = Blocks.END_STONE_BRICKS; a.peakLine = 40; a.bareMountain = true;
                a.flowers = new Block[]{Blocks.SHORT_GRASS}; a.floraDensity = 0; a.house = House.PURPUR;
                a.road = Blocks.PURPUR_BLOCK; a.roadSlab = Blocks.PURPUR_SLAB;
            }
            default -> {
                a.tree = Tree.OAK; a.tree2 = Tree.BIRCH; a.treeDensity = 0.6; a.flowers = grass; a.floraDensity = 0.55; a.house = House.OAK;
                a.road = Blocks.COBBLESTONE; a.roadSlab = Blocks.COBBLESTONE_SLAB;
            }
        }
        extras(a, id);
        return a;
    }

    /** Ponds, landmarks and roadside clutter: the details that make each stretch recognisable. */
    private static void extras(Palette a, String id) {
        switch (id) {
            case "minecraft:plains" -> { a.pond = 0.3; a.reeds = true; a.landmarks = new Landmark[]{Landmark.WELL, Landmark.HAYSTACKS, Landmark.STONEHENGE};
                a.props = new Block[]{Blocks.HAY_BLOCK, Blocks.COMPOSTER, Blocks.BARREL, Blocks.OAK_FENCE}; }
            case "minecraft:sunflower_plains" -> { a.pond = 0.2; a.reeds = true; a.landmarks = new Landmark[]{Landmark.BEES, Landmark.HAYSTACKS};
                a.props = new Block[]{Blocks.HAY_BLOCK, Blocks.BEEHIVE, Blocks.OAK_FENCE}; }
            case "minecraft:flower_forest" -> { a.pond = 0.35; a.lilies = true; a.landmarks = new Landmark[]{Landmark.BEES, Landmark.WELL};
                a.props = new Block[]{Blocks.BEEHIVE, Blocks.FLOWERING_AZALEA, Blocks.OAK_FENCE}; }
            case "minecraft:birch_forest" -> { a.pond = 0.2; a.landmarks = new Landmark[]{Landmark.CAMP}; a.props = new Block[]{Blocks.BIRCH_FENCE, Blocks.BARREL}; }
            case "minecraft:forest" -> { a.pond = 0.25; a.lilies = true; a.landmarks = new Landmark[]{Landmark.CAMP, Landmark.WELL};
                a.props = new Block[]{Blocks.OAK_FENCE, Blocks.BARREL, Blocks.MOSSY_COBBLESTONE}; }
            case "minecraft:dappled_forest" -> { a.landmarks = new Landmark[]{Landmark.PUMPKINS, Landmark.CAMP};
                a.props = new Block[]{Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.JACK_O_LANTERN}; a.propDensity = 0.07; }
            case "minecraft:meadow" -> { a.pond = 0.3; a.reeds = true; a.landmarks = new Landmark[]{Landmark.BEES, Landmark.STONEHENGE};
                a.props = new Block[]{Blocks.BEEHIVE, Blocks.HAY_BLOCK}; }
            case "minecraft:cherry_grove" -> { a.pond = 0.5; a.lilies = true; a.pondBed = Blocks.SAND; a.landmarks = new Landmark[]{Landmark.TORII};
                a.props = new Block[]{Blocks.CHERRY_FENCE, Blocks.LANTERN}; }
            case "minecraft:dark_forest" -> { a.landmarks = new Landmark[]{Landmark.GRAVES, Landmark.CAMP};
                a.props = new Block[]{Blocks.MOSSY_COBBLESTONE, Blocks.COBWEB, Blocks.JACK_O_LANTERN}; }
            case "minecraft:pale_garden" -> { a.landmarks = new Landmark[]{Landmark.GRAVES}; a.props = new Block[]{Blocks.CREAKING_HEART, Blocks.COBWEB}; a.propDensity = 0.03; }
            case "minecraft:swamp" -> { a.pond = 0.95; a.lilies = true; a.reeds = true; a.pondBed = Blocks.MUD; a.landmarks = new Landmark[]{Landmark.WITCH_HUT};
                a.props = new Block[]{Blocks.MUD, Blocks.MOSSY_COBBLESTONE}; }
            case "minecraft:mangrove_swamp" -> { a.pond = 0.95; a.lilies = true; a.pondBed = Blocks.MUD; a.landmarks = new Landmark[]{Landmark.WITCH_HUT};
                a.props = new Block[]{Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS}; a.propDensity = 0.08; }
            case "minecraft:sparse_jungle" -> { a.pond = 0.3; a.reeds = true; a.landmarks = new Landmark[]{Landmark.TEMPLE, Landmark.CAMP};
                a.props = new Block[]{Blocks.MELON, Blocks.MOSSY_COBBLESTONE, Blocks.JUNGLE_FENCE}; }
            case "minecraft:jungle", "minecraft:bamboo_jungle" -> { a.pond = 0.4; a.lilies = true; a.landmarks = new Landmark[]{Landmark.TEMPLE};
                a.props = new Block[]{Blocks.MELON, Blocks.MOSSY_COBBLESTONE, Blocks.MOSS_BLOCK}; }
            case "minecraft:lush_caves" -> { a.pond = 0.8; a.lilies = true; a.pondBed = Blocks.CLAY; a.props = new Block[]{Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.VERDANT_FROGLIGHT}; }
            case "minecraft:beach" -> { a.landmarks = new Landmark[]{Landmark.LIGHTHOUSE, Landmark.CAMP}; a.pond = 0.5; a.pondBed = Blocks.SAND;
                a.props = new Block[]{Blocks.BARREL, Blocks.SANDSTONE_WALL, Blocks.DEAD_BRAIN_CORAL_BLOCK}; }
            case "minecraft:savanna" -> { a.pond = 0.15; a.landmarks = new Landmark[]{Landmark.TOTEMS, Landmark.WELL};
                a.props = new Block[]{Blocks.HAY_BLOCK, Blocks.ACACIA_FENCE, Blocks.COARSE_DIRT}; a.tree2 = Tree.DEAD; }
            case "minecraft:desert" -> { a.pond = 0.25; a.pondBed = Blocks.SAND; a.reeds = true; a.landmarks = new Landmark[]{Landmark.PYRAMID, Landmark.WELL};
                a.props = new Block[]{Blocks.BONE_BLOCK, Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE_WALL}; a.propDensity = 0.03; a.tree2 = Tree.DEAD; }
            case "minecraft:badlands" -> { a.landmarks = new Landmark[]{Landmark.TRESTLE}; a.props = new Block[]{Blocks.BARREL, Blocks.BONE_BLOCK, Blocks.RAW_GOLD_BLOCK};
                a.propDensity = 0.03; a.tree2 = Tree.DEAD; }
            case "minecraft:wooded_badlands" -> { a.landmarks = new Landmark[]{Landmark.TRESTLE, Landmark.CAMP}; a.props = new Block[]{Blocks.BARREL, Blocks.COARSE_DIRT}; }
            case "minecraft:windswept_hills" -> { a.pond = 0.2; a.landmarks = new Landmark[]{Landmark.STONEHENGE, Landmark.CAMP};
                a.props = new Block[]{Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE}; a.propDensity = 0.08; }
            case "minecraft:taiga", "minecraft:old_growth_spruce_taiga" -> { a.pond = 0.25; a.landmarks = new Landmark[]{Landmark.CAMP};
                a.props = new Block[]{Blocks.SPRUCE_FENCE, Blocks.BARREL, Blocks.MOSSY_COBBLESTONE, Blocks.CAMPFIRE}; }
            case "minecraft:snowy_taiga" -> { a.pond = 0.3; a.pondFluid = Blocks.ICE; a.landmarks = new Landmark[]{Landmark.SNOWMEN, Landmark.CAMP};
                a.props = new Block[]{Blocks.SPRUCE_FENCE, Blocks.SNOW_BLOCK}; }
            case "minecraft:snowy_plains" -> { a.pond = 0.5; a.pondFluid = Blocks.ICE; a.landmarks = new Landmark[]{Landmark.SNOWMEN}; a.props = new Block[]{Blocks.SNOW_BLOCK, Blocks.PACKED_ICE}; }
            case "minecraft:ice_spikes" -> { a.pond = 0.5; a.pondFluid = Blocks.ICE; a.landmarks = new Landmark[]{Landmark.ICE_ARCH}; a.props = new Block[]{Blocks.PACKED_ICE, Blocks.BLUE_ICE}; }
            case "minecraft:frozen_peaks" -> { a.pond = 0.7; a.pondFluid = Blocks.ICE; a.landmarks = new Landmark[]{Landmark.ICE_ARCH, Landmark.SNOWMEN};
                a.props = new Block[]{Blocks.PACKED_ICE, Blocks.COBBLESTONE_WALL}; }
            case "minecraft:mushroom_fields" -> { a.pond = 0.3; a.props = new Block[]{Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM}; }
            case "minecraft:deep_dark" -> { a.landmarks = new Landmark[]{Landmark.SCULK_GATE}; a.props = new Block[]{Blocks.SCULK_CATALYST, Blocks.SOUL_LANTERN, Blocks.DEEPSLATE_BRICK_WALL};
                a.propDensity = 0.04; }
            case "minecraft:crimson_forest" -> { a.pond = 0.45; a.pondFluid = Blocks.LAVA; a.pondBed = Blocks.NETHERRACK; a.landmarks = new Landmark[]{Landmark.BASTION};
                a.props = new Block[]{Blocks.SHROOMLIGHT, Blocks.NETHER_WART_BLOCK, Blocks.NETHER_GOLD_ORE}; }
            case "minecraft:warped_forest" -> { a.landmarks = new Landmark[]{Landmark.BASTION}; a.props = new Block[]{Blocks.SHROOMLIGHT, Blocks.WARPED_WART_BLOCK, Blocks.CRYING_OBSIDIAN}; }
            case "minecraft:soul_sand_valley" -> { a.pond = 0.35; a.pondFluid = Blocks.LAVA; a.pondBed = Blocks.BLACKSTONE; a.landmarks = new Landmark[]{Landmark.BASTION};
                a.props = new Block[]{Blocks.BONE_BLOCK, Blocks.SOUL_LANTERN, Blocks.BASALT}; }
            case "minecraft:end_highlands" -> { a.landmarks = new Landmark[]{Landmark.END_TOWER}; a.props = new Block[]{Blocks.END_ROD, Blocks.PURPUR_PILLAR, Blocks.OBSIDIAN}; a.propDensity = 0.03; }
            default -> {
            }
        }
    }

    public static Palette at(int x, int z) {
        return BY_BAND[Track.decorBiome(x, z)];
    }
}
