package ru.rorcik.structures.generation.city.plains.cities.buildings;

import ru.rorcik.structures.data.city.plains.containers.ParkConfig;

import java.util.List;

@FunctionalInterface
public interface ParkAvailability {

    List<ParkConfig> getAvailableParks();
}
