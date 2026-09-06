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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private static final Deque<BuildTask> buildQueue = new ArrayDeque<>();

    private static final AtomicInteger pendingPreloads = new AtomicInteger(0);

    private static Runnable onChunksReady = null;
    private static boolean active = false;

    private static int chunkReadyCooldownTicks = 0;
    private static final int CHUNK_READY_DELAY_TICKS = 2;

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

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

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
        chunkReadyCooldownTicks = 0;

        adaptiveStructuresPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
        totalBuildTimeMs = 0;
        adaptiveTickCount = 0;
        warmupTicks = 0;

        LOGGER.info("[SCHEDULER] Queued {} chunks for loading ({} per tick)",
                totalChunks, GeneratorConfig.CHUNKS_PER_TICK);
    }

    public static void queuePreload(Collection<ResourceLocation> templates) {
        if (templates.isEmpty()) return;

        int added = 0;
        for (ResourceLocation loc : templates) {
            PRELOAD_EXECUTOR.submit(() -> {
                try {
                    StructureCache.precompile(loc);
                    preloadedCount++;
                } catch (Exception e) {
                    LOGGER.error("[SCHEDULER] Failed to precompile template in background: {}", loc, e);
                } finally {
                    pendingPreloads.decrementAndGet();
                }
            });
            added++;
        }
        pendingPreloads.addAndGet(added);
        totalPreload += added;

        LOGGER.info("[SCHEDULER] Queued {} templates for background preloading (pending: {})",
                added, pendingPreloads.get());
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

        LOGGER.info("[SCHEDULER] Queued {} build actions (adaptive, starting at {} per tick)",
                totalBuildActions, adaptiveStructuresPerTick);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!chunkQueue.isEmpty()) {
            processChunkLoading(level);
        }

        else if (onChunksReady != null) {
            if (chunkReadyCooldownTicks < CHUNK_READY_DELAY_TICKS) {
                chunkReadyCooldownTicks++;
            } else {
                long elapsed = System.currentTimeMillis() - chunkLoadStartTime;
                LOGGER.info("[SCHEDULER] Chunks loaded and stabilized in {} ms. Preloaded: {}/{}",
                        elapsed, preloadedCount, totalPreload);
                Runnable callback = onChunksReady;
                onChunksReady = null;
                callback.run();
            }
        }
        else if (pendingPreloads.get() > 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogTimeMs >= LOG_INTERVAL_MS) {
                LOGGER.info("[SCHEDULER] Waiting for background preloading... {} remaining", pendingPreloads.get());
                lastLogTimeMs = now;
            }
        }
        else if (!buildQueue.isEmpty()) {
            if (!readOnlySet) {
                StructureCache.setReadOnly(true);
                readOnlySet = true;
                LOGGER.info("[SCHEDULER] Entered READ-ONLY cache mode. On-demand compilation is now DISABLED.");
            }
            processBuildActions();
        }

        else {
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
                int percent = (loadedChunks * 100) / totalChunks;
                LOGGER.info("[SCHEDULER] Chunks: {}/{} ({}%)", loadedChunks, totalChunks, percent);
                lastLogTimeMs = now;
            }
        }
    }

    private static void processBuildActions() {
        long startTime = System.currentTimeMillis();
        int processed = 0;
        long targetTickTime = GeneratorConfig.TARGET_TICK_TIME_MS;

        while (!buildQueue.isEmpty() && processed < adaptiveStructuresPerTick) {
            BuildTask task = buildQueue.peek();
            if (task == null) break;

            long actionStart = System.currentTimeMillis();
            try {
                task.action().run();
                completedBuildActions++;
                buildQueue.poll();
            } catch (Exception e) {
                LOGGER.error("[SCHEDULER] Build action failed, skipping", e);
                buildQueue.poll();
            }
            processed++;

            if (System.currentTimeMillis() - startTime > targetTickTime && processed >= GeneratorConfig.MIN_STRUCTURES_PER_TICK) {
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        totalBuildTimeMs += elapsed;
        adaptiveTickCount++;
        warmupTicks++;

        if (warmupTicks > WARMUP_TICKS) {
            adjustStructuresPerTick(elapsed);
        }

        long now = System.currentTimeMillis();
        if (buildQueue.isEmpty() || completedBuildActions % 50 == 0 || (now - lastLogTimeMs >= LOG_INTERVAL_MS)) {
            LOGGER.info("[SCHEDULER] Build progress: {}/{} structures (rate: {}/tick, tick time: {} ms)",
                    completedBuildActions, totalBuildActions, adaptiveStructuresPerTick, elapsed);
            lastLogTimeMs = now;
        }
    }

    private static void finishGeneration() {
        long elapsed = System.currentTimeMillis() - buildStartTime;
        LOGGER.info("[SCHEDULER] City generation complete: {} structures in {} ms ({} per second)",
                completedBuildActions, elapsed,
                elapsed > 0 ? (completedBuildActions * 1000L) / elapsed : 0);

        if (adaptiveTickCount > 0) {
            LOGGER.info("[SCHEDULER] Adaptive stats: {} ticks, final rate: {} structures/tick, avg time per tick: {} ms",
                    adaptiveTickCount, adaptiveStructuresPerTick, totalBuildTimeMs / adaptiveTickCount);
        }

        active = false;
        StructureCache.setReadOnly(false);
        readOnlySet = false;
        StructureCache.logStats();

        if (RorcikStructuresModConfig.unloadAfterGeneration) {
            StructureCache.unloadCompiledTemplates();
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

        if (previous != adaptiveStructuresPerTick) {
            LOGGER.debug("[SCHEDULER] Adaptive rate: {} -> {} structures/tick (tick took {} ms)",
                    previous, adaptiveStructuresPerTick, elapsedMs);
        }
    }

    public static boolean isActive() { return active; }
    public static int getCurrentStructuresPerTick() { return adaptiveStructuresPerTick; }

    public static void reset() {
        chunkQueue.clear();
        buildQueue.clear();
        onChunksReady = null;
        active = false;
        totalBuildActions = 0;
        completedBuildActions = 0;
        totalChunks = 0;
        loadedChunks = 0;
        preloadedCount = 0;
        totalPreload = 0;
        pendingPreloads.set(0);
        chunkReadyCooldownTicks = 0;

        adaptiveStructuresPerTick = GeneratorConfig.INITIAL_STRUCTURES_PER_TICK;
        totalBuildTimeMs = 0;
        adaptiveTickCount = 0;
        warmupTicks = 0;
        lastLogTimeMs = 0;
        readOnlySet = false;
        StructureCache.setReadOnly(false);
    }
}