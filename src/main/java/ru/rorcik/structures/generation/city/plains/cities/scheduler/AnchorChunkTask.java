package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class AnchorChunkTask {
    public enum State { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    private static final int FOOTPRINT_MARGIN = 8;

    private final int anchorChunkX;
    private final int anchorChunkZ;
    private final int cityCenterX;
    private final int cityCenterZ;
    private final String cityId;
    private final List<SlotWork> slotWorks;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final AtomicInteger completedPhases = new AtomicInteger(0);
    private final int totalPhases;
    private volatile State state = State.PENDING;
    private volatile int priority;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final Set<Long> ticketedChunks = new HashSet<>();
    private final Set<Long> requiredChunks;

    public AnchorChunkTask(int anchorChunkX, int anchorChunkZ, List<SlotWork> slotWorks, int cityCenterX, int cityCenterZ) {
        this.anchorChunkX = anchorChunkX;
        this.anchorChunkZ = anchorChunkZ;
        this.cityCenterX = cityCenterX;
        this.cityCenterZ = cityCenterZ;
        this.cityId = "city@" + cityCenterX + "," + cityCenterZ;
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
        this.requiredChunks = computeRequiredChunks();
    }

    private Set<Long> computeRequiredChunks() {
        Set<Long> chunks = new HashSet<>();

        // Use exact footprints when every slot in the task has one. Fall back
        // to the anchor group when some structure size could not be resolved.
        boolean allHaveFootprints = !slotWorks.isEmpty();
        for (SlotWork work : slotWorks) {
            if (work.footprints().isEmpty()) {
                allHaveFootprints = false;
                break;
            }
        }

        if (!allHaveFootprints) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    chunks.add(chunkKey(anchorChunkX + dx, anchorChunkZ + dz));
                }
            }
        }

        for (SlotWork work : slotWorks) {
            for (SlotWork.Footprint footprint : work.footprints()) {
                int minChunkX = (footprint.minX() - FOOTPRINT_MARGIN) >> 4;
                int maxChunkX = (footprint.maxX() + FOOTPRINT_MARGIN) >> 4;
                int minChunkZ = (footprint.minZ() - FOOTPRINT_MARGIN) >> 4;
                int maxChunkZ = (footprint.maxZ() + FOOTPRINT_MARGIN) >> 4;
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        chunks.add(chunkKey(chunkX, chunkZ));
                    }
                }
            }
        }
        return chunks;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public Set<Long> requiredChunks() {
        return requiredChunks;
    }

    public int anchorChunkX() { return anchorChunkX; }
    public int anchorChunkZ() { return anchorChunkZ; }
    public int centerX() { return cityCenterX; }
    public int centerZ() { return cityCenterZ; }
    public String cityId() { return cityId; }
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

    public int incrementRetryCount() { return retryCount.incrementAndGet(); }
    public int getRetryCount() { return retryCount.get(); }
    public void resetRetryCount() { retryCount.set(0); }

    public boolean addTicketedChunk(int chunkX, int chunkZ) {
        return ticketedChunks.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
    }

    public Set<Long> ticketedChunks() {
        return ticketedChunks;
    }

    public void clearTicketedChunks() {
        ticketedChunks.clear();
    }

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
        return String.format("AnchorChunkTask[%d,%d -> %d,%d | slots=%d | phases=%d/%d | state=%s | priority=%d | retries=%d]",
                anchorChunkX, anchorChunkZ, anchorChunkX + 3, anchorChunkZ + 3,
                slotWorks.size(), completedPhases.get(), totalPhases, state, priority, retryCount.get());
    }
}