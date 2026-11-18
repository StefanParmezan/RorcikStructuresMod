package ru.rorcik.structures.planning;

import ru.rorcik.structures.city.CityType;

/**
 * Per-type chance that a deterministic candidate site actually becomes a city.
 * The site lattice already guarantees the minimum center distance, so 100%
 * means the maximum possible city density for that distance.
 */
public record CitySpawnSettings(int plainsChance, int desertChance) {

    public static final CitySpawnSettings DEFAULT = new CitySpawnSettings(100, 100);

    public CitySpawnSettings {
        plainsChance = clamp(plainsChance);
        desertChance = clamp(desertChance);
    }

    public int chance(CityType type) {
        return type == CityType.PLAINS ? plainsChance : desertChance;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
