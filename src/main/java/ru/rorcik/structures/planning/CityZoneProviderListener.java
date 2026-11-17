package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.city.CityZoneProviders;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.List;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityZoneProviderListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile RandomCityZoneProvider currentProvider;

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        if (!RorcikStructuresModConfig.enableCityCords) {
            CitySpawnSettings spawnSettings = new CitySpawnSettings(
                    RorcikStructuresModConfig.randomPlainsSpawnChance,
                    RorcikStructuresModConfig.randomDesertSpawnChance
            );
            RandomCityZoneProvider randomProvider = new RandomCityZoneProvider(
                    level.getSeed(), level.getSeaLevel(), CityCandidateValidator.forLevel(level), spawnSettings
            );
            currentProvider = randomProvider;
            CityZoneProviders.register(randomProvider);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-ZONE] Registered random city zone provider for world {} (plainsChance={}%, desertChance={}%)",
                        level.getSeed(), spawnSettings.plainsChance(), spawnSettings.desertChance());
                logNearestCandidate(level, randomProvider);
            }
        } else {
            currentProvider = null;
            CityZoneProviders.clear();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        currentProvider = null;
        CityZoneProviders.clear();
    }

    public static RandomCityZoneProvider currentProvider() {
        return currentProvider;
    }

    private static void logNearestCandidate(ServerLevel level, RandomCityZoneProvider provider) {
        BlockPos spawn = level.getSharedSpawnPos();
        List<CityCandidate> candidates = provider.candidatesNear(spawn.getX(), spawn.getZ(), 30000);

        candidates.sort(java.util.Comparator.comparingLong(candidate -> {
            long dx = (long) candidate.centerX() - spawn.getX();
            long dz = (long) candidate.centerZ() - spawn.getZ();
            return dx * dx + dz * dz;
        }));

        if (candidates.isEmpty()) {
            LOGGER.info("Info: [CITY-ZONE] No random city candidates within 30000 blocks of spawn");
            return;
        }

        CityCandidate nearestRaw = candidates.get(0);
        LOGGER.info("Info: [CITY-ZONE] Nearest random city candidate to spawn: [{}, {}] type={}",
                nearestRaw.centerX(), nearestRaw.centerZ(), nearestRaw.type().id);

        for (CityCandidate candidate : candidates) {
            if (!provider.isCandidateValid(candidate)) continue;
            LOGGER.info("Info: [CITY-ZONE] Nearest valid random city candidate to spawn: [{}, {}] type={}",
                    candidate.centerX(), candidate.centerZ(), candidate.type().id);
            return;
        }

        LOGGER.info("Info: [CITY-ZONE] No valid random city candidate within 30000 blocks of spawn");
    }
}
