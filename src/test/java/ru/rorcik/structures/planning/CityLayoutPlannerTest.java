package ru.rorcik.structures.planning;

import org.junit.jupiter.api.Test;
import ru.rorcik.structures.city.CityType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityLayoutPlannerTest {

    @Test
    void plainsLayoutIsDeterministic() {
        CityLayoutPlan first = CityLayoutPlanner.planPlains(123456L, 100, -200, 63, List::of);
        CityLayoutPlan second = CityLayoutPlanner.planPlains(123456L, 100, -200, 63, List::of);

        assertEquals(first.chunks(), second.chunks());
        assertEquals(first.zone(), second.zone());
        assertEquals(first.slots(), second.slots());
        assertEquals(first.nodeMap().keySet(), second.nodeMap().keySet());
        assertEquals(first.straightPositions(), second.straightPositions());
    }

    @Test
    void desertLayoutIsDeterministic() {
        CityLayoutPlan first = CityLayoutPlanner.planDesert(987654L, -300, 400, 63);
        CityLayoutPlan second = CityLayoutPlanner.planDesert(987654L, -300, 400, 63);

        assertEquals(first.chunks(), second.chunks());
        assertEquals(first.zone(), second.zone());
        assertEquals(first.slots(), second.slots());
    }

    @Test
    void layoutChunksAreInsideZone() {
        CityLayoutPlan plan = CityLayoutPlanner.planPlains(555L, 0, 0, 63, List::of);

        assertFalse(plan.chunks().isEmpty());
        assertEquals(CityType.PLAINS, plan.type());
        assertEquals(CityType.PLAINS.targetHeight, plan.zone().targetHeight());

        for (long chunkKey : plan.chunks()) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) (chunkKey & 0xFFFFFFFFL);
            assertTrue(plan.zone().containsChunk(chunkX, chunkZ));
        }
    }

    @Test
    void differentSeedsProduceDifferentLayouts() {
        CityLayoutPlan first = CityLayoutPlanner.planPlains(1L, 0, 0, 63, List::of);
        CityLayoutPlan second = CityLayoutPlanner.planPlains(2L, 0, 0, 63, List::of);

        assertNotEquals(first.chunks(), second.chunks());
    }

    @Test
    void desertZoneUsesTargetHeight() {
        CityLayoutPlan plan = CityLayoutPlanner.planDesert(7L, 0, 0, 63);

        assertEquals(CityType.DESERT, plan.type());
        assertEquals(62, plan.zone().targetHeight());
        assertFalse(plan.chunks().isEmpty());
    }
}
