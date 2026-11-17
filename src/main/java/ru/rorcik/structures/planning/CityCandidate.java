package ru.rorcik.structures.planning;

import ru.rorcik.structures.city.CityType;

public record CityCandidate(int centerX, int centerZ, CityType type, long rank) {

    public int minDistance() {
        return type.minDistance;
    }

    /**
     * Conflict priority: a type with a larger minimum distance is kept first,
     * so desert candidates never displace a plains city that needs 5000 blocks
     * of space. Ties fall back to the deterministic rank.
     */
    public static int comparePriority(CityCandidate first, CityCandidate second) {
        int byDistance = Integer.compare(second.minDistance(), first.minDistance());
        if (byDistance != 0) return byDistance;

        int byRank = Long.compare(first.rank(), second.rank());
        if (byRank != 0) return byRank;

        int byX = Integer.compare(first.centerX(), second.centerX());
        if (byX != 0) return byX;

        return Integer.compare(first.centerZ(), second.centerZ());
    }
}
