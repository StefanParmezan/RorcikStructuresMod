package ru.rorcik.structures.generation.city.plains.cities.scheduler;

public enum PhaseType {
    ROAD,
    ROAD_DECOR,
    HOUSE_SHELL,
    INSTALL,
    ROOMS,
    SIDEWALKS;

    public int order() {
        return ordinal();
    }
}