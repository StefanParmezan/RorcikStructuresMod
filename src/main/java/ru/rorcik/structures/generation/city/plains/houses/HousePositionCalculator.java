package ru.rorcik.structures.generation.city.plains.houses;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.generation.StructureUtils;

public class HousePositionCalculator {
    public static BlockPos calculateHouseOrigin(BlockPos origin, HouseConfig config) {
        return origin.offset(
                config.offset()[0] - 1,
                config.offset()[1] - 1,
                config.offset()[2] - 1
        );
    }

    public static BlockPos calculatePlacementOrigin(BlockPos houseOrigin, Rotation rotation, int sizeX, int sizeZ) {
        return StructureUtils.getCompensatedOrigin(houseOrigin, rotation, sizeX, sizeZ);
    }
}