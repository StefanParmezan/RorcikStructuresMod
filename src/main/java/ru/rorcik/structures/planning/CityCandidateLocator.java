package ru.rorcik.structures.planning;

import ru.rorcik.structures.city.CityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CityCandidateLocator {

    private static final int MAX_MIN_DISTANCE = maxMinDistance();

    private final long seed;

    public CityCandidateLocator(long seed) {
        this.seed = seed;
    }

    public List<CityCandidate> candidatesNear(int blockX, int blockZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        Set<CityCandidate> seen = new HashSet<>();
        List<CityCandidate> candidates = new ArrayList<>();

        for (CityType type : CityType.values()) {
            int cellSize = cellSize(type);
            int minCellX = Math.floorDiv(blockX - clampedRadius, cellSize);
            int maxCellX = Math.floorDiv(blockX + clampedRadius, cellSize);
            int minCellZ = Math.floorDiv(blockZ - clampedRadius, cellSize);
            int maxCellZ = Math.floorDiv(blockZ + clampedRadius, cellSize);

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    CityCandidate candidate = rawCandidate(type, cellX, cellZ);
                    if (!seen.add(candidate)) continue;

                    if (Math.abs(candidate.centerX() - blockX) > clampedRadius) continue;
                    if (Math.abs(candidate.centerZ() - blockZ) > clampedRadius) continue;
                    if (!isValid(candidate)) continue;

                    candidates.add(candidate);
                }
            }
        }

        candidates.sort(Comparator.comparingLong(CityCandidate::rank));
        return candidates;
    }

    private boolean isValid(CityCandidate candidate) {
        for (CityType type : CityType.values()) {
            int cellSize = cellSize(type);
            int minCellX = Math.floorDiv(candidate.centerX() - MAX_MIN_DISTANCE, cellSize);
            int maxCellX = Math.floorDiv(candidate.centerX() + MAX_MIN_DISTANCE, cellSize);
            int minCellZ = Math.floorDiv(candidate.centerZ() - MAX_MIN_DISTANCE, cellSize);
            int maxCellZ = Math.floorDiv(candidate.centerZ() + MAX_MIN_DISTANCE, cellSize);

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    CityCandidate other = rawCandidate(type, cellX, cellZ);
                    if (other.equals(candidate)) continue;

                    int requiredDistance = Math.max(candidate.minDistance(), other.minDistance());
                    long dx = (long) candidate.centerX() - other.centerX();
                    long dz = (long) candidate.centerZ() - other.centerZ();
                    if (dx * dx + dz * dz >= (long) requiredDistance * requiredDistance) continue;

                    if (compareRank(other, candidate) < 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private CityCandidate rawCandidate(CityType type, int cellX, int cellZ) {
        int cellSize = cellSize(type);
        int jitter = type.minDistance / 4;
        int jitterSpan = jitter * 2 + 1;

        long hash = mix(seed);
        hash = mix(hash ^ ((long) type.ordinal() * 0x9E3779B97F4A7C15L));
        hash = mix(hash ^ cellX);
        hash = mix(hash ^ ((long) cellZ * 0xBF58476D1CE4E5B9L));

        int offsetX = (int) ((hash & 0xFFFFFFFFL) % jitterSpan) - jitter;
        int offsetZ = (int) (((hash >>> 32) & 0xFFFFFFFFL) % jitterSpan) - jitter;

        long cellOriginX = (long) cellX * cellSize + cellSize / 2L;
        long cellOriginZ = (long) cellZ * cellSize + cellSize / 2L;

        int centerX = (int) (cellOriginX + offsetX);
        int centerZ = (int) (cellOriginZ + offsetZ);
        long rank = mix(hash ^ 0xD1B54A32D192ED03L);

        return new CityCandidate(centerX, centerZ, type, rank);
    }

    private static int compareRank(CityCandidate first, CityCandidate second) {
        int byRank = Long.compare(first.rank(), second.rank());
        if (byRank != 0) return byRank;

        int byX = Integer.compare(first.centerX(), second.centerX());
        if (byX != 0) return byX;

        return Integer.compare(first.centerZ(), second.centerZ());
    }

    private static int cellSize(CityType type) {
        return type.minDistance;
    }

    private static int maxMinDistance() {
        int max = 0;
        for (CityType type : CityType.values()) {
            max = Math.max(max, type.minDistance);
        }
        return max;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
