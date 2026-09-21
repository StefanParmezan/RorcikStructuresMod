package ru.rorcik.structures.generation;

import ru.rorcik.structures.common.RorcikStructuresModConfig;

/**
 * Runtime configuration values read from the Forge config file.
 * All fields are non-final so they can be updated when the config is reloaded.
 */
public final class GeneratorConfig {
    private GeneratorConfig() {}

    // ==================== PLAINS CITY ====================
    public static int ROAD_STEP = 64;
    public static int BUILDING_DISTANCE = 64;
    public static int STRAIGHT_BETWEEN_NODES = 2;
    public static int MAX_GRAPH_DEPTH = 5;
    public static int MAX_NODES = 70;
    public static int BUILDING_MIN_DEPTH = 2;
    public static int BUILDING_MAX_DEPTH = 2;
    public static int HOUSES_CHANCE = 90;
    public static int OTHER_CHANCE = 5;
    public static int LOOTABLE_CHANCE = 3;
    public static boolean SPAWN_PARKS = true;
    public static boolean PARK_ONLY_RECT = false;
    public static int CHUNK_LOAD_RADIUS = 4;

    // ==================== DESERT CITY ====================
    public static int DESERT_BUILDING_COUNT = 150;
    public static int DESERT_STEP_MIN = 32;
    public static int DESERT_STEP_MAX = 32;
    public static int DESERT_MIN_DIRECTIONS = 1;
    public static int DESERT_MAX_DIRECTIONS = 4;
    public static int DESERT_Y_OFFSET = -1;

    // ==================== PERFORMANCE ====================
    public static int INITIAL_STRUCTURES_PER_TICK = 16;
    public static long TARGET_TICK_TIME_MS = 30;
    public static int MIN_STRUCTURES_PER_TICK = 2;
    public static int MAX_STRUCTURES_PER_TICK = 64;
    public static int CHUNKS_PER_TICK = 48;
    public static int PRELOAD_PER_TICK_DURING_CHUNKS = 6;
    public static int PRELOAD_PER_TICK_BEFORE_BUILD = 15;

    // ==================== MEMORY ====================
    public static int BUILDING_MEMORY_BUDGET_PERCENT = 70;
    public static int ROOM_MEMORY_BUDGET_PERCENT = 30;
    public static long RESERVED_RAM_MB = 2048;

    /**
     * Reload all values from the Forge config.
     * Called on config load/reload events.
     */
    public static void reload() {
        // Plains
        ROAD_STEP = RorcikStructuresModConfig.plainsRoadStep;
        BUILDING_DISTANCE = RorcikStructuresModConfig.plainsBuildingDistance;
        STRAIGHT_BETWEEN_NODES = RorcikStructuresModConfig.plainsStraightBetweenNodes;
        MAX_GRAPH_DEPTH = RorcikStructuresModConfig.plainsMaxGraphDepth;
        MAX_NODES = RorcikStructuresModConfig.plainsMaxNodes;
        BUILDING_MIN_DEPTH = RorcikStructuresModConfig.plainsBuildingMinDepth;
        BUILDING_MAX_DEPTH = RorcikStructuresModConfig.plainsBuildingMaxDepth;
        HOUSES_CHANCE = RorcikStructuresModConfig.plainsHousesChance;
        OTHER_CHANCE = RorcikStructuresModConfig.plainsOtherChance;
        LOOTABLE_CHANCE = RorcikStructuresModConfig.plainsLootableChance;
        SPAWN_PARKS = RorcikStructuresModConfig.plainsSpawnParks;
        PARK_ONLY_RECT = RorcikStructuresModConfig.plainsParkOnlyRect;
        CHUNK_LOAD_RADIUS = RorcikStructuresModConfig.plainsChunkLoadRadius;

        // Desert
        DESERT_BUILDING_COUNT = RorcikStructuresModConfig.desertBuildingCount;
        DESERT_STEP_MIN = RorcikStructuresModConfig.desertStepMin;
        DESERT_STEP_MAX = RorcikStructuresModConfig.desertStepMax;
        DESERT_MIN_DIRECTIONS = RorcikStructuresModConfig.desertMinDirections;
        DESERT_MAX_DIRECTIONS = RorcikStructuresModConfig.desertMaxDirections;
        DESERT_Y_OFFSET = RorcikStructuresModConfig.desertYOffset;

        // Performance
        INITIAL_STRUCTURES_PER_TICK = RorcikStructuresModConfig.initialStructuresPerTick;
        TARGET_TICK_TIME_MS = RorcikStructuresModConfig.targetTickTimeMs;
        MIN_STRUCTURES_PER_TICK = RorcikStructuresModConfig.minStructuresPerTick;
        MAX_STRUCTURES_PER_TICK = RorcikStructuresModConfig.maxStructuresPerTick;
        CHUNKS_PER_TICK = RorcikStructuresModConfig.chunksPerTick;
        PRELOAD_PER_TICK_DURING_CHUNKS = RorcikStructuresModConfig.preloadPerTickDuringChunks;
        PRELOAD_PER_TICK_BEFORE_BUILD = RorcikStructuresModConfig.preloadPerTickBeforeBuild;

        // Memory
        BUILDING_MEMORY_BUDGET_PERCENT = RorcikStructuresModConfig.buildingMemoryBudgetPercent;
        ROOM_MEMORY_BUDGET_PERCENT = RorcikStructuresModConfig.roomMemoryBudgetPercent;
        RESERVED_RAM_MB = RorcikStructuresModConfig.reservedRamMb;
    }
}