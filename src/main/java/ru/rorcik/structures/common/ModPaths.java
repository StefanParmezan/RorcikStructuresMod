package ru.rorcik.structures.common;

public final class ModPaths {
    private ModPaths() {}

    public static final String CONFIGS_CITIES_PLAINS_HOUSES = "configs/cities/plains/houses";

    public static final String STRUCTURES_BASE = "structures";
    public static final String STRUCTURES_CITIES_PLAINS_HOUSES = "structures/cities/plains/houses";
    public static final String STRUCTURES_CITIES_PLAINS_ROOMS = "structures/cities/plains/rooms";

    public static String houseNbtPath(String houseType, String houseNbt) {
        return RorcikStructuresMod.MODID + ":cities/plains/houses/" + houseType + "/" + houseNbt;
    }

    public static String roomNbtPath(String houseType, String roomNbt) {
        return RorcikStructuresMod.MODID + ":cities/plains/rooms/" + houseType + "/" + roomNbt;
    }
}