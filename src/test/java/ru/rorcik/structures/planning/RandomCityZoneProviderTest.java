package ru.rorcik.structures.planning;

import org.junit.jupiter.api.Test;
import ru.rorcik.core.city.CityZone;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomCityZoneProviderTest {

    @Test
    void zoneLookupIsDeterministic() {
        RandomCityZoneProvider first = new RandomCityZoneProvider(42L, 63);
        RandomCityZoneProvider second = new RandomCityZoneProvider(42L, 63);

        CityCandidate candidate = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000).get(0);
        int chunkX = candidate.centerX() >> 4;
        int chunkZ = candidate.centerZ() >> 4;

        assertEquals(first.getZoneAtChunk(chunkX, chunkZ), second.getZoneAtChunk(chunkX, chunkZ));
    }

    @Test
    void candidateCenterBelongsToItsZone() {
        RandomCityZoneProvider provider = new RandomCityZoneProvider(42L, 63);
        CityCandidate candidate = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000).get(0);
        int chunkX = candidate.centerX() >> 4;
        int chunkZ = candidate.centerZ() >> 4;

        CityZone zone = provider.getZoneAtChunk(chunkX, chunkZ);

        assertNotNull(zone);
        assertTrue(zone.containsChunk(chunkX, chunkZ));
    }

    @Test
    void nearbyZonesContainCandidateZone() {
        RandomCityZoneProvider provider = new RandomCityZoneProvider(42L, 63);
        CityCandidate candidate = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000).get(0);
        int chunkX = candidate.centerX() >> 4;
        int chunkZ = candidate.centerZ() >> 4;

        List<CityZone> zones = provider.getNearbyZones(candidate.centerX(), candidate.centerZ(), 0);

        assertTrue(zones.stream().anyMatch(zone -> zone.containsChunk(chunkX, chunkZ)));
    }

    @Test
    void validatorCanRejectCandidates() {
        RandomCityZoneProvider provider = new RandomCityZoneProvider(42L, 63, candidate -> false);
        CityCandidate candidate = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000).get(0);
        int chunkX = candidate.centerX() >> 4;
        int chunkZ = candidate.centerZ() >> 4;

        assertNull(provider.getZoneAtChunk(chunkX, chunkZ));
        assertTrue(provider.getNearbyZones(candidate.centerX(), candidate.centerZ(), 0).isEmpty());
    }
}
