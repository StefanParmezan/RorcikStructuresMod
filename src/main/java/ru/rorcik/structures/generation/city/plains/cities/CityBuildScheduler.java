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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService PRELOAD_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 2),
            r -> {
                Thread t = new Thread(r, "StructurePreloader");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Deque<ChunkLoadTask> chunkQueue = new ArrayDeque<>();
    private static List<GenerationPhase> phases = new ArrayList<>();
    private static int currentPhaseIndex = 0;

    private static final AtomicInteger pendingPreloads = new AtomicInteger(0);
    private static Runnable onChunksReady = null;
    private static boolean active = false;
    private static int chunkReadyCooldownTicks = 0;
    private static final int CHUNK_READY_DELAY_TICKS = 2;

    private static long phaseStartTime = 0;
    private static int totalPhaseActions = 0;
    private static int completedPhaseActions = 0;
    private static int totalChunks = 0;
    private static int loadedChunks = 0;

    private static int adaptiveActionsPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
    private static long totalPhaseTimeMs = 0;
    private static int adaptiveTickCount = 0;
    private static int warmupTicks = 0;
    private static final int WARMUP_TICKS = 15;
    private static long lastLogTimeMs = 0;
    private static final long LOG_INTERVAL_MS = 1000;

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

    public record GenerationPhase(
            String name,
            Collection<ResourceLocation> templatesToPreload,
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
        active = true;
        totalChunks = chunkKeys.size();
        loadedChunks = 0;
        chunkReadyCooldownTicks = 0;

        LOGGER.info("[SCHEDULER] Starting phased generation: {} phases, {} chunks", phases.size(), totalChunks);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!chunkQueue.isEmpty()) {
            processChunkLoading(level);
        } else if (onChunksReady != null) {
            if (chunkReadyCooldownTicks < CHUNK_READY_DELAY_TICKS) {
                chunkReadyCooldownTicks++;
            } else {
                Runnable callback = onChunksReady;
                onChunksReady = null;
                callback.run();
            }
        } else if (currentPhaseIndex < phases.size()) {
            processCurrentPhase(level);
        } else {
            finishGeneration();
        }
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
                LOGGER.info("[SCHEDULER] Chunks: {}/{} ({}%)", loadedChunks, totalChunks, (loadedChunks * 100) / totalChunks);
                lastLogTimeMs = now;
            }
        }

        if (chunkQueue.isEmpty()) {
            onChunksReady = () -> {
                LOGGER.info("[SCHEDULER] Chunks stabilized. Beginning phased execution.");
                prepareNextPhase();
            };
        }
    }

    private static void prepareNextPhase() {
        if (currentPhaseIndex >= phases.size()) return;

        GenerationPhase phase = phases.get(currentPhaseIndex);
        LOGGER.info("[SCHEDULER] === PHASE {}: {} ===", currentPhaseIndex + 1, phase.name());

        StructureCache.setReadOnly(false);
        pendingPreloads.set(0);

        for (ResourceLocation loc : phase.templatesToPreload()) {
            PRELOAD_EXECUTOR.submit(() -> {
                try {
                    StructureCache.precompile(loc);
                } catch (Exception e) {
                    LOGGER.error("[SCHEDULER] Preload failed: {}", loc, e);
                } finally {
                    pendingPreloads.decrementAndGet();
                }
            });
            pendingPreloads.incrementAndGet();
        }

        totalPhaseActions = phase.actions().size();
        completedPhaseActions = 0;
        phaseStartTime = System.currentTimeMillis();
        lastLogTimeMs = System.currentTimeMillis();
        adaptiveActionsPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
        totalPhaseTimeMs = 0;
        adaptiveTickCount = 0;
        warmupTicks = 0;

        if (phase.templatesToPreload().isEmpty()) {
            executePhaseActions();
        }
    }

    private static void processCurrentPhase(ServerLevel level) {
        if (pendingPreloads.get() > 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogTimeMs >= LOG_INTERVAL_MS) {
                LOGGER.info("[SCHEDULER] Phase {} preloading: {} remaining", currentPhaseIndex + 1, pendingPreloads.get());
                lastLogTimeMs = now;
            }
            return;
        }

        StructureCache.setReadOnly(true);
        executePhaseActions();
    }

    private static void executePhaseActions() {
        if (completedPhaseActions >= totalPhaseActions) {
            completeCurrentPhase();
            return;
        }

        long startTime = System.currentTimeMillis();
        int processed = 0;
        GenerationPhase phase = phases.get(currentPhaseIndex);
        List<Runnable> actions = phase.actions();

        while (completedPhaseActions < totalPhaseActions && processed < adaptiveActionsPerTick) {
            Runnable action = actions.get(completedPhaseActions);
            try {
                action.run();
                completedPhaseActions++;
            } catch (Exception e) {
                LOGGER.error("[SCHEDULER] Action failed in phase {}", phase.name(), e);
                completedPhaseActions++;
            }
            processed++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        totalPhaseTimeMs += elapsed;
        adaptiveTickCount++;
        warmupTicks++;

        if (warmupTicks > WARMUP_TICKS) {
            adjustActionsPerTick(elapsed);
        }

        long now = System.currentTimeMillis();
        if (completedPhaseActions == totalPhaseActions || completedPhaseActions % 50 == 0 || (now - lastLogTimeMs >= LOG_INTERVAL_MS)) {
            LOGGER.info("[SCHEDULER] Phase {} progress: {}/{} (rate: {}/tick, tick: {} ms)",
                    currentPhaseIndex + 1, completedPhaseActions, totalPhaseActions, adaptiveActionsPerTick, elapsed);
            lastLogTimeMs = now;
        }

        if (completedPhaseActions >= totalPhaseActions) {
            completeCurrentPhase();
        }
    }



    private static void completeCurrentPhase() {
        GenerationPhase phase = phases.get(currentPhaseIndex);
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        LOGGER.info("[SCHEDULER] Phase {} completed in {} ms", currentPhaseIndex + 1, elapsed);

        if (phase.categoryToClearAfter() != null) {
            StructureCache.clearCategory(phase.categoryToClearAfter());
        }

        currentPhaseIndex++;
        if (currentPhaseIndex < phases.size()) {
            prepareNextPhase();
        }
    }

    private static void adjustActionsPerTick(long elapsedMs) {
        long target = GeneratorConfig.TARGET_TICK_TIME_MS;
        int min = GeneratorConfig.MIN_STRUCTURES_PER_TICK;
        int max = GeneratorConfig.MAX_STRUCTURES_PER_TICK;
        int previous = adaptiveActionsPerTick;

        if (elapsedMs > target) {
            adaptiveActionsPerTick = Math.max(min, adaptiveActionsPerTick * 8 / 10);
        } else if (elapsedMs < target / 2 && adaptiveActionsPerTick < max) {
            adaptiveActionsPerTick = Math.min(max, adaptiveActionsPerTick * 12 / 10);
        }

        if (previous != adaptiveActionsPerTick) {
            LOGGER.debug("[SCHEDULER] Adaptive rate: {} -> {}/tick ({} ms)", previous, adaptiveActionsPerTick, elapsedMs);
        }
    }

    private static void finishGeneration() {
        LOGGER.info("[SCHEDULER] City generation fully complete.");
        StructureCache.logStats();

        if (RorcikStructuresModConfig.unloadAfterGeneration) {
            StructureCache.clear();
        }

        active = false;
        phases.clear();
        chunkQueue.clear();
        pendingPreloads.set(0);
        StructureCache.setReadOnly(false);
    }

    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        chunkQueue.clear();
        phases.clear();
        currentPhaseIndex = 0;
        pendingPreloads.set(0);
        active = false;
        StructureCache.setReadOnly(false);
        StructureCache.clear();
    }
}