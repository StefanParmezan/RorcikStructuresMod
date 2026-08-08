package ru.rorcik.structures.common;

public final class ModPaths {
    private ModPaths() {}

    public static final String CONFIGS_CITIES_PLAINS_HOUSES = "configs/cities/plains/houses";
    public static final String CONFIGS_CITIES_PLAINS_ROADS = "configs/cities/plains/roads";
    public static final String CONFIGS_CITIES_PLAINS_PARKS = "configs/cities/plains/parks";

    public static final String STRUCTURES_BASE = "structures";
    public static final String STRUCTURES_CITIES_PLAINS = "structures/cities/plains";
    public static final String STRUCTURES_CITIES_PLAINS_ROOMS = "structures/cities/plains/rooms";
    public static final String STRUCTURES_CITIES_PLAINS_ROADS = "structures/cities/plains/roads";

    public static String houseNbtPath(String houseNbt) {
        return RorcikStructuresMod.MODID + ":cities/plains/houses/" + houseNbt;
    }

    public static String roomNbtPath(String houseType, String roomNbt) {
        return RorcikStructuresMod.MODID + ":cities/plains/rooms/" + houseType + "/" + roomNbt;
    }

    public static String roadNbtPath(String roadType, String roadNbt) {
        return RorcikStructuresMod.MODID + ":cities/plains/roads/" + roadType + "/" + roadNbt;
    }

    public static String buildingNbtPath(String category, String buildingName) {
        return RorcikStructuresMod.MODID + ":cities/plains/" + category + "/" + buildingName;
    }

    public static String platformNbtPath(String platformName) {
        return RorcikStructuresMod.MODID + ":cities/plains/platforms/" + platformName;
    }

    public static String parkNbtPath(String parkName) {
        return RorcikStructuresMod.MODID + ":cities/plains/parks/" + parkName;
    }
}