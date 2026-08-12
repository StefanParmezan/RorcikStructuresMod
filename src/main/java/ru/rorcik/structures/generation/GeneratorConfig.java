package ru.rorcik.structures.generation;

public final class GeneratorConfig {
    private GeneratorConfig() {}

    public static final int ROAD_STEP = 64;
    public static final int BUILDING_DISTANCE = 64;

    public static final int STRAIGHT_BETWEEN_NODES = 2;
    public static final int MAX_GRAPH_DEPTH = 7;
    public static final int MAX_NODES = 70;

    public static final int BUILDING_MIN_DEPTH = 2;
    public static final int BUILDING_MAX_DEPTH = 2;

    public static final int HOUSES_CHANCE = 90;
    public static final int OTHER_CHANCE = 5;
    public static final int LOOTABLE_CHANCE = 3;
    public static final int PARKS_CHANCE = 2;

    public static final boolean SPAWN_PARKS = true;
    public static final boolean PARK_ONLY_RECT = false;

    public static final int INITIAL_STRUCTURES_PER_TICK = 16;
    public static final long TARGET_TICK_TIME_MS = 30;
    public static final int MIN_STRUCTURES_PER_TICK = 2;
    public static final int MAX_STRUCTURES_PER_TICK = 64;
    public static final int CHUNKS_PER_TICK = 48;

    public static final boolean UPDATE_LIGHTING = false;
    public static final boolean UPDATE_NEIGHBORS = false;
    public static final boolean PRECOMPILE_ALL_TEMPLATES = false;

    public static final int MIN_BUILDING_CACHE = 16;
    public static final int MAX_BUILDING_CACHE_LIMIT = 512;

    public static final int MIN_ROOM_CACHE = 64;
    public static final int MAX_ROOM_CACHE_LIMIT = 4096;

    public static final long RESERVED_RAM_MB = 2048;
    public static final int BUILDING_MEMORY_BUDGET_PERCENT = 70;
    public static final int ROOM_MEMORY_BUDGET_PERCENT = 30;

    public static final double DEFAULT_DECOMPRESSION_FACTOR = 5.0;
    public static final int DECOMPRESSION_SAMPLE_SIZE = 20;

    public static final long FALLBACK_AVG_BUILDING_BYTES = 500L * 1024L;
    public static final long FALLBACK_AVG_ROOM_BYTES = 5L * 1024L;
}