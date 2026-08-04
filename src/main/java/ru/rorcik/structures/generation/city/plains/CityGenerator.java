package ru.rorcik.structures.generation.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class CityGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateCity(ServerLevel level, BlockPos origin, long seed) {
        LOGGER.info("=== GENERATING CITY ===");
        LOGGER.info("Origin: {}", origin.toShortString());

        RoadGenerator.generateRoadNetwork(level, origin, seed, 500  );

        LOGGER.info("=== CITY GENERATION COMPLETE ===");
    }
}