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
            RandomCityZoneProvider randomProvider = new RandomCityZoneProvider(
                    level.getSeed(), level.getSeaLevel(), CityCandidateValidator.forLevel(level)
            );
            currentProvider = randomProvider;
            CityZoneProviders.register(randomProvider);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-ZONE] Registered random city zone provider for world {}", level.getSeed());
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
        List<CityCandidate> candidates = provider.candidatesNear(spawn.getX(), spawn.getZ(), 12000);

        CityCandidate nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (CityCandidate candidate : candidates) {
            if (!provider.isCandidateValid(candidate)) continue;
            long dx = (long) candidate.centerX() - spawn.getX();
            long dz = (long) candidate.centerZ() - spawn.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }

        if (nearest != null) {
            LOGGER.info("Info: [CITY-ZONE] Nearest random city candidate to spawn: [{}, {}] type={}",
                    nearest.centerX(), nearest.centerZ(), nearest.type().id);
        }
    }
}
