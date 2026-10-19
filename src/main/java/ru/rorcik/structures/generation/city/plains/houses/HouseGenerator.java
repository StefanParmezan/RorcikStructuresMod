package ru.rorcik.structures.generation.city.plains.houses;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IStructureGenerator;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.config_loaders.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

public class HouseGenerator implements IStructureGenerator {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;

    @Override
    public void generate(ServerLevel level, BlockPos origin, String type, Rotation rotation) {
        generateHouse(level, origin, type, level.getSeed(), rotation, -1);
    }

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation) {
        new HouseGenerator().generate(level, origin, houseType, houseRotation);
    }

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation, int forcedDecor) {
        generateHouse(level, origin, houseType, level.getSeed(), houseRotation, forcedDecor);
    }

    public static void generateRoomsOnly(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation) {
        generateRoomsOnly(level, origin, houseType, level.getSeed(), houseRotation);
    }

    private static void generateHouse(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation, int forcedDecor) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Error: Config not found: {}", houseType);
            return;
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];

        BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(origin, config);
        BlockPos housePlacementOrigin = HousePositionCalculator.calculatePlacementOrigin(houseOrigin, houseRotation, houseSizeX, houseSizeZ);

        int houseDecor = (forcedDecor >= 0) ? forcedDecor : (config.houseDecorCount() > 0 ? random.nextInt(config.houseDecorCount()) : 0);
        String houseNbt = houseType + "_" + houseDecor;

        StructureUtils.placeStructure(level, housePlacementOrigin, ModPaths.houseNbtPath(houseNbt), houseRotation, false, false);
        RoomPlacer.placeRooms(level, config, houseOrigin, houseRotation, random);
    }

    private static void generateRoomsOnly(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Error: Config not found: {}", houseType);
            return;
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(origin, config);
        RoomPlacer.placeRooms(level, config, houseOrigin, houseRotation, random);
    }
}