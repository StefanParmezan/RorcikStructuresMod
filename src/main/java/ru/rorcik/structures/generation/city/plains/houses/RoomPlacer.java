// FILE: ./main/java/ru/rorcik/structures/generation/city/plains/houses/RoomPlacer.java
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
        for (RoomSlot slot : config.rooms()) {
            for (int i = 0; i < slot.repeat(); i++) {
                placeSingleRoom(level, config, slot, houseOrigin, houseRotation, random, i);
            }
        }
    }

    /**
     * Ставит одну конкретную комнату. Используется гранулярным планировщиком.
     */
    public static void placeSingleRoom(
            ServerLevel level,
            HouseConfig config,
            RoomSlot slot,
            BlockPos houseOrigin,
            Rotation houseRotation,
            RandomSource random,
            int floorIndex
    ) {
        int houseSizeX = config.size()[0];
        int houseSizeZ = config.size()[2];

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

        int floor = slot.startFloor() + floorIndex;
        int y = config.firstFloorOffset() + (floor * config.floorHeight());
        int decor = slot.decorCount() > 0 ? random.nextInt(slot.decorCount()) : 0;
        String variant = slot.variant();

        String roomNbt = variant.isEmpty()
                ? slot.nbt() + "_" + decor
                : slot.nbt() + "_" + decor + "_" + variant;

        BlockPos roomOrigin = houseOrigin.offset(
                rotatedRoomPos[0] + rotatedRoomOffset[0],
                y + rotatedRoomOffset[1],
                rotatedRoomPos[2] + rotatedRoomOffset[2]
        );

        StructureUtils.placeStructure(
                level, roomOrigin, ModPaths.roomNbtPath(config.type(), roomNbt),
                roomRotation, slot.mirrorX(), slot.mirrorZ()
        );
    }
}