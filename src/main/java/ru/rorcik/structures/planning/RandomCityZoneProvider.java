package ru.rorcik.structures.planning;

import ru.rorcik.core.city.CityZone;
import ru.rorcik.core.city.CityZoneProvider;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class RandomCityZoneProvider implements CityZoneProvider {

    private static final int MAX_CITY_RADIUS = 2048;
    private static final int MAX_CACHED_PLANS = 4096;
    private static final int MAX_CACHED_ZONES = 8192;

    private final long seed;
    private final int seaLevel;
    private final CityCandidateLocator candidateLocator;
    private final CityCandidateValidator candidateValidator;
    private final ConcurrentHashMap<PlanKey, CityLayoutPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PlanKey, Boolean> validityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, java.util.Optional<CityZone>> zoneCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NearbyKey, List<CityLayoutPlan>> nearbyCache = new ConcurrentHashMap<>();

    public RandomCityZoneProvider(long seed, int seaLevel) {
        this(seed, seaLevel, candidate -> true);
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

        for (CityCandidate candidate : candidateLocator.candidatesNear(blockX, blockZ, MAX_CITY_RADIUS)) {
            if (!isValidCandidate(candidate)) continue;

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

        List<CityLayoutPlan> cached = nearbyCache.get(key);
        if (cached == null) {
            int centerX = (chunkX << 4) + 8;
            int centerZ = (chunkZ << 4) + 8;
            int queryRadius = clampedRadius + MAX_CITY_RADIUS + 16;
            List<CityLayoutPlan> computed = new ArrayList<>();

            for (CityCandidate candidate : candidateLocator.candidatesNear(centerX, centerZ, queryRadius)) {
                if (!isValidCandidate(candidate)) continue;

                computed.add(plan(candidate));
            }

            if (nearbyCache.size() > MAX_CACHED_ZONES) {
                nearbyCache.clear();
            }
            cached = computed;
            nearbyCache.put(key, cached);
        }

        int minX = blockX - clampedRadius;
        int maxX = blockX + clampedRadius;
        int minZ = blockZ - clampedRadius;
        int maxZ = blockZ + clampedRadius;
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        List<CityZone> zones = new ArrayList<>();
        for (CityLayoutPlan plan : cached) {
            if (!intersectsZoneChunks(plan, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) continue;
            zones.add(plan.zone());
        }
        return zones;
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
        return candidateLocator.candidatesNear(blockX, blockZ, radius);
    }

    public boolean isCandidateValid(CityCandidate candidate) {
        return isValidCandidate(candidate);
    }

    public CityLayoutPlan getLayoutPlan(CityCandidate candidate) {
        return plan(candidate);
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

    private boolean isValidCandidate(CityCandidate candidate) {
        if (validityCache.size() > MAX_CACHED_PLANS) {
            validityCache.clear();
        }

        PlanKey key = planKey(candidate);
        return validityCache.computeIfAbsent(key, ignored -> candidateValidator.isValid(candidate));
    }

    private static PlanKey planKey(CityCandidate candidate) {
        return new PlanKey(candidate.type(), candidate.centerX(), candidate.centerZ());
    }

    private record PlanKey(CityType type, int centerX, int centerZ) {
    }

    private record NearbyKey(int chunkX, int chunkZ, int radius) {
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
