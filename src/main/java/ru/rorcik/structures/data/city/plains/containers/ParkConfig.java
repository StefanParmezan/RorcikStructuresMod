package ru.rorcik.structures.data.city.plains.containers;

public record ParkConfig(
        String name,
        int[] size,
        int yOffset,
        int worldCount
) {
    public boolean isLargePark() {
        return size[0] >= 128 && size[2] >= 128;
    }

    public boolean hasWorldLimit() {
        return worldCount >= 0;
    }

    public boolean canSpawn(int currentCount) {
        if (worldCount < 0) return true;
        return currentCount < worldCount;
    }
}