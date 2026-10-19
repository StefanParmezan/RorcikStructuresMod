package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CityBuildScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ExecutorService dispatcherPool;
    private static PriorityTaskQueue taskQueue;
    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final AtomicInteger totalTasksSubmitted = new AtomicInteger(0);
    private static final AtomicInteger totalTasksCompleted = new AtomicInteger(0);

    public static synchronized void initialize() {
        if (dispatcherPool != null) return;
        dispatcherPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CityDispatcher");
            t.setDaemon(true);
            return t;
        });
        taskQueue = new PriorityTaskQueue(GeneratorConfig.MAX_PENDING_TASKS);
        active.set(true);

        dispatcherPool.submit(() -> {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [SCHEDULER] Dispatcher started");
            }
            while (active.get()) {
                try {
                    AnchorChunkTask task = taskQueue.poll();
                    if (task == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    processTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error: [SCHEDULER] Dispatcher error", e);
                }
            }
        });
    }

    public static void startCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level,
                                           CityGenerationContext context, int playerX, int playerZ) {
        if (!active.get()) initialize();
        int baseY = context.baseY();
        RandomSource random = RandomSource.create(context.seed());

        PhaseBuilder phaseBuilder = new PhaseBuilder(
                level,
                context.roadNetwork(),
                random,
                context.slots(),
                baseY,
                context.nodeMap(),
                context.straightPositions()
        );

        List<AnchorChunkTask> tasks = ChunkGrouper.groupIntoAnchorChunks(
                context.nodeMap(), context.straightPositions(), context.slots(), phaseBuilder);

        for (AnchorChunkTask task : tasks) {
            task.updatePriority(playerX, playerZ);
            submitTask(task);
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Submitted {} anchor chunk tasks for city '{}'", tasks.size(), city.cityId);
        }
    }

    public static boolean submitTask(AnchorChunkTask task) {
        if (taskQueue == null) initialize();
        boolean added = taskQueue.offer(task);
        if (added) totalTasksSubmitted.incrementAndGet();
        return added;
    }

    public static void updatePriorities(int playerBlockX, int playerBlockZ) {
        if (taskQueue != null) taskQueue.updatePriorities(playerBlockX, playerBlockZ);
    }

    private static void processTask(AnchorChunkTask task) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!areChunksLoaded(level, task)) {
            taskQueue.offer(task);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        task.setState(AnchorChunkTask.State.IN_PROGRESS);

        Set<net.minecraft.resources.ResourceLocation> allTemplates = new HashSet<>();
        for (SlotWork sw : task.slotWorks()) {
            for (Phase phase : sw.phases()) {
                allTemplates.addAll(phase.templatesToPreload());
            }
        }
        for (net.minecraft.resources.ResourceLocation loc : allTemplates) {
            StructureCache.precompile(loc);
        }

        level.getServer().execute(() -> executeTaskOnMainThread(task, level));
    }

    private static boolean areChunksLoaded(ServerLevel level, AnchorChunkTask task) {
        int anchorCX = task.anchorChunkX();
        int anchorCZ = task.anchorChunkZ();
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                if (!level.hasChunk(anchorCX + dx, anchorCZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void executeTaskOnMainThread(AnchorChunkTask task, ServerLevel level) {
        try {
            Map<PhaseType, List<Runnable>> phasedActions = new TreeMap<>(Comparator.comparing(PhaseType::order));
            for (SlotWork slotWork : task.slotWorks()) {
                for (Phase phase : slotWork.phases()) {
                    phasedActions.computeIfAbsent(phase.type(), k -> new ArrayList<>()).add(phase.action());
                }
            }
            for (Map.Entry<PhaseType, List<Runnable>> entry : phasedActions.entrySet()) {
                for (Runnable action : entry.getValue()) {
                    action.run();
                }
            }
            task.setState(AnchorChunkTask.State.COMPLETED);
            totalTasksCompleted.incrementAndGet();

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [SCHEDULER] Completed task {}", task);
            }
        } catch (Exception e) {
            LOGGER.error("Error: [SCHEDULER] Main thread failed for task {}", task, e);
            task.setState(AnchorChunkTask.State.FAILED);
        }
    }

    public static void pauseCityGeneration(PlayerProximityTracker.CityInfo city) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Paused city '{}'", city.cityId);
        }
    }

    public static void resumeCityGeneration(PlayerProximityTracker.CityInfo city) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Resumed city '{}'", city.cityId);
        }
    }

    public static synchronized void shutdown() {
        active.set(false);
        if (dispatcherPool != null) {
            dispatcherPool.shutdown();
            dispatcherPool = null;
        }
        if (taskQueue != null) {
            taskQueue.clear();
            taskQueue = null;
        }
    }

    public static void reset() {
        shutdown();
        totalTasksSubmitted.set(0);
        totalTasksCompleted.set(0);
    }
}