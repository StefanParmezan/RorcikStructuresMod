// FILE: ./main/java/ru/rorcik/structures/generation/city/plains/cities/CityBuildScheduler.java
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

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Deque<ChunkLoadTask> chunkQueue = new ArrayDeque<>();
    private static final Deque<BuildTask> buildQueue = new ArrayDeque<>();
    private static final Deque<ResourceLocation> preloadQueue = new ArrayDeque<>();
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

    // Скорость прелоада: 6/тик вместе с чанками, 15/тик когда чанки уже загружены
    private static final int PRELOAD_PER_TICK_DURING_CHUNKS = 6;
    private static final int PRELOAD_PER_TICK_BEFORE_BUILD = 15;

    private static long lastLogTimeMs = 0;
    private static final long LOG_INTERVAL_MS = 1000;
    private static int preloadedCount = 0;
    private static int totalPreload = 0;

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

    public static void scheduleChunkLoading(Set<Long> chunkKeys, Runnable whenReady) {
        chunkQueue.clear();
        buildQueue.clear();
        // НЕ очищаем preloadQueue — шаблоны дорог уже могут быть в очереди!

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

        LOGGER.info("[SCHEDULER] Queued {} chunks for loading ({} per tick)",
                totalChunks, GeneratorConfig.CHUNKS_PER_TICK);
    }

    public static void queuePreload(Collection<ResourceLocation> templates) {
        preloadQueue.addAll(templates);
        totalPreload += templates.size();
        if (!templates.isEmpty()) {
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

        // ФАЗА 1: Загрузка чанков + фоновый прелоад дорог
        if (!chunkQueue.isEmpty()) {
            processChunkLoading(level);
            processPreloadQueue(PRELOAD_PER_TICK_DURING_CHUNKS);
        }
        // ФАЗА 2: Чанки загружены, запускаем аллокацию слотов (если еще не запущена)
        else if (onChunksReady != null) {
            if (preloadQueue.isEmpty()) {
                long elapsed = System.currentTimeMillis() - chunkLoadStartTime;
                LOGGER.info("[SCHEDULER] All {} chunks loaded in {} ms, preloaded {} templates, starting allocation phase",
                        totalChunks, elapsed, preloadedCount);
                Runnable callback = onChunksReady;
                onChunksReady = null;
                callback.run(); // Здесь вызовется queuePreload для зданий
            } else {
                processPreloadQueue(PRELOAD_PER_TICK_DURING_CHUNKS);
            }
        }
        // ФАЗА 3: Прелоад шаблонов зданий ПЕРЕД началом строительства (БЕЗ стройки!)
        else if (!preloadQueue.isEmpty()) {
            processPreloadQueue(PRELOAD_PER_TICK_BEFORE_BUILD);

            long now = System.currentTimeMillis();
            if (now - lastLogTimeMs >= LOG_INTERVAL_MS || preloadQueue.size() < PRELOAD_PER_TICK_BEFORE_BUILD) {
                LOGGER.info("[SCHEDULER] Preloading building templates... {} remaining in queue", preloadQueue.size());
                lastLogTimeMs = now;
            }
        }
        // ФАЗА 4: Строительство (кэш уже полностью готов, промахов не будет)
        else if (!buildQueue.isEmpty()) {
            processBuildActions();
        }
        // ФАЗА 5: Завершение
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

    private static void processBuildActions() {
        long startTime = System.currentTimeMillis();
        int processed = 0;

        while (!buildQueue.isEmpty() && processed < adaptiveStructuresPerTick) {
            BuildTask task = buildQueue.poll();
            if (task != null) {
                try {
                    task.action().run();
                    completedBuildActions++;
                } catch (Exception e) {
                    LOGGER.error("[SCHEDULER] Build action failed", e);
                }
                processed++;
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
        preloadQueue.clear();
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
    }
}