package ru.rorcik.structures.generation.city.plains.cities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;

    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 2),
            r -> {
                Thread t = new Thread(r, "CityAllocator");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Deque<ChunkLoadTask> chunkQueue = new ArrayDeque<>();
    private static final Deque<BuildTask> buildQueue = new ArrayDeque<>();
    private static final Deque<ResourceLocation> preloadQueue = new ArrayDeque<>();

    private static CompletableFuture<?> pendingBackgroundTask = null;
    private static Runnable onBackgroundTaskDone = null;
    private static Runnable onChunksReady = null;

    private static boolean active = false;
    private static long buildStartTime = 0;
    private static int totalBuildActions = 0;
    private static int completedBuildActions = 0;
    private static long chunkLoadStartTime = 0;
    private static int totalChunks = 0;
    private static int loadedChunks = 0;

    private static int adaptiveStructuresPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
    private static long totalBuildTimeMs = 0;
    private static int adaptiveTickCount = 0;
    private static int warmupTicks = 0;
    private static final int WARMUP_TICKS = 15;
    private static boolean readOnlySet = false;

    private static long lastLogTimeMs = 0;
    private static final long LOG_INTERVAL_MS = 1000;
    private static int preloadedCount = 0;
    private static int totalPreload = 0;

    private static List<GenerationPhase> phases = new ArrayList<>();
    private static int currentPhaseIndex = 0;
    private static boolean phaseStarted = false;

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

    public record GenerationPhase(
            String name,
            Set<ResourceLocation> templatesToPreload,
            List<Runnable> actions,
            StructureCache.Category categoryToClearAfter
    ) {}

    public static void startPhasedGeneration(Set<Long> chunkKeys, List<GenerationPhase> generationPhases) {
        chunkQueue.clear();
        for (long key : chunkKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chunkQueue.add(new ChunkLoadTask(cx, cz));
        }

        phases = new ArrayList<>(generationPhases);
        currentPhaseIndex = 0;
        phaseStarted = false;
        active = true;
        totalChunks = chunkKeys.size();
        loadedChunks = 0;
        buildStartTime = System.currentTimeMillis();
        lastLogTimeMs = System.currentTimeMillis();

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] Starting phased generation: {} phases, {} chunks", phases.size(), totalChunks);
        }
    }

    public static void scheduleChunkLoading(Set<Long> chunkKeys, Runnable whenReady) {
        chunkQueue.clear();
        buildQueue.clear();

        for (long key : chunkKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chunkQueue.add(new ChunkLoadTask(cx, cz));
        }

        onChunksReady = whenReady;
        active = true;
        totalChunks = chunkKeys.size();
        loadedChunks = 0;
        chunkLoadStartTime = System.currentTimeMillis();
        lastLogTimeMs = System.currentTimeMillis();

        adaptiveStructuresPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
        totalBuildTimeMs = 0;
        adaptiveTickCount = 0;
        warmupTicks = 0;

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] Queued {} chunks for loading ({} per tick)",
                    totalChunks, GeneratorConfig.CHUNKS_PER_TICK);
        }
    }

    public static void scheduleBackgroundTask(CompletableFuture<?> task, Runnable whenDone) {
        pendingBackgroundTask = task;
        onBackgroundTaskDone = whenDone;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] Background task scheduled");
        }
    }

    public static ExecutorService getBackgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static void queuePreload(Collection<ResourceLocation> templates) {
        preloadQueue.addAll(templates);
        totalPreload += templates.size();
        if (RorcikStructuresModConfig.enableLogging && !templates.isEmpty()) {
            LOGGER.info("[SCHEDULER] Queued {} templates for interleaved preloading (total in queue: {})",
                    templates.size(), preloadQueue.size());
        }
    }

    public static void scheduleBuildActions(List<Runnable> actions) {
        buildQueue.clear();
        for (Runnable action : actions) {
            buildQueue.add(new BuildTask(action));
        }
        totalBuildActions = actions.size();
        completedBuildActions = 0;
        buildStartTime = System.currentTimeMillis();
        lastLogTimeMs = System.currentTimeMillis();

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] Queued {} build actions (adaptive, starting at {} per tick)",
                    totalBuildActions, adaptiveStructuresPerTick);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (pendingBackgroundTask != null) {
            if (pendingBackgroundTask.isDone()) {
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("[SCHEDULER] Background task completed");
                }
                Runnable callback = onBackgroundTaskDone;
                pendingBackgroundTask = null;
                onBackgroundTaskDone = null;
                if (callback != null) {
                    callback.run();
                }
            }
            return;
        }

        if (!chunkQueue.isEmpty()) {
            processChunkLoading(level);
        } else if (onChunksReady != null) {
            Runnable callback = onChunksReady;
            onChunksReady = null;
            callback.run();
        } else if (currentPhaseIndex < phases.size()) {
            processCurrentPhase(level);
        } else {
            finishGeneration();
        }
    }

    private static void processCurrentPhase(ServerLevel level) {
        GenerationPhase phase = phases.get(currentPhaseIndex);

        if (preloadQueue.isEmpty() && !phaseStarted) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("[SCHEDULER] === PHASE {}: {} ===", currentPhaseIndex + 1, phase.name());
            }
            preloadQueue.addAll(phase.templatesToPreload());
            phaseStarted = true;
        }

        if (!preloadQueue.isEmpty()) {
            processPreloadQueue(GeneratorConfig.PRELOAD_PER_TICK_BEFORE_BUILD);
        } else if (!phase.actions().isEmpty()) {
            if (!readOnlySet) {
                StructureCache.setReadOnly(true);
                readOnlySet = true;
            }
            processPhaseActions(phase);
        } else {
            completeCurrentPhase();
        }
    }

    private static void processPhaseActions(GenerationPhase phase) {
        long startTime = System.currentTimeMillis();
        int processed = 0;

        while (!phase.actions().isEmpty() && processed < adaptiveStructuresPerTick) {
            Runnable action = phase.actions().remove(0);
            try {
                action.run();
                completedBuildActions++;
            } catch (Exception e) {
                LOGGER.error("[SCHEDULER] Build action failed in phase {}", phase.name(), e);
            }
            processed++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        totalBuildTimeMs += elapsed;
        adaptiveTickCount++;
        warmupTicks++;

        if (warmupTicks > WARMUP_TICKS) {
            adjustStructuresPerTick(elapsed);
        }

        if (phase.actions().isEmpty()) {
            completeCurrentPhase();
        }
    }

    private static void completeCurrentPhase() {
        GenerationPhase phase = phases.get(currentPhaseIndex);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] Phase {} completed", phase.name());
        }

        if (phase.categoryToClearAfter() != null) {
            StructureCache.clearCategory(phase.categoryToClearAfter());
        }

        currentPhaseIndex++;
        phaseStarted = false;
        readOnlySet = false;
        StructureCache.setReadOnly(false);
    }

    private static void processChunkLoading(ServerLevel level) {
        int processed = 0;
        var iterator = chunkQueue.iterator();

        while (iterator.hasNext() && processed < GeneratorConfig.CHUNKS_PER_TICK) {
            ChunkLoadTask task = iterator.next();
            level.getChunkSource().getChunk(task.chunkX(), task.chunkZ(), ChunkStatus.FULL, true);
            iterator.remove();
            processed++;
            loadedChunks++;
        }

        if (processed > 0 && totalChunks > 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogTimeMs >= LOG_INTERVAL_MS || loadedChunks == totalChunks) {
                if (RorcikStructuresModConfig.enableLogging) {
                    int percent = (loadedChunks * 100) / totalChunks;
                    LOGGER.info("[SCHEDULER] Chunks: {}/{} ({}%)", loadedChunks, totalChunks, percent);
                }
                lastLogTimeMs = now;
            }
        }
    }

    private static void processPreloadQueue(int perTick) {
        int processed = 0;
        while (!preloadQueue.isEmpty() && processed < perTick) {
            ResourceLocation loc = preloadQueue.poll();
            if (loc != null) {
                StructureCache.get(loc);
                preloadedCount++;
                processed++;
            }
        }
    }

    private static void finishGeneration() {
        long elapsed = System.currentTimeMillis() - buildStartTime;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("[SCHEDULER] City generation complete: {} structures in {} ms ({} per second)",
                    completedBuildActions, elapsed,
                    elapsed > 0 ? (completedBuildActions * 1000L) / elapsed : 0);

            if (adaptiveTickCount > 0) {
                LOGGER.info("[SCHEDULER] Adaptive stats: {} ticks, final rate: {} structures/tick, avg time per tick: {} ms",
                        adaptiveTickCount, adaptiveStructuresPerTick, totalBuildTimeMs / adaptiveTickCount);
            }
        }

        active = false;
        StructureCache.setReadOnly(false);
        readOnlySet = false;

        if (RorcikStructuresModConfig.enableLogging) {
            StructureCache.logStats();
        }

        if (RorcikStructuresModConfig.unloadAfterGeneration) {
            StructureCache.clear();
        }
    }

    private static void adjustStructuresPerTick(long elapsedMs) {
        long target = GeneratorConfig.TARGET_TICK_TIME_MS;
        int min = GeneratorConfig.MIN_STRUCTURES_PER_TICK;
        int max = GeneratorConfig.MAX_STRUCTURES_PER_TICK;
        int previous = adaptiveStructuresPerTick;

        if (elapsedMs > target) {
            adaptiveStructuresPerTick = Math.max(min, adaptiveStructuresPerTick * 8 / 10);
        } else if (elapsedMs < target / 2 && adaptiveStructuresPerTick < max) {
            adaptiveStructuresPerTick = Math.min(max, adaptiveStructuresPerTick * 12 / 10);
        }

        if (previous != adaptiveStructuresPerTick && RorcikStructuresModConfig.debugMode) {
            LOGGER.debug("[SCHEDULER] Adaptive rate: {} -> {} structures/tick (tick took {} ms)",
                    previous, adaptiveStructuresPerTick, elapsedMs);
        }
    }

    public static void reset() {
        chunkQueue.clear();
        buildQueue.clear();
        preloadQueue.clear();
        phases.clear();
        currentPhaseIndex = 0;
        phaseStarted = false;
        pendingBackgroundTask = null;
        onBackgroundTaskDone = null;
        onChunksReady = null;
        active = false;
        totalBuildActions = 0;
        completedBuildActions = 0;
        totalChunks = 0;
        loadedChunks = 0;
        preloadedCount = 0;
        totalPreload = 0;
        adaptiveStructuresPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
        totalBuildTimeMs = 0;
        adaptiveTickCount = 0;
        warmupTicks = 0;
        lastLogTimeMs = 0;
        readOnlySet = false;
        StructureCache.setReadOnly(false);
    }
}