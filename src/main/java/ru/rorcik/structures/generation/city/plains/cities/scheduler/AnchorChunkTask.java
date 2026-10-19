package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class AnchorChunkTask {
    public enum State { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    private final int anchorChunkX;
    private final int anchorChunkZ;
    private final List<SlotWork> slotWorks;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final AtomicInteger completedPhases = new AtomicInteger(0);
    private final int totalPhases;
    private volatile State state = State.PENDING;
    private volatile int priority;

    public AnchorChunkTask(int anchorChunkX, int anchorChunkZ, List<SlotWork> slotWorks) {
        this.anchorChunkX = anchorChunkX;
        this.anchorChunkZ = anchorChunkZ;
        this.slotWorks = new ArrayList<>(slotWorks);
        this.slotWorks.sort(Comparator.comparing(sw -> {
            if (sw.phases().isEmpty()) return Integer.MAX_VALUE;
            return sw.phases().get(0).type().order();
        }));
        int total = 0;
        for (SlotWork sw : slotWorks) total += sw.phases().size();
        this.totalPhases = total;
        int centerX = (anchorChunkX + 2) << 4;
        int centerZ = (anchorChunkZ + 2) << 4;
        this.priority = (int) Math.sqrt((double) centerX * centerX + (double) centerZ * centerZ);
    }

    public int anchorChunkX() { return anchorChunkX; }
    public int anchorChunkZ() { return anchorChunkZ; }
    public int anchorBlockX() { return anchorChunkX << 4; }
    public int anchorBlockZ() { return anchorChunkZ << 4; }
    public List<SlotWork> slotWorks() { return slotWorks; }
    public CompletableFuture<Void> completion() { return completion; }
    public State state() { return state; }
    public void setState(State state) {
        this.state = state;
        if (state == State.COMPLETED || state == State.FAILED) completion.complete(null);
    }
    public int priority() { return priority; }
    public void updatePriority(int playerBlockX, int playerBlockZ) {
        int centerX = (anchorChunkX + 2) << 4;
        int centerZ = (anchorChunkZ + 2) << 4;
        int dx = centerX - playerBlockX;
        int dz = centerZ - playerBlockZ;
        this.priority = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
    }
    public int totalPhases() { return totalPhases; }
    public int completedPhases() { return completedPhases.get(); }
    public void incrementCompletedPhases() { completedPhases.incrementAndGet(); }
    public boolean containsBlock(int blockX, int blockZ) {
        int minX = anchorChunkX << 4;
        int minZ = anchorChunkZ << 4;
        return blockX >= minX && blockX < minX + 64 && blockZ >= minZ && blockZ < minZ + 64;
    }
    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= anchorChunkX && chunkX < anchorChunkX + 4
                && chunkZ >= anchorChunkZ && chunkZ < anchorChunkZ + 4;
    }
    @Override
    public String toString() {
        return String.format("AnchorChunkTask[%d,%d -> %d,%d | slots=%d | phases=%d/%d | state=%s | priority=%d]",
                anchorChunkX, anchorChunkZ, anchorChunkX + 3, anchorChunkZ + 3,
                slotWorks.size(), completedPhases.get(), totalPhases, state, priority);
    }
}