package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.buildings.SidewalkPlacer;
import ru.rorcik.structures.planning.CityZonePlanner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> CITY_CHUNK_TICKET = TicketType.create(
            "rorcik_city", Comparator.comparingLong(ChunkPos::toLong)
    );
    private static PriorityTaskQueue taskQueue;
    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final AtomicInteger totalTasksSubmitted = new AtomicInteger(0);
    private static final AtomicInteger totalTasksCompleted = new AtomicInteger(0);
    private static final Map<String, CityJournal> ACTIVE_JOURNALS = new ConcurrentHashMap<>();
    private static final Map<String, Set<Long>> CITY_CHUNKS = new ConcurrentHashMap<>();

    private static final int MAX_EXECUTION_RETRIES = 3;
    private static final int MAX_TICKETED_TASKS = 2;
    private static final Map<Long, Integer> CHUNK_TICKET_REFS = new HashMap<>();
    private static int tasksWithTickets = 0;

    public static synchronized void initialize() {
        if (taskQueue != null) return;
        taskQueue = new PriorityTaskQueue(GeneratorConfig.MAX_PENDING_TASKS);
        active.set(true);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Tick-driven scheduler initialized");
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active.get() || taskQueue == null) return;
        drainTasks();
    }

    private static void drainTasks() {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        int maxTasks = Math.max(1, GeneratorConfig.MAX_STRUCTURES_PER_TICK);
        long start = System.nanoTime();
        long deadline = start + GeneratorConfig.TARGET_TICK_TIME_MS * 1_000_000L;
        int[] ticketBudget = {Math.max(1, GeneratorConfig.CHUNKS_PER_TICK)};
        List<AnchorChunkTask> deferred = new ArrayList<>();
        int processed = 0;
        int examined = 0;

        while (processed < maxTasks && examined < maxTasks && System.nanoTime() < deadline) {
            AnchorChunkTask task = taskQueue.poll();
            if (task == null) break;
            examined++;

            boolean mayRequestTickets = !task.ticketedChunks().isEmpty()
                    || tasksWithTickets < MAX_TICKETED_TASKS;
            if (!ensureChunksReady(level, task, ticketBudget, mayRequestTickets)) {
                deferred.add(task);
                continue;
            }

            task.setState(AnchorChunkTask.State.IN_PROGRESS);
            precompileTemplates(task);
            executeTaskOnMainThread(task, level);
            processed++;
        }

        for (AnchorChunkTask task : deferred) {
            taskQueue.offer(task);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        if (RorcikStructuresModConfig.enableLogging
                && elapsedMs > GeneratorConfig.TARGET_TICK_TIME_MS * 2) {
            LOGGER.info("Info: [SCHEDULER] Tick spent {} ms on {} tasks ({} examined, {} deferred)",
                    elapsedMs, processed, examined, deferred.size());
        }
    }

    private static void precompileTemplates(AnchorChunkTask task) {
        Set<net.minecraft.resources.ResourceLocation> allTemplates = new HashSet<>();
        for (SlotWork sw : task.slotWorks()) {
            for (Phase phase : sw.phases()) {
                allTemplates.addAll(phase.templatesToPreload());
            }
        }
        for (net.minecraft.resources.ResourceLocation loc : allTemplates) {
            StructureCache.precompile(loc);
        }
    }

    private static boolean ensureChunksReady(ServerLevel level, AnchorChunkTask task,
                                             int[] ticketBudget, boolean mayRequestTickets) {
        boolean ready = true;
        for (long key : task.requiredChunks()) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;

            if (isChunkReady(level, chunkX, chunkZ)) continue;

            ready = false;
            if (mayRequestTickets && ticketBudget[0] > 0 && task.addTicketedChunk(chunkX, chunkZ)) {
                if (task.ticketedChunks().size() == 1) {
                    tasksWithTickets++;
                }
                acquireChunkTicket(level, key);
                ticketBudget[0]--;
            }
        }
        return ready;
    }

    private static void acquireChunkTicket(ServerLevel level, long chunkKey) {
        int refs = CHUNK_TICKET_REFS.getOrDefault(chunkKey, 0);
        CHUNK_TICKET_REFS.put(chunkKey, refs + 1);
        if (refs == 0) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            level.getChunkSource().addRegionTicket(CITY_CHUNK_TICKET, pos, 0, pos);
        }
    }

    private static void releaseChunkTicket(ServerLevel level, long chunkKey) {
        Integer refs = CHUNK_TICKET_REFS.get(chunkKey);
        if (refs == null) return;

        if (refs <= 1) {
            CHUNK_TICKET_REFS.remove(chunkKey);
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            level.getChunkSource().removeRegionTicket(CITY_CHUNK_TICKET, pos, 0, pos);
        } else {
            CHUNK_TICKET_REFS.put(chunkKey, refs - 1);
        }
    }

    private static boolean isChunkReady(ServerLevel level, int chunkX, int chunkZ) {
        // getChunkNow never waits for generation, unlike getChunk(FULL, false).
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    private static void releaseChunkTickets(ServerLevel level, AnchorChunkTask task) {
        if (task.ticketedChunks().isEmpty()) return;

        for (long key : task.ticketedChunks()) {
            releaseChunkTicket(level, key);
        }
        task.clearTicketedChunks();
        tasksWithTickets = Math.max(0, tasksWithTickets - 1);
    }

    public static boolean startCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level,
                                              CityGenerationContext context, int playerX, int playerZ) {
        if (!active.get()) initialize();

        CityJournal journal = ACTIVE_JOURNALS.get("city@" + city.centerX + "," + city.centerZ);
        if (journal == null) {
            journal = CityJournal.load(city.centerX, city.centerZ, city.typeId, level);
            ACTIVE_JOURNALS.put("city@" + city.centerX + "," + city.centerZ, journal);
        }

        if (journal.completionRatio() >= 1.0) {
            finalizeCity(level, journal, city.centerX, city.centerZ);
            return true;
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

        List<BuildingSlot> allSlots = new ArrayList<>();
        for (CityJournal.SlotData slotData : journal.getAllSlots()) {
            allSlots.add(slotData.toBuildingSlot());
        }

        int anchorChunkSize = context != null
                ? context.anchorChunkSize()
                : ("desert".equals(city.typeId) ? 2 : 4);
        Set<Long> cityChunks = CityZonePlanner.calculateCityChunks(
                allNodeMap, allStraightPositions, allSlots, anchorChunkSize, CityZonePlanner.CITY_CHUNK_BORDER
        );
        CITY_CHUNKS.put("city@" + city.centerX + "," + city.centerZ, cityChunks);

        PhaseBuilder phaseBuilder = new PhaseBuilder(
                level, roadNetwork, random, pendingSlots, baseY,
                allNodeMap, allStraightPositions, city.centerX, city.centerZ, journal, cityChunks
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

    private static void executeTaskOnMainThread(AnchorChunkTask task, ServerLevel level) {
        try {
            task.resetRetryCount();
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

            releaseChunkTickets(level, task);

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
                releaseChunkTickets(level, task);

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
            finalizeCity(level, journal, task.centerX(), task.centerZ());
        } else if (ratio >= 0.9 && RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] City at [{}, {}] is {}% complete, continuing generation",
                    task.centerX(), task.centerZ(), (int)(ratio * 100));
        }
    }

    private static void finalizeCity(ServerLevel level, CityJournal journal, int centerX, int centerZ) {
        Set<Long> cityChunks = CITY_CHUNKS.get("city@" + centerX + "," + centerZ);
        repairAllSidewalks(level, journal, cityChunks);

        journal.markCityCompleted();
        journal.delete();
        ACTIVE_JOURNALS.remove("city@" + centerX + "," + centerZ);
        CITY_CHUNKS.remove("city@" + centerX + "," + centerZ);

        CityGenerationSavedData.get(level).markComplete(centerX, centerZ);

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: [SCHEDULER] City generation at [{}, {}] is 100% complete, journal deleted",
                    centerX, centerZ);
        }
    }

    /**
     * Sidewalks are placed together with their house, but later buildings and
     * roads can overwrite them. Instead of re-placing sidewalks after every
     * anchor task (which rewrote the same blocks many times), do one repair
     * pass when the whole city is finished.
     */
    private static void repairAllSidewalks(ServerLevel level, CityJournal journal, Set<Long> cityChunks) {
        if (cityChunks == null) return;

        for (CityJournal.SlotData slotData : journal.getAllSlots()) {
            if (!"houses".equals(slotData.category) || !slotData.isCompleted() || !slotData.isHousePlaced()) continue;

            BuildingSlot slot = slotData.toBuildingSlot();
            try {
                long seed = level.getSeed() ^ slot.position().hashCode();
                SidewalkPlacer.placeSidewalkForSlot(level, slot, RandomSource.create(seed), cityChunks);
            } catch (Exception e) {
                LOGGER.error("Error: [SCHEDULER] Failed to repair sidewalk for slot {}: {}",
                        slot.position().toShortString(), e.getMessage(), e);
            }
        }
    }

    public static boolean pauseCityGeneration(PlayerProximityTracker.CityInfo city, ServerLevel level) {
        if (taskQueue == null) return false;
        List<AnchorChunkTask> removed = taskQueue.removeTasksForCity(city.centerX, city.centerZ);
        for (AnchorChunkTask task : removed) {
            releaseChunkTickets(level, task);
        }
        ACTIVE_JOURNALS.remove("city@" + city.centerX + "," + city.centerZ);
        CITY_CHUNKS.remove("city@" + city.centerX + "," + city.centerZ);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SCHEDULER] Paused city '{}' — removed {} pending tasks from queue. Progress saved in journal.",
                    city.cityId, removed.size());
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

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.overworld() : null;
        if (taskQueue != null) {
            if (level != null) {
                AnchorChunkTask task;
                while ((task = taskQueue.poll()) != null) {
                    releaseChunkTickets(level, task);
                }
            }
            taskQueue.clear();
            taskQueue = null;
        }
        if (level != null) {
            for (long key : new ArrayList<>(CHUNK_TICKET_REFS.keySet())) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                level.getChunkSource().removeRegionTicket(CITY_CHUNK_TICKET, pos, 0, pos);
            }
        }
        CHUNK_TICKET_REFS.clear();
        tasksWithTickets = 0;
        ACTIVE_JOURNALS.clear();
        CITY_CHUNKS.clear();
    }

    public static void reset() {
        shutdown();
        totalTasksSubmitted.set(0);
        totalTasksCompleted.set(0);
    }
}