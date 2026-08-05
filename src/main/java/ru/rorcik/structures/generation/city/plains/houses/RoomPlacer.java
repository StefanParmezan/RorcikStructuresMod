package ru.rorcik.structures.generation.city.plains.houses;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.RoomSlot;
import ru.rorcik.structures.generation.StructureUtils;

public class RoomPlacer {
    public static void placeRooms(ServerLevel level, HouseConfig config, BlockPos houseOrigin, Rotation houseRotation, RandomSource random) {
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

                int decor = slot.decorCount() > 0 ? random.nextInt(slot.decorCount()) : 0;
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
}