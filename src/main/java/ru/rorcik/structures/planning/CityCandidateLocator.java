package ru.rorcik.structures.planning;

import ru.rorcik.structures.city.CityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic infinite city candidate lattice.
 *
 * <p>Candidates are placed on a triangular lattice with nearest-neighbour
 * distance slightly larger than the type minimum distance. A small jitter is
 * applied inside each site, but never enough to violate the minimum distance.
 * This allows every enabled site to be a valid city instead of dropping a
 * large part of a square grid to conflicts.</p>
 */
public final class CityCandidateLocator {

    private static final double SQRT3_OVER_2 = 0.8660254037844386;
    private static final int MAX_MIN_DISTANCE = maxMinDistance();

    private final long seed;
    private final CitySpawnSettings spawnSettings;

    public CityCandidateLocator(long seed) {
        this(seed, CitySpawnSettings.DEFAULT);
    }

    public CityCandidateLocator(long seed, CitySpawnSettings spawnSettings) {
        this.seed = seed;
        this.spawnSettings = spawnSettings == null ? CitySpawnSettings.DEFAULT : spawnSettings;
    }

    public List<CityCandidate> candidatesNear(int blockX, int blockZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        Set<CityCandidate> seen = new HashSet<>();
        List<CityCandidate> candidates = new ArrayList<>();

        for (CityType type : CityType.values()) {
            collectRawCandidates(type,
                    blockX - clampedRadius, blockX + clampedRadius,
                    blockZ - clampedRadius, blockZ + clampedRadius,
                    seen);
        }

        for (CityCandidate candidate : seen) {
            if (!isValid(candidate)) continue;
            candidates.add(candidate);
        }

        candidates.sort(Comparator.comparingLong(CityCandidate::rank));
        return candidates;
    }

    public List<CityCandidate> rawCandidatesNear(int blockX, int blockZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        Set<CityCandidate> candidates = new HashSet<>();

        for (CityType type : CityType.values()) {
            collectRawCandidates(type,
                    blockX - clampedRadius, blockX + clampedRadius,
                    blockZ - clampedRadius, blockZ + clampedRadius,
                    candidates);
        }

        return new ArrayList<>(candidates);
    }

    private boolean isValid(CityCandidate candidate) {
        for (CityCandidate other : rawCandidatesNear(candidate.centerX(), candidate.centerZ(), MAX_MIN_DISTANCE)) {
            if (other.equals(candidate)) continue;

            int requiredDistance = Math.max(candidate.minDistance(), other.minDistance());
            long dx = (long) candidate.centerX() - other.centerX();
            long dz = (long) candidate.centerZ() - other.centerZ();
            if (dx * dx + dz * dz >= (long) requiredDistance * requiredDistance) continue;

            if (CityCandidate.comparePriority(other, candidate) < 0) {
                return false;
            }
        }
        return true;
    }

    private void collectRawCandidates(CityType type, int minX, int maxX, int minZ, int maxZ,
                                      Set<CityCandidate> out) {
        int spacing = spacing(type);
        int rowStep = rowStep(type);
        int jitter = jitter(type);

        int minRow = Math.floorDiv(minZ - jitter - rowStep, rowStep);
        int maxRow = Math.floorDiv(maxZ + jitter + rowStep, rowStep);

        for (int row = minRow; row <= maxRow; row++) {
            int rowOffset = (row & 1) != 0 ? spacing / 2 : 0;
            int minCol = Math.floorDiv(minX - jitter - rowOffset - spacing, spacing);
            int maxCol = Math.floorDiv(maxX + jitter - rowOffset + spacing, spacing);

            for (int col = minCol; col <= maxCol; col++) {
                if (!isSiteEnabled(type, row, col)) continue;

                CityCandidate candidate = rawCandidate(type, row, col);
                if (candidate.centerX() < minX || candidate.centerX() > maxX) continue;
                if (candidate.centerZ() < minZ || candidate.centerZ() > maxZ) continue;

                out.add(candidate);
            }
        }
    }

    private boolean isSiteEnabled(CityType type, int row, int col) {
        int chance = spawnSettings.chance(type);
        if (chance >= 100) return true;
        if (chance <= 0) return false;

        return Math.floorMod(siteHash(type, row, col), 100) < chance;
    }

    private CityCandidate rawCandidate(CityType type, int row, int col) {
        int spacing = spacing(type);
        int rowStep = rowStep(type);
        int jitter = jitter(type);

        long hash = siteHash(type, row, col);
        int jitterSpan = jitter * 2 + 1;

        int offsetX = (int) ((hash & 0xFFFFFFFFL) % jitterSpan) - jitter;
        int offsetZ = (int) (((hash >>> 32) & 0xFFFFFFFFL) % jitterSpan) - jitter;

        long baseX = (long) col * spacing + ((row & 1) != 0 ? spacing / 2 : 0) + spacing / 2L;
        long baseZ = (long) row * rowStep + rowStep / 2L;

        int centerX = (int) (baseX + offsetX);
        int centerZ = (int) (baseZ + offsetZ);
        long rank = mix(hash ^ 0xD1B54A32D192ED03L);

        return new CityCandidate(centerX, centerZ, type, rank);
    }

    private long siteHash(CityType type, int row, int col) {
        long hash = mix(seed);
        hash = mix(hash ^ ((long) type.ordinal() * 0x9E3779B97F4A7C15L));
        hash = mix(hash ^ row);
        return mix(hash ^ ((long) col * 0xBF58476D1CE4E5B9L));
    }

    private static int spacing(CityType type) {
        int distance = type.minDistance;
        return distance + Math.max(1, distance / 8);
    }

    private static int rowStep(CityType type) {
        return (int) Math.floor(spacing(type) * SQRT3_OVER_2);
    }

    /**
     * Per-axis jitter. The lattice margin is {@code spacing - minDistance};
     * moving two neighbours by {@code 2 * sqrt(2) * jitter} towards each other
     * must still leave the minimum distance, so the margin is split into three.
     */
    private static int jitter(CityType type) {
        int margin = spacing(type) - type.minDistance;
        return Math.max(1, margin / 3);
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
