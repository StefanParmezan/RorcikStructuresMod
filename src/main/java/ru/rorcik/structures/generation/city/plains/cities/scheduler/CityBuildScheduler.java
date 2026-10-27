package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
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
    private static final Map<String, CityJournal> ACTIVE_JOURNALS = new ConcurrentHashMap<>();

    private static final int MAX_CHUNK_RETRIES = 15;
    private static final int MAX_EXECUTION_RETRIES = 3;

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
                        continue;
                    }
                    processTask(task);
                } catch (Exception e) {
                    LOGGER.error("Error: [SCHEDULER] Dispatcher error", e);
                }
            }
        });
    }

    public static boolean startCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level,
                                              CityGenerationContext context, int playerX, int playerZ) {
        if (!active.get()) initialize();

        CityJournal journal = ACTIVE_JOURNALS.get(city.cityId);
        if (journal == null) {
            journal = CityJournal.load(city.centerX, city.centerZ, city.typeId, level);
            ACTIVE_JOURNALS.put(city.cityId, journal);
        }

        if (journal.getSeed() == 0) {
            if (context != null) {
                LOGGER.warn("Warn: [SCHEDULER] Journal for city '{}' has no seed. Rebuilding from context...", city.cityId);
                journal.rebuildFromContext(context);
                journal.save();
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Info: [SCHEDULER] Journal rebuilt and persisted for city '{}' ({} total entries)",
                            city.cityId, journal.getTotalSlots());
                }
            } else {
                LOGGER.error("Error: [SCHEDULER] Journal for city '{}' not initialized (seed=0) and no context available", city.cityId);
                return false;
            }
        }

        RoadNetwork roadNetwork = journal.restoreRoadNetwork();

        Map<BlockPos, RoadNode> allNodeMap = new HashMap<>();
        for (CityJournal.RoadNodeData nodeData : journal.getAllRoadNodes().values()) {
            RoadNode node = nodeData.toRoadNode();
            allNodeMap.put(node.position(), node);
        }

        Set<BlockPos> allStraightPositions = new HashSet<>();
        for (CityJournal.StraightRoadData roadData : journal.getAllStraightRoads().values()) {
            allStraightPositions.add(roadData.toBlockPos());
        }

        Map<BlockPos, RoadNode> pendingNodeMap = new HashMap<>();
        for (CityJournal.RoadNodeData nodeData : journal.getPendingRoadNodes()) {
            RoadNode node = nodeData.toRoadNode();
            pendingNodeMap.put(node.position(), node);
        }

        Set<BlockPos> pendingStraightPositions = new HashSet<>();
        for (CityJournal.StraightRoadData roadData : journal.getPendingStraightRoads()) {
            pendingStraightPositions.add(roadData.toBlockPos());
        }

        List<BuildingSlot> pendingSlots = new ArrayList<>();
        for (CityJournal.SlotData slotData : journal.getPendingSlots()) {
            pendingSlots.add(slotData.toBuildingSlot());
        }

        int baseY = journal.getBaseY();
        RandomSource random = RandomSource.create(journal.getSeed());

        PhaseBuilder phaseBuilder = new PhaseBuilder(
                level, roadNetwork, random, pendingSlots, baseY,
                allNodeMap, allStraightPositions, city.centerX, city.centerZ, journal
        );

        List<AnchorChunkTask> allTasks = ChunkGrouper.groupIntoAnchorChunks(
                pendingNodeMap, pendingStraightPositions, pendingSlots, phaseBuilder, city.centerX, city.centerZ, context.anchorChunkSize());

        List<AnchorChunkTask> tasks = new ArrayList<>();
        for (AnchorChunkTask task : allTasks) {
            task.updatePriority(playerX, playerZ);
            tasks.add(task);
        }

        int submitted = 0;
        for (AnchorChunkTask task : tasks) {
            if (submitTask(task)) {
                submitted++;
            }
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Submitted {} tasks for city '{}' (from journal: {} total, {} pending slots, {} pending road nodes, {} pending straight roads)",
                    submitted, city.cityId, journal.getTotalSlots(), pendingSlots.size(), pendingNodeMap.size(), pendingStraightPositions.size());
        }
        return submitted > 0;
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
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!areChunksFullyGenerated(level, task)) {
            int retries = task.incrementRetryCount();

            if (retries > MAX_CHUNK_RETRIES) {
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Info: [SCHEDULER] Deferring task {} to journal (chunks not loading). Will retry later.", task);
                }
                CityJournal journal = ACTIVE_JOURNALS.get(task.cityId());
                if (journal != null) {
                    for (SlotWork slotWork : task.slotWorks()) {
                        String slotKey = slotWork.slotId();
                        String[] parts = slotKey.split(":");
                        if (parts.length == 2) {
                            String[] anchorParts = parts[0].split(",");
                            String[] posParts = parts[1].split(",");
                            if (anchorParts.length == 2 && posParts.length == 3) {
                                int anchorChunkX = Integer.parseInt(anchorParts[0]);
                                int anchorChunkZ = Integer.parseInt(anchorParts[1]);
                                int posX = Integer.parseInt(posParts[0]);
                                int posY = Integer.parseInt(posParts[1]);
                                int posZ = Integer.parseInt(posParts[2]);
                                if ("BUILDING".equals(slotWork.slotType())) {
                                    journal.markSlotDeferred(anchorChunkX, anchorChunkZ, posX, posY, posZ);
                                }
                            }
                        }
                    }
                }

                task.resetRetryCount();
                taskQueue.offer(task);
                return;
            }

            taskQueue.offer(task);
            return;
        }

        task.resetRetryCount();
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

    private static boolean areChunksFullyGenerated(ServerLevel level, AnchorChunkTask task) {
        int anchorCX = task.anchorChunkX();
        int anchorCZ = task.anchorChunkZ();
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int chunkX = anchorCX + dx;
                int chunkZ = anchorCZ + dz;
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                if (chunk == null || !chunk.getStatus().isOrAfter(ChunkStatus.FULL)) {
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
                    task.incrementCompletedPhases();
                }
            }

            task.setState(AnchorChunkTask.State.COMPLETED);
            totalTasksCompleted.incrementAndGet();

            CityJournal journal = ACTIVE_JOURNALS.get(task.cityId());
            if (journal != null) {
                for (SlotWork slotWork : task.slotWorks()) {
                    String slotKey = slotWork.slotId();
                    String slotType = slotWork.slotType();
                    String[] parts = slotKey.split(":");
                    if (parts.length == 2) {
                        String[] anchorParts = parts[0].split(",");
                        String[] posParts = parts[1].split(",");
                        if (anchorParts.length == 2 && posParts.length == 3) {
                            int anchorChunkX = Integer.parseInt(anchorParts[0]);
                            int anchorChunkZ = Integer.parseInt(anchorParts[1]);
                            int posX = Integer.parseInt(posParts[0]);
                            int posY = Integer.parseInt(posParts[1]);
                            int posZ = Integer.parseInt(posParts[2]);

                            if ("BUILDING".equals(slotType)) {
                                journal.markSlotCompleted(anchorChunkX, anchorChunkZ, posX, posY, posZ);
                            } else if ("ROAD_NODE".equals(slotType)) {
                                journal.markRoadNodeCompleted(anchorChunkX, anchorChunkZ, posX, posY, posZ);
                            } else if ("STRAIGHT_ROAD".equals(slotType)) {
                                journal.markStraightRoadCompleted(anchorChunkX, anchorChunkZ, posX, posY, posZ);
                            }
                        }
                    }
                }
            }

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [SCHEDULER] Completed task {} (Phases: {}/{})",
                        task, task.completedPhases(), task.totalPhases());
            }

            checkCityCompletion(level, task);

        } catch (Exception e) {
            int retries = task.incrementRetryCount();
            if (retries < MAX_EXECUTION_RETRIES) {
                LOGGER.warn("Warn: [SCHEDULER] Task {} failed (attempt {}/{}). Re-queuing. Reason: {}",
                        task, retries, MAX_EXECUTION_RETRIES, e.getMessage());
                task.setState(AnchorChunkTask.State.PENDING);
                taskQueue.offer(task);
            } else {
                LOGGER.error("Error: [SCHEDULER] Task {} FAILED permanently after {} attempts. Marking slots as FAILED in journal. Reason: {}",
                        task, MAX_EXECUTION_RETRIES, e.getMessage(), e);
                task.setState(AnchorChunkTask.State.FAILED);

                CityJournal journal = ACTIVE_JOURNALS.get(task.cityId());
                if (journal != null) {
                    for (SlotWork slotWork : task.slotWorks()) {
                        String slotKey = slotWork.slotId();
                        String slotType = slotWork.slotType();
                        String[] parts = slotKey.split(":");
                        if (parts.length == 2) {
                            String[] anchorParts = parts[0].split(",");
                            String[] posParts = parts[1].split(",");
                            if (anchorParts.length == 2 && posParts.length == 3) {
                                int anchorChunkX = Integer.parseInt(anchorParts[0]);
                                int anchorChunkZ = Integer.parseInt(anchorParts[1]);
                                int posX = Integer.parseInt(posParts[0]);
                                int posY = Integer.parseInt(posParts[1]);
                                int posZ = Integer.parseInt(posParts[2]);

                                if ("BUILDING".equals(slotType)) {
                                    journal.markSlotFailed(anchorChunkX, anchorChunkZ, posX, posY, posZ);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void checkCityCompletion(ServerLevel level, AnchorChunkTask task) {
        if (taskQueue == null) return;

        CityJournal journal = ACTIVE_JOURNALS.get(task.cityId());
        if (journal == null) return;

        double ratio = journal.completionRatio();

        if (ratio >= 1.0) {
            journal.markCityCompleted();
            journal.delete();
            ACTIVE_JOURNALS.remove(task.cityId());

            CityGenerationSavedData.get(level).markComplete(task.centerX(), task.centerZ());

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [SCHEDULER] City generation at [{}, {}] is 100% complete, journal deleted",
                        task.centerX(), task.centerZ());
            }
        } else if (ratio >= 0.9 && RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] City at [{}, {}] is {}% complete, continuing generation",
                    task.centerX(), task.centerZ(), (int)(ratio * 100));
        }
    }

    public static boolean pauseCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level) {
        if (taskQueue == null) return false;
        int removed = taskQueue.removeTasksForCity(city.centerX, city.centerZ);
        ACTIVE_JOURNALS.remove(city.cityId);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Paused city '{}' — removed {} pending tasks from queue. Progress saved in journal.",
                    city.cityId, removed);
        }
        return true;
    }

    public static boolean resumeCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level, int playerX, int playerZ) {
        CityGenerationContext context = CityGenerationContextRegistry.get(city.worldId, city.centerX, city.centerZ);
        boolean contextRebuilt = false;

        if (context == null) {
            context = ru.rorcik.structures.planning.CityZonePlanner.rebuildContext(level, city.centerX, city.centerZ, city.typeId);
            contextRebuilt = true;
        }

        if (context == null) {
            LOGGER.error("Error: [SCHEDULER] Cannot resume city '{}' — no context available", city.cityId);
            return false;
        }

        if (RorcikStructuresModConfig.enableLogging) {
            if (contextRebuilt) {
                LOGGER.info("Info: [SCHEDULER] Resuming city '{}' (type: {}) — context REBUILT, restoring tasks", city.cityId, city.typeId);
            } else {
                LOGGER.info("Info: [SCHEDULER] Resuming city '{}' (type: {}) — context from registry, restoring tasks", city.cityId, city.typeId);
            }
        }
        return startCityGeneration(city, level, context, playerX, playerZ);
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
        ACTIVE_JOURNALS.clear();
    }

    public static void reset() {
        shutdown();
        totalTasksSubmitted.set(0);
        totalTasksCompleted.set(0);
    }
}