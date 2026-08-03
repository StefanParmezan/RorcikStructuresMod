package ru.rorcik.structures.data.city.plains;

import java.util.List;

public record HouseConfig(
        String type,
        int[] offset,
        int[] size,
        int floorHeight,
        int firstFloorOffset,
        int houseDecorCount,
        List<RoomSlot> rooms
) {}