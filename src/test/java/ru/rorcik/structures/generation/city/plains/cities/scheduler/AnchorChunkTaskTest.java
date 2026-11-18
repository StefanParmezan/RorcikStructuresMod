package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorChunkTaskTest {

    @Test
    void usesExactFootprintChunksWhenAvailable() {
        SlotWork work = new SlotWork(
                "test", "BUILDING", List.of(),
                List.of(new SlotWork.Footprint(0, 0, 15, 15))
        );
        AnchorChunkTask task = new AnchorChunkTask(100, 100, List.of(work), 0, 0);

        assertTrue(task.requiredChunks().contains(chunkKey(0, 0)));
        assertFalse(task.requiredChunks().contains(chunkKey(100, 100)));
    }

    @Test
    void fallsBackToAnchorGroupWithoutFootprints() {
        SlotWork work = new SlotWork("test", "BUILDING", List.of());
        AnchorChunkTask task = new AnchorChunkTask(100, 100, List.of(work), 0, 0);

        assertTrue(task.requiredChunks().contains(chunkKey(100, 100)));
        assertTrue(task.requiredChunks().contains(chunkKey(103, 103)));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
