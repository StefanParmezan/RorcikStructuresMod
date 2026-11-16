package ru.rorcik.structures.planning;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

@FunctionalInterface
public interface CityCandidateValidator {

    boolean isValid(CityCandidate candidate);

    static CityCandidateValidator forLevel(ServerLevel level) {
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        int seaLevel = level.getSeaLevel();

        return candidate -> {
            Holder<Biome> biome = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(candidate.centerX()),
                    QuartPos.fromBlock(seaLevel),
                    QuartPos.fromBlock(candidate.centerZ()),
                    level.getChunkSource().randomState().sampler()
            );

            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return false;

            ResourceLocation biomeId = biome.unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (biomeId == null) return false;
            return CityZonePlanner.isAllowedByRwg(biomeId);
        };
    }
}
