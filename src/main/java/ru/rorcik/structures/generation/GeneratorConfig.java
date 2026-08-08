package ru.rorcik.structures.generation;

public final class GeneratorConfig {
    private GeneratorConfig() {}

    public static final int ROAD_STEP = 64;
    public static final int BUILDING_DISTANCE = 64;

    public static final int STRAIGHT_BETWEEN_NODES = 2;
    public static final int MAX_GRAPH_DEPTH = 5;
    public static final int MAX_NODES = 70;

    public static final int BUILDING_MIN_DEPTH = 2;
    public static final int BUILDING_MAX_DEPTH = 2;
    public static final int HOUSES_CHANCE = 90;
    public static final int OTHER_CHANCE = 5;
    public static final int LOOTABLE_CHANCE = 4;
    public static final int PARKS_CHANCE = 1;

    public static final boolean SPAWN_PARKS = true;
}