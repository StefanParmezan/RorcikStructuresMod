package ru.rorcik.structures.generation.city.plains.cities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadNetworkGenerator;

public class CityGenerator {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;

    public static void generateCity(ServerLevel level, BlockPos origin, long seed) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: === GENERATING CITY ===");
            LOGGER.info("Info: Origin: {}", origin.toShortString());
        }
        RoadNetworkGenerator.generateRoadNetwork(level, origin, seed);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: === CITY GENERATION COMPLETE ===");
        }
    }
}