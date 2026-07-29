package ru.rorcik.structures.data;

import java.util.List;

public record HouseConfig(
        String type,
        int[] offset,
        int[] size,
        int floorHeight,
        int firstFloorOffset,
        List<RoomSlot> rooms
) {}