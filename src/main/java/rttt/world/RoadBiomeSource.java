package rttt.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public class RoadBiomeSource extends BiomeSource {
    public static final MapCodec<RoadBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, RoadBiomeSource::new));

    private final HolderSet<Biome> biomes;

    public RoadBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    public Holder<Biome> biomeAtBlockX(int x) {
        int n = biomes.size();
        int idx = Track.bandIndex(x);
        return biomes.get(Math.min(idx, n - 1) % n);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> biomeAtBlockX(qx * 4 + 2);
    }
}
