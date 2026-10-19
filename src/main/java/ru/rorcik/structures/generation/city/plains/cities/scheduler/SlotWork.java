package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SlotWork {
    private final String slotId;
    private final List<Phase> phases;
    private int currentPhaseIndex = 0;

    public SlotWork(String slotId, List<Phase> phases) {
        this.slotId = slotId;
        this.phases = new ArrayList<>(phases);
        this.phases.sort(Comparator.comparingInt(p -> p.type().order()));
    }

    public String slotId() { return slotId; }
    public List<Phase> phases() { return phases; }
    public boolean hasNextPhase() { return currentPhaseIndex < phases.size(); }
    public Phase nextPhase() {
        if (!hasNextPhase()) throw new IllegalStateException("No more phases for slot " + slotId);
        return phases.get(currentPhaseIndex++);
    }
    public boolean isComplete() { return currentPhaseIndex >= phases.size(); }
    public void reset() { currentPhaseIndex = 0; }
}