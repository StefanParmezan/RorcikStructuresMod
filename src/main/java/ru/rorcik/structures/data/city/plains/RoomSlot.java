package ru.rorcik.structures.data.city.plains;

public record RoomSlot(
        String nbt,
        int[] position,
        int[] offset,
        int[] size,
        int rotation,
        int startFloor,
        int repeat,
        String variant,
        boolean mirrorX,
        boolean mirrorZ,
        int decorCount
) {}