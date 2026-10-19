package ru.rorcik.structures.generation.city.plains.cities.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.planning.CityZonePlanner;
import ru.rorcik.structures.city.CityType;

public class RoadNetworkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: === RESERVING CITY ZONE ===");
            LOGGER.info("Info: Origin: {}", origin.toShortString());
        }

        boolean reserved = CityZonePlanner.tryReserveCity(level, origin.getX(), origin.getZ(), CityType.PLAINS);
        if (!reserved) {
            LOGGER.error("Error: [ROAD-NETWORK] Failed to reserve city zone at {}", origin.toShortString());
            return;
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: === CITY ZONE RESERVED ===");
            LOGGER.info("Info: City will generate when player approaches within {} blocks",
                    ru.rorcik.structures.generation.GeneratorConfig.BUFFER_DISTANCE);
        }
    }
}