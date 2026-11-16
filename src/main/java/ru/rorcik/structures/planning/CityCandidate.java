package ru.rorcik.structures.planning;

import ru.rorcik.structures.city.CityType;

public record CityCandidate(int centerX, int centerZ, CityType type, long rank) {

    public int minDistance() {
        return type.minDistance;
    }
}
