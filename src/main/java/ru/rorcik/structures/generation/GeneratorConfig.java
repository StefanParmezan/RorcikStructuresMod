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

    // Все шансы в сумме должны давать 100
    public static final int HOUSES_CHANCE = 90;
    public static final int OTHER_CHANCE = 5;
    public static final int LOOTABLE_CHANCE = 3;
    public static final int PARKS_CHANCE = 2;

    public static final boolean SPAWN_PARKS = true;
    public static final boolean PARK_ONLY_RECT = false;

    public static final int STRUCTURES_PER_TICK = 32;
    public static final int CHUNKS_PER_TICK = 48;
    public static final int MAX_TEMPLATE_CACHE = 128;
    public static final boolean UPDATE_LIGHTING = false;
    public static final boolean UPDATE_NEIGHBORS = false;
    public static final boolean PRECOMPILE_ALL_TEMPLATES = true;
}