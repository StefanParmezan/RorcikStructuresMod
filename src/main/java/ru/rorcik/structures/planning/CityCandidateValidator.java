package ru.rorcik.structures.planning;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.structures.city.CityType;

@FunctionalInterface
public interface CityCandidateValidator {

    CityType resolveType(CityCandidate candidate);

    default boolean isValid(CityCandidate candidate) {
        return resolveType(candidate) != null;
    }

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

            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return null;

            ResourceLocation biomeId = biome.unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (biomeId == null) return null;

            TemperatureZone zone = CityZonePlanner.getBiomeTemperatureZone(biomeId, biome.value());
            if (zone == null || !candidate.type().allowedZones.contains(zone)) return null;

            boolean hot = zone == TemperatureZone.HOT || zone == TemperatureZone.EXTREME_HOT;
            if (!hot && !CityZonePlanner.isAllowedByRwg(biomeId)) return null;

            return candidate.type();
        };
    }
}
