package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.Set;

public class Phase {
    private final PhaseType type;
    private final Runnable action;
    private final Set<ResourceLocation> templatesToPreload;

    public Phase(PhaseType type, Runnable action, Set<ResourceLocation> templatesToPreload) {
        this.type = type;
        this.action = action;
        this.templatesToPreload = templatesToPreload != null ? templatesToPreload : Collections.emptySet();
    }

    public Phase(PhaseType type, Runnable action) {
        this(type, action, Collections.emptySet());
    }

    public PhaseType type() { return type; }
    public Runnable action() { return action; }
    public Set<ResourceLocation> templatesToPreload() { return templatesToPreload; }
}