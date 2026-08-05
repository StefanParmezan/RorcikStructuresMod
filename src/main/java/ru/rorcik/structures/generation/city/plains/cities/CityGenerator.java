package ru.rorcik.structures.generation.city.plains.cities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadNetworkGenerator;

public class CityGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateCity(ServerLevel level, BlockPos origin, long seed) {
        LOGGER.info("=== GENERATING CITY ===");
        LOGGER.info("Origin: {}", origin.toShortString());

        RoadNetworkGenerator.generateRoadNetwork(level, origin, seed, 500);

        LOGGER.info("=== CITY GENERATION COMPLETE ===");
    }
}