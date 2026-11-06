package rttt;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rttt.world.RoadBiomeSource;
import rttt.world.RoadChunkGenerator;

public class RTTT implements ModInitializer {
    public static final String MOD_ID = "rttt";
    public static final Logger LOG = LoggerFactory.getLogger("rttt");
    public static final ResourceKey<Level> ROAD_LEVEL = ResourceKey.create(Registries.DIMENSION, id("road"));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("road"), RoadChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("road_biomes"), RoadBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath("ttmod", "road"), RoadChunkGenerator.CODEC.xmap(g -> g, g -> g));
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("ttmod", "road_biomes"), RoadBiomeSource.CODEC.xmap(b -> b, b -> b));
        ServerHooks.init();
        LOG.info("RTTT Road Trip on TikTok initialised");
    }
}
