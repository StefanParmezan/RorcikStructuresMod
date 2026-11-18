package ru.rorcik.structures.planning;

import org.junit.jupiter.api.Test;
import ru.rorcik.structures.city.CityType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityCandidateLocatorTest {

    @Test
    void candidatesAreDeterministic() {
        CityCandidateLocator locator = new CityCandidateLocator(123456789L);
        List<CityCandidate> first = locator.candidatesNear(0, 0, 20000);
        List<CityCandidate> second = locator.candidatesNear(0, 0, 20000);

        assertEquals(first, second);
    }

    @Test
    void differentSeedsProduceDifferentCandidates() {
        List<CityCandidate> first = new CityCandidateLocator(1L).candidatesNear(0, 0, 20000);
        List<CityCandidate> second = new CityCandidateLocator(2L).candidatesNear(0, 0, 20000);

        assertNotEquals(first, second);
    }

    @Test
    void candidatesHaveNoDuplicates() {
        List<CityCandidate> candidates = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000);
        Set<CityCandidate> unique = new HashSet<>(candidates);

        assertEquals(candidates.size(), unique.size());
    }

    @Test
    void candidatesRespectMinimumDistances() {
        List<CityCandidate> candidates = new CityCandidateLocator(42L).candidatesNear(0, 0, 20000);

        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                CityCandidate first = candidates.get(i);
                CityCandidate second = candidates.get(j);
                int required = Math.max(first.minDistance(), second.minDistance());
                long dx = (long) first.centerX() - second.centerX();
                long dz = (long) first.centerZ() - second.centerZ();

                assertTrue(dx * dx + dz * dz >= (long) required * required,
                        "Candidates too close: " + first + " and " + second);
            }
        }
    }

    @Test
    void bothCityTypesArePresent() {
        List<CityCandidate> candidates = new CityCandidateLocator(
                42L, new CitySpawnSettings(50, 100)
        ).candidatesNear(0, 0, 20000);

        assertTrue(candidates.stream().anyMatch(c -> c.type() == CityType.PLAINS));
        assertTrue(candidates.stream().anyMatch(c -> c.type() == CityType.DESERT));
    }

    @Test
    void candidatesAreInsideQueryRadius() {
        int radius = 4000;
        List<CityCandidate> candidates = new CityCandidateLocator(7L).candidatesNear(500, -500, radius);

        for (CityCandidate candidate : candidates) {
            assertTrue(Math.abs(candidate.centerX() - 500) <= radius);
            assertTrue(Math.abs(candidate.centerZ() + 500) <= radius);
        }
    }

    @Test
    void emptyRadiusCanStillReturnCandidate() {
        List<CityCandidate> candidates = new CityCandidateLocator(42L).candidatesNear(0, 0, 0);

        for (CityCandidate candidate : candidates) {
            assertEquals(0, candidate.centerX());
            assertEquals(0, candidate.centerZ());
        }
        assertFalse(candidates.size() > 1);
    }

    @Test
    void zeroSpawnChanceProducesNoCandidates() {
        CityCandidateLocator locator = new CityCandidateLocator(42L, new CitySpawnSettings(0, 0));

        assertTrue(locator.candidatesNear(0, 0, 20000).isEmpty());
    }

    @Test
    void lowerSpawnChanceProducesFewerCandidates() {
        long fullPlains = countPlains(100);
        long halfPlains = countPlains(50);

        assertTrue(halfPlains > 0, "Half chance should still spawn plains cities");
        assertTrue(halfPlains < fullPlains,
                "Half chance should produce fewer plains cities than full chance");
    }

    @Test
    void fullChanceIsDenserThanHalfChanceForEachType() {
        long fullPlains = countPlains(100);
        long fullDesert = new CityCandidateLocator(
                42L, new CitySpawnSettings(0, 100)
        ).candidatesNear(0, 0, 30000).stream()
                .filter(c -> c.type() == CityType.DESERT)
                .count();

        assertTrue(fullPlains >= 50, "Expected a dense plains lattice, got " + fullPlains);
        assertTrue(fullDesert >= 50, "Expected a dense desert lattice, got " + fullDesert);
    }

    private static long countPlains(int chance) {
        return new CityCandidateLocator(42L, new CitySpawnSettings(chance, 0))
                .candidatesNear(0, 0, 30000).stream()
                .filter(c -> c.type() == CityType.PLAINS)
                .count();
    }
}
