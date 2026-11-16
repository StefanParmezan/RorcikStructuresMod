package ru.rorcik.structures.planning;

import ru.rorcik.core.city.CityZone;
import ru.rorcik.core.city.CityZoneProvider;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RandomCityZoneProvider implements CityZoneProvider {

    private static final int MAX_CITY_RADIUS = 2048;
    private static final int MAX_CACHED_PLANS = 4096;
    private static final int MAX_CACHED_ZONES = 8192;
    private static final int MAX_MIN_DISTANCE = maxMinDistance();

    private final long seed;
    private final int seaLevel;
    private final CityCandidateLocator candidateLocator;
    private final CityCandidateValidator candidateValidator;
    private final ConcurrentHashMap<PlanKey, CityLayoutPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RawKey, java.util.Optional<CityType>> typeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, java.util.Optional<CityZone>> zoneCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NearbyKey, List<CityZone>> nearbyCache = new ConcurrentHashMap<>();

    public RandomCityZoneProvider(long seed, int seaLevel) {
        this(seed, seaLevel, candidate -> candidate.type());
    }

    public RandomCityZoneProvider(long seed, int seaLevel, CityCandidateValidator candidateValidator) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.candidateLocator = new CityCandidateLocator(seed);
        this.candidateValidator = candidateValidator;
    }

    @Override
    public CityZone getZoneAtChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        java.util.Optional<CityZone> cached = zoneCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }

        CityZone zone = computeZoneAtChunk(chunkX, chunkZ);
        if (zoneCache.size() > MAX_CACHED_ZONES) {
            zoneCache.clear();
        }
        zoneCache.put(key, java.util.Optional.ofNullable(zone));
        return zone;
    }

    private CityZone computeZoneAtChunk(int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;

        CityZone best = null;
        long bestDistance = Long.MAX_VALUE;

        for (CityCandidate candidate : resolveCandidates(blockX, blockZ, MAX_CITY_RADIUS)) {
            CityLayoutPlan plan = plan(candidate);
            long chunkKey = chunkKey(chunkX, chunkZ);
            if (!plan.zoneChunks().contains(chunkKey)) continue;

            CityZone zone = plan.zone();
            long dx = (long) blockX - candidate.centerX();
            long dz = (long) blockZ - candidate.centerZ();
            long distance = dx * dx + dz * dz;
            if (best == null || distance < bestDistance) {
                best = zone;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public List<CityZone> getNearbyZones(int blockX, int blockZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        NearbyKey key = new NearbyKey(chunkX, chunkZ, clampedRadius);

        List<CityZone> cached = nearbyCache.get(key);
        if (cached != null) {
            return cached;
        }

        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int queryRadius = clampedRadius + MAX_CITY_RADIUS + 16;

        int minBlockX = (chunkX << 4) - clampedRadius - 16;
        int maxBlockX = (chunkX << 4) + 15 + clampedRadius + 16;
        int minBlockZ = (chunkZ << 4) - clampedRadius - 16;
        int maxBlockZ = (chunkZ << 4) + 15 + clampedRadius + 16;
        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        List<CityZone> computed = new ArrayList<>();
        for (CityCandidate candidate : resolveCandidates(centerX, centerZ, queryRadius)) {
            CityLayoutPlan plan = plan(candidate);
            if (!intersectsZoneChunks(plan, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) continue;
            computed.add(plan.zone());
        }

        if (nearbyCache.size() > MAX_CACHED_ZONES) {
            nearbyCache.clear();
        }
        nearbyCache.put(key, computed);
        return computed;
    }

    private static boolean intersectsZoneChunks(CityLayoutPlan plan, int minChunkX, int minChunkZ,
                                                int maxChunkX, int maxChunkZ) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (plan.zoneChunks().contains(chunkKey(chunkX, chunkZ))) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<CityCandidate> candidatesNear(int blockX, int blockZ, int radius) {
        return resolveCandidates(blockX, blockZ, radius);
    }

    public boolean isCandidateValid(CityCandidate candidate) {
        return resolveType(candidate) != null;
    }

    public CityLayoutPlan getLayoutPlan(CityCandidate candidate) {
        return plan(candidate);
    }

    private List<CityCandidate> resolveCandidates(int blockX, int blockZ, int radius) {
        int clampedRadius = Math.max(0, radius);
        int queryRadius = clampedRadius + MAX_MIN_DISTANCE;
        List<CityCandidate> rawCandidates = candidateLocator.rawCandidatesNear(blockX, blockZ, queryRadius);

        Map<TypedKey, CityCandidate> typed = new HashMap<>();
        for (CityCandidate raw : rawCandidates) {
            CityType type = resolveType(raw);
            if (type == null) continue;

            CityCandidate candidate = new CityCandidate(raw.centerX(), raw.centerZ(), type, raw.rank());
            TypedKey key = new TypedKey(type, candidate.centerX(), candidate.centerZ());
            typed.merge(key, candidate, (first, second) ->
                    comparePriority(first, second) <= 0 ? first : second);
        }

        List<CityCandidate> resolved = new ArrayList<>();
        for (CityCandidate candidate : typed.values()) {
            if (Math.abs(candidate.centerX() - blockX) > clampedRadius) continue;
            if (Math.abs(candidate.centerZ() - blockZ) > clampedRadius) continue;
            if (!hasConflict(candidate, typed.values())) {
                resolved.add(candidate);
            }
        }
        resolved.sort((first, second) -> comparePriority(first, second));
        return resolved;
    }

    private static boolean hasConflict(CityCandidate candidate, Iterable<CityCandidate> others) {
        for (CityCandidate other : others) {
            if (other.equals(candidate)) continue;

            int requiredDistance = Math.max(candidate.minDistance(), other.minDistance());
            long dx = (long) candidate.centerX() - other.centerX();
            long dz = (long) candidate.centerZ() - other.centerZ();
            if (dx * dx + dz * dz >= (long) requiredDistance * requiredDistance) continue;

            if (comparePriority(other, candidate) < 0) {
                return true;
            }
        }
        return false;
    }

    private CityType resolveType(CityCandidate candidate) {
        if (typeCache.size() > MAX_CACHED_PLANS) {
            typeCache.clear();
        }

        RawKey key = new RawKey(candidate.type(), candidate.centerX(), candidate.centerZ());
        return typeCache.computeIfAbsent(key, ignored ->
                java.util.Optional.ofNullable(candidateValidator.resolveType(candidate))
        ).orElse(null);
    }

    private CityLayoutPlan plan(CityCandidate candidate) {
        if (plans.size() > MAX_CACHED_PLANS) {
            plans.clear();
        }

        PlanKey key = planKey(candidate);
        return plans.computeIfAbsent(key, ignored -> {
            if (candidate.type() == CityType.PLAINS) {
                return CityLayoutPlanner.planPlains(
                        seed, candidate.centerX(), candidate.centerZ(), seaLevel, ParkConfigLoader::getParks
                );
            }
            return CityLayoutPlanner.planDesert(seed, candidate.centerX(), candidate.centerZ(), seaLevel);
        });
    }

    private static int comparePriority(CityCandidate first, CityCandidate second) {
        int byRank = Long.compare(first.rank(), second.rank());
        if (byRank != 0) return byRank;

        int byX = Integer.compare(first.centerX(), second.centerX());
        if (byX != 0) return byX;

        return Integer.compare(first.centerZ(), second.centerZ());
    }

    private static PlanKey planKey(CityCandidate candidate) {
        return new PlanKey(candidate.type(), candidate.centerX(), candidate.centerZ());
    }

    private static int maxMinDistance() {
        int max = 0;
        for (CityType type : CityType.values()) {
            max = Math.max(max, type.minDistance);
        }
        return max;
    }

    private record PlanKey(CityType type, int centerX, int centerZ) {
    }

    private record RawKey(CityType type, int centerX, int centerZ) {
    }

    private record TypedKey(CityType type, int centerX, int centerZ) {
    }

    private record NearbyKey(int chunkX, int chunkZ, int radius) {
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
