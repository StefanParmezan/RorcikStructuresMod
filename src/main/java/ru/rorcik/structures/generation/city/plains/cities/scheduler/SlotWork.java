package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record SlotWork(String slotId, String slotType, List<Phase> phases, List<Footprint> footprints) {

    /**
     * Block rectangle occupied by the placed structure, including rotation.
     * Used to force-load exactly the chunks that placement will touch instead
     * of blindly loading the whole anchor chunk group.
     */
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        public Footprint {
            if (maxX < minX) maxX = minX;
            if (maxZ < minZ) maxZ = minZ;
        }
    }

    public SlotWork {
        phases = phases != null ? phases : Collections.emptyList();
        footprints = footprints != null ? footprints : List.of();
    }

    public SlotWork(String slotId, String slotType, List<Phase> phases) {
        this(slotId, slotType, phases, List.of());
    }

    public SlotWork(String slotId, Phase phase) {
        this(slotId, "UNKNOWN", phase != null ? List.of(phase) : Collections.emptyList());
    }

    public SlotWork(String slotId, Phase phase, Set<ResourceLocation> templates) {
        this(slotId, "UNKNOWN", phase != null ? List.of(new Phase(phase.type(), phase.action(), templates)) : Collections.emptyList());
    }
}
