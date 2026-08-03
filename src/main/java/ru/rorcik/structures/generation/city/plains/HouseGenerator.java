package ru.rorcik.structures.generation.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.HouseConfig;
import ru.rorcik.structures.data.city.plains.RoomSlot;
import ru.rorcik.structures.generation.StructureUtils;

public class HouseGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation) {
        generateHouse(level, origin, houseType, level.getSeed(), houseRotation, -1);
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
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];

        BlockPos houseOrigin = origin.offset(
                config.offset()[0] - 1,
                config.offset()[1] - 1,
                config.offset()[2] - 1
        );
        BlockPos housePlacementOrigin = StructureUtils.getCompensatedOrigin(houseOrigin, houseRotation, houseSizeX, houseSizeZ);

        int houseDecor = (forcedDecor >= 0) ? forcedDecor : random.nextInt(config.houseDecorCount());
        String houseNbt = houseType + "_" + houseDecor;

        StructureUtils.placeStructure(level, housePlacementOrigin, ModPaths.houseNbtPath(houseType, houseNbt), houseRotation, false, false);

        generateRooms(level, config, houseOrigin, houseRotation, random);

        BlockPos maxPos = housePlacementOrigin.offset(houseSizeX - 1, houseSizeY - 1, houseSizeZ - 1);
        updateNeighborsAndLight(level, housePlacementOrigin, maxPos);
    }

    private static void generateRoomsOnly(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];

        BlockPos houseOrigin = origin.offset(
                config.offset()[0] - 1,
                config.offset()[1] - 1,
                config.offset()[2] - 1
        );

        generateRooms(level, config, houseOrigin, houseRotation, random);

        BlockPos maxPos = houseOrigin.offset(houseSizeX - 1, houseSizeY - 1, houseSizeZ - 1);
        updateNeighborsAndLight(level, houseOrigin, maxPos);
    }

    private static void generateRooms(ServerLevel level, HouseConfig config, BlockPos houseOrigin, Rotation houseRotation, RandomSource random) {
        int houseSizeX = config.size()[0];
        int houseSizeZ = config.size()[2];

        for (RoomSlot slot : config.rooms()) {
            int roomRotationDegrees = (slot.rotation() + houseRotation.ordinal() * 90) % 360;
            Rotation roomRotation = Rotation.values()[roomRotationDegrees / 90];

            int[] rotatedRoomPos = StructureUtils.rotatePositionInStructure(
                    slot.position()[0], slot.position()[1], slot.position()[2],
                    houseRotation, houseSizeX, houseSizeZ
            );

            int[] rotatedRoomOffset = StructureUtils.rotateOffset(
                    slot.offset()[0], slot.offset()[1], slot.offset()[2],
                    houseRotation
            );

            for (int i = 0; i < slot.repeat(); i++) {
                int floor = slot.startFloor() + i;
                int y = config.firstFloorOffset() + (floor * config.floorHeight());

                int decor = random.nextInt(slot.decorCount());
                String variant = slot.variant();

                String roomNbt;
                if (variant.isEmpty()) {
                    roomNbt = slot.nbt() + "_" + decor;
                } else {
                    roomNbt = slot.nbt() + "_" + decor + "_" + variant;
                }

                BlockPos roomOrigin = houseOrigin.offset(
                        rotatedRoomPos[0] + rotatedRoomOffset[0],
                        y + rotatedRoomOffset[1],
                        rotatedRoomPos[2] + rotatedRoomOffset[2]
                );

                StructureUtils.placeStructure(level, roomOrigin, ModPaths.roomNbtPath(config.type(), roomNbt), roomRotation, slot.mirrorX(), slot.mirrorZ());
            }
        }
    }

    private static void updateNeighborsAndLight(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                level.blockUpdated(pos, state.getBlock());
                if (state.getLightEmission(level, pos) > 0) {
                    level.getLightEngine().checkBlock(pos);
                }
            }
        }
    }
}