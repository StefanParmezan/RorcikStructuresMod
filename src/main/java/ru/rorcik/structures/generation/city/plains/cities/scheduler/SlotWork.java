package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record SlotWork(String slotId, String slotType, List<Phase> phases) {
    public SlotWork(String slotId, String slotType, List<Phase> phases) {
        this.slotId = slotId;
        this.slotType = slotType;
        this.phases = phases != null ? phases : Collections.emptyList();
    }

    public SlotWork(String slotId, Phase phase) {
        this(slotId, "UNKNOWN", phase != null ? List.of(phase) : Collections.emptyList());
    }

    public SlotWork(String slotId, Phase phase, Set<ResourceLocation> templates) {
        this(slotId, "UNKNOWN", phase != null ? List.of(new Phase(phase.type(), phase.action(), templates)) : Collections.emptyList());
    }
}