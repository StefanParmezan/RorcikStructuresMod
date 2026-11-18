package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CityJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JOURNALS_SUBDIR = "rorcik_journals";

    private final int centerX;
    private final int centerZ;
    private final String worldId;
    private final String typeId;
    private final Path journalPath;

    private final Map<String, SlotData> slots = new ConcurrentHashMap<>();
    private final Map<String, RoadNodeData> roadNodes = new ConcurrentHashMap<>();
    private final Map<String, StraightRoadData> straightRoads = new ConcurrentHashMap<>();
    private RoadNetworkData roadNetworkData;

    private long seed;
    private int baseY;
    private int targetHeight;
    private String cityStatus;

    public enum SlotStatus { PLANNED, PROCESSING, COMPLETED, FAILED, DEFERRED }
    public enum CityStatus { PLANNING, GENERATING, COMPLETED }

    public static class SlotData {
        public int anchorChunkX;
        public int anchorChunkZ;
        public String category;
        public String buildingName;
        public int positionX;
        public int positionY;
        public int positionZ;
        public String rotation;
        public String roadSide;
        public int maxDepth;
        public String currentPhase;
        public String status;
        public boolean housePlaced;

        public SlotData() {}

        public SlotData(int anchorChunkX, int anchorChunkZ, BuildingSlot slot) {
            this.anchorChunkX = anchorChunkX;
            this.anchorChunkZ = anchorChunkZ;
            this.category = slot.category();
            this.buildingName = slot.buildingName();
            this.positionX = slot.position().getX();
            this.positionY = slot.position().getY();
            this.positionZ = slot.position().getZ();
            this.rotation = slot.rotation().name();
            this.roadSide = slot.roadSide() != null ? slot.roadSide().name() : null;
            this.maxDepth = slot.maxDepth();
            this.currentPhase = "PLANNED";
            this.status = SlotStatus.PLANNED.name();
        }

        public String getKey() {
            return anchorChunkX + "," + anchorChunkZ + ":" + category + ":" + positionX + "," + positionY + "," + positionZ;
        }

        public boolean isPending() {
            return SlotStatus.PLANNED.name().equals(status) || SlotStatus.DEFERRED.name().equals(status);
        }

        public boolean isCompleted() {
            return SlotStatus.COMPLETED.name().equals(status);
        }

        public boolean isHousePlaced() {
            return housePlaced;
        }

        public BuildingSlot toBuildingSlot() {
            return new BuildingSlot(
                    new BlockPos(positionX, positionY, positionZ),
                    Rotation.valueOf(rotation),
                    roadSide != null ? Direction.valueOf(roadSide) : null,
                    category,
                    buildingName,
                    maxDepth
            );
        }
    }

    public static class RoadNodeData {
        public int anchorChunkX;
        public int anchorChunkZ;
        public int positionX;
        public int positionY;
        public int positionZ;
        public List<String> entries;
        public List<String> exitDirections;
        public int depth;
        public boolean isTerminal;
        public String status;

        public RoadNodeData() {}

        public RoadNodeData(int anchorChunkX, int anchorChunkZ, RoadNode node) {
            this.anchorChunkX = anchorChunkX;
            this.anchorChunkZ = anchorChunkZ;
            this.positionX = node.position().getX();
            this.positionY = node.position().getY();
            this.positionZ = node.position().getZ();

            List<Direction> nodeEntries = node.getEntries();
            this.entries = nodeEntries != null
                    ? nodeEntries.stream().map(Direction::name).collect(Collectors.toList())
                    : new ArrayList<>();

            List<Direction> nodeExits = node.getExitDirections();
            this.exitDirections = nodeExits != null
                    ? nodeExits.stream().map(Direction::name).collect(Collectors.toList())
                    : new ArrayList<>();

            this.depth = node.depth();
            this.isTerminal = node.isTerminal();
            this.status = SlotStatus.PLANNED.name();
        }

        public String getKey() {
            return anchorChunkX + "," + anchorChunkZ + ":" + positionX + "," + positionY + "," + positionZ;
        }

        public boolean isPending() {
            return SlotStatus.PLANNED.name().equals(status) || SlotStatus.DEFERRED.name().equals(status);
        }

        public RoadNode toRoadNode() {
            RoadNode node = new RoadNode(new BlockPos(positionX, positionY, positionZ), null, depth);

            if (entries != null) {
                for (String entry : entries) {
                    try {
                        node.addEntryDirect(Direction.valueOf(entry));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Warn: [JOURNAL] Invalid direction in entries: {}", entry);
                    }
                }
            }

            if (exitDirections != null) {
                for (String exit : exitDirections) {
                    try {
                        node.addExit(Direction.valueOf(exit), null);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Warn: [JOURNAL] Invalid direction in exitDirections: {}", exit);
                    }
                }
            }

            if (isTerminal) {
                node.markTerminal();
            }
            return node;
        }
    }

    public static class StraightRoadData {
        public int anchorChunkX;
        public int anchorChunkZ;
        public int positionX;
        public int positionY;
        public int positionZ;
        public String status;

        public StraightRoadData() {}

        public StraightRoadData(int anchorChunkX, int anchorChunkZ, BlockPos pos) {
            this.anchorChunkX = anchorChunkX;
            this.anchorChunkZ = anchorChunkZ;
            this.positionX = pos.getX();
            this.positionY = pos.getY();
            this.positionZ = pos.getZ();
            this.status = SlotStatus.PLANNED.name();
        }

        public String getKey() {
            return anchorChunkX + "," + anchorChunkZ + ":" + positionX + "," + positionY + "," + positionZ;
        }

        public boolean isPending() {
            return SlotStatus.PLANNED.name().equals(status) || SlotStatus.DEFERRED.name().equals(status);
        }

        public BlockPos toBlockPos() {
            return new BlockPos(positionX, positionY, positionZ);
        }
    }

    public static class RoadNetworkData {
        public List<RoadSegmentData> segments = new ArrayList<>();
        public List<Integer> availableStopVariants = new ArrayList<>();
        public int straightRoadCount = 0;
    }

    public static class RoadSegmentData {
        public String type;
        public int x, y, z;
        public String rotation;
        public int decorVariant;
        public List<String> connections;

        public RoadSegmentData() {}

        public RoadSegmentData(RoadSegment segment) {
            this.type = segment.type();
            this.x = segment.position().getX();
            this.y = segment.position().getY();
            this.z = segment.position().getZ();
            this.rotation = segment.rotation().name();
            this.decorVariant = segment.decorVariant();
            this.connections = segment.connections().stream()
                    .map(Direction::name)
                    .collect(Collectors.toList());
        }

        public RoadSegment toRoadSegment() {
            List<Direction> dirs = connections.stream()
                    .map(Direction::valueOf)
                    .collect(Collectors.toList());
            return new RoadSegment(type, new BlockPos(x, y, z), Rotation.valueOf(rotation), decorVariant, dirs);
        }
    }

    public static class JournalData {
        public int centerX;
        public int centerZ;
        public String worldId;
        public String typeId;
        public long seed;
        public int baseY;
        public int targetHeight;
        public String cityStatus;
        public List<SlotData> slots;
        public List<RoadNodeData> roadNodes;
        public List<StraightRoadData> straightRoads;
        public RoadNetworkData roadNetworkData;
    }

    public CityJournal(int centerX, int centerZ, String worldId, String typeId, Path journalPath) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.worldId = worldId;
        this.typeId = typeId;
        this.journalPath = journalPath;
        this.cityStatus = CityStatus.PLANNING.name();
    }

    public static String getWorldId(ServerLevel level) {
        Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }

    public static Path getJournalsDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(JOURNALS_SUBDIR);
    }

    public static CityJournal load(int centerX, int centerZ, String typeId, ServerLevel level) {
        String worldId = getWorldId(level);
        Path journalsDir = getJournalsDir(level);
        Path journalPath = journalsDir.resolve(String.format("city_%s_%s@%d_%d.json", worldId, typeId, centerX, centerZ));

        CityJournal journal = new CityJournal(centerX, centerZ, worldId, typeId, journalPath);

        if (!Files.exists(journalPath)) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] No journal found for {} city [{}, {}] in world {}, creating new", typeId, centerX, centerZ, worldId);
            }
            return journal;
        }

        try {
            String json = Files.readString(journalPath);
            JournalData data = GSON.fromJson(json, JournalData.class);

            journal.seed = data.seed;
            journal.baseY = data.baseY;
            journal.targetHeight = data.targetHeight;
            journal.cityStatus = data.cityStatus != null ? data.cityStatus : CityStatus.PLANNING.name();
            journal.roadNetworkData = data.roadNetworkData;

            if (data.slots != null) {
                for (SlotData slot : data.slots) {
                    journal.slots.put(slot.getKey(), slot);
                }
            }
            if (data.roadNodes != null) {
                for (RoadNodeData node : data.roadNodes) {
                    journal.roadNodes.put(node.getKey(), node);
                }
            }
            if (data.straightRoads != null) {
                for (StraightRoadData road : data.straightRoads) {
                    journal.straightRoads.put(road.getKey(), road);
                }
            }

            if (RorcikStructuresModConfig.enableLogging) {
                long pendingSlots = journal.slots.values().stream().filter(SlotData::isPending).count();
                long completedSlots = journal.slots.values().stream().filter(SlotData::isCompleted).count();
                long pendingNodes = journal.roadNodes.values().stream().filter(RoadNodeData::isPending).count();
                long pendingStraight = journal.straightRoads.values().stream().filter(StraightRoadData::isPending).count();
                LOGGER.info("Info: [JOURNAL] Loaded journal for {} city [{}, {}] in world {}: {} slots ({} pending, {} completed), {} road nodes ({} pending), {} straight roads ({} pending), cityStatus={}",
                        typeId, centerX, centerZ, worldId,
                        journal.slots.size(), pendingSlots, completedSlots,
                        journal.roadNodes.size(), pendingNodes,
                        journal.straightRoads.size(), pendingStraight,
                        journal.cityStatus);
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to load journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }

        return journal;
    }

    public void initializeCity(long seed, int baseY, int targetHeight) {
        this.seed = seed;
        this.baseY = baseY;
        this.targetHeight = targetHeight;
        this.cityStatus = CityStatus.GENERATING.name();

        this.slots.clear();
        this.roadNodes.clear();
        this.straightRoads.clear();
        this.roadNetworkData = null;
    }

    public void rebuildFromContext(CityGenerationContext context) {
        if (this.seed != 0) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Journal for city [{}, {}] already has seed={}, skipping rebuild",
                        centerX, centerZ, this.seed);
            }
            return;
        }
        if (context == null) {
            LOGGER.error("Error: [JOURNAL] Cannot rebuild journal from null context for city [{}, {}]", centerX, centerZ);
            return;
        }

        initializeCity(context.seed(), context.baseY(), context.targetHeight());

        for (Map.Entry<BlockPos, RoadNode> entry : context.nodeMap().entrySet()) {
            BlockPos pos = entry.getKey();
            int anchorChunkX = (pos.getX() >> 6) << 2;
            int anchorChunkZ = (pos.getZ() >> 6) << 2;
            addRoadNode(anchorChunkX, anchorChunkZ, entry.getValue());
        }

        for (BlockPos pos : context.straightPositions()) {
            int anchorChunkX = (pos.getX() >> 6) << 2;
            int anchorChunkZ = (pos.getZ() >> 6) << 2;
            addStraightRoad(anchorChunkX, anchorChunkZ, pos);
        }

        for (BuildingSlot slot : context.slots()) {
            BlockPos pos = slot.position();
            int anchorChunkX = (pos.getX() >> 6) << 2;
            int anchorChunkZ = (pos.getZ() >> 6) << 2;
            addSlot(anchorChunkX, anchorChunkZ, slot);
        }

        setRoadNetworkData(context.roadNetwork());

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [JOURNAL] Rebuilt journal from context for city [{}, {}]: {} slots, {} road nodes, {} straight roads, seed={}",
                    centerX, centerZ, slots.size(), roadNodes.size(), straightRoads.size(), this.seed);
        }
    }

    public void addSlot(int anchorChunkX, int anchorChunkZ, BuildingSlot slot) {
        SlotData data = new SlotData(anchorChunkX, anchorChunkZ, slot);
        slots.put(data.getKey(), data);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [JOURNAL] Added slot: {} ({}) at [{},{},{}]", data.getKey(), slot.category(), slot.position().getX(), slot.position().getY(), slot.position().getZ());
        }
    }

    public void addRoadNode(int anchorChunkX, int anchorChunkZ, RoadNode node) {
        RoadNodeData data = new RoadNodeData(anchorChunkX, anchorChunkZ, node);
        roadNodes.put(data.getKey(), data);
    }

    public void addStraightRoad(int anchorChunkX, int anchorChunkZ, BlockPos pos) {
        StraightRoadData data = new StraightRoadData(anchorChunkX, anchorChunkZ, pos);
        straightRoads.put(data.getKey(), data);
    }

    public void setRoadNetworkData(RoadNetwork network) {
        if (network == null) {
            LOGGER.warn("Warn: [JOURNAL] Cannot set roadNetworkData: network is null");
            return;
        }

        RoadNetworkData data = new RoadNetworkData();

        if (network.getSegments() != null) {
            for (RoadSegment segment : network.getSegments()) {
                data.segments.add(new RoadSegmentData(segment));
            }
        }

        List<Integer> stopVariants = network.getAvailableStopVariants();
        data.availableStopVariants = stopVariants != null
                ? new ArrayList<>(stopVariants)
                : new ArrayList<>();

        data.straightRoadCount = network.getStraightRoadCount();
        this.roadNetworkData = data;

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [JOURNAL] Saved roadNetwork: {} segments, {} stopVariants, {} straightRoadCount",
                    data.segments.size(), data.availableStopVariants.size(), data.straightRoadCount);
        }
    }

    public RoadNetwork restoreRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        if (roadNetworkData == null) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] No roadNetworkData to restore, creating empty");
            }
            return network;
        }
        for (RoadSegmentData segData : roadNetworkData.segments) {
            network.addSegment(segData.toRoadSegment());
        }
        if (!roadNetworkData.availableStopVariants.isEmpty()) {
            network.initStopVariants(roadNetworkData.availableStopVariants);
        }
        for (int i = 0; i < roadNetworkData.straightRoadCount; i++) {
            network.incrementStraightRoadCount();
        }
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [JOURNAL] Restored roadNetwork: {} segments, {} stopVariants, {} straightRoadCount",
                    roadNetworkData.segments.size(), roadNetworkData.availableStopVariants.size(), roadNetworkData.straightRoadCount);
        }
        return network;
    }

    public boolean hasRoadAt(int x, int y, int z) {
        for (RoadNodeData node : roadNodes.values()) {
            if (node.positionX == x && node.positionY == y && node.positionZ == z
                    && SlotStatus.COMPLETED.name().equals(node.status)) {
                return true;
            }
        }
        for (StraightRoadData road : straightRoads.values()) {
            if (road.positionX == x && road.positionY == y && road.positionZ == z
                    && SlotStatus.COMPLETED.name().equals(road.status)) {
                return true;
            }
        }
        return false;
    }

    private static String slotKey(int anchorChunkX, int anchorChunkZ, String category,
                                  int posX, int posY, int posZ) {
        return anchorChunkX + "," + anchorChunkZ + ":" + category + ":" + posX + "," + posY + "," + posZ;
    }

    public void markSlotProcessing(int anchorChunkX, int anchorChunkZ, String category,
                                  int posX, int posY, int posZ, String phase) {
        String key = slotKey(anchorChunkX, anchorChunkZ, category, posX, posY, posZ);
        SlotData slot = slots.get(key);
        if (slot != null) {
            slot.status = SlotStatus.PROCESSING.name();
            slot.currentPhase = phase;
            CityJournalWriter.scheduleWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Slot {} -> PROCESSING (phase={})", key, phase);
            }
        }
    }

    public void markSlotCompleted(int anchorChunkX, int anchorChunkZ, String category,
                                  int posX, int posY, int posZ) {
        String key = slotKey(anchorChunkX, anchorChunkZ, category, posX, posY, posZ);
        SlotData slot = slots.get(key);
        if (slot != null) {
            slot.status = SlotStatus.COMPLETED.name();
            slot.currentPhase = "COMPLETED";
            CityJournalWriter.scheduleWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [JOURNAL] Slot {} -> COMPLETED", key);
            }
        }
    }

    public void markHousePlaced(int anchorChunkX, int anchorChunkZ, String category,
                                int posX, int posY, int posZ, boolean placed) {
        String key = slotKey(anchorChunkX, anchorChunkZ, category, posX, posY, posZ);
        SlotData slot = slots.get(key);
        if (slot != null && slot.housePlaced != placed) {
            slot.housePlaced = placed;
            CityJournalWriter.scheduleWrite(this);
        }
    }

    public void markSlotFailed(int anchorChunkX, int anchorChunkZ, String category,
                               int posX, int posY, int posZ) {
        String key = slotKey(anchorChunkX, anchorChunkZ, category, posX, posY, posZ);
        SlotData slot = slots.get(key);
        if (slot != null) {
            slot.status = SlotStatus.FAILED.name();
            CityJournalWriter.forceWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.error("Error: [JOURNAL] Slot {} -> FAILED", key);
            }
        }
    }

    public void markSlotDeferred(int anchorChunkX, int anchorChunkZ, String category,
                                 int posX, int posY, int posZ) {
        String key = slotKey(anchorChunkX, anchorChunkZ, category, posX, posY, posZ);
        SlotData slot = slots.get(key);
        if (slot != null) {
            slot.status = SlotStatus.DEFERRED.name();
            CityJournalWriter.scheduleWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Slot {} -> DEFERRED", key);
            }
        }
    }

    public void markRoadNodeCompleted(int anchorChunkX, int anchorChunkZ, int posX, int posY, int posZ) {
        String key = anchorChunkX + "," + anchorChunkZ + ":" + posX + "," + posY + "," + posZ;
        RoadNodeData node = roadNodes.get(key);
        if (node != null) {
            node.status = SlotStatus.COMPLETED.name();
            CityJournalWriter.scheduleWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [JOURNAL] RoadNode {} -> COMPLETED", key);
            }
        }
    }

    public void markStraightRoadCompleted(int anchorChunkX, int anchorChunkZ, int posX, int posY, int posZ) {
        String key = anchorChunkX + "," + anchorChunkZ + ":" + posX + "," + posY + "," + posZ;
        StraightRoadData road = straightRoads.get(key);
        if (road != null) {
            road.status = SlotStatus.COMPLETED.name();
            CityJournalWriter.scheduleWrite(this);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [JOURNAL] StraightRoad {} -> COMPLETED", key);
            }
        }
    }

    public List<SlotData> getPendingSlots() {
        return slots.values().stream()
                .filter(SlotData::isPending)
                .collect(Collectors.toList());
    }

    public List<SlotData> getAllSlots() {
        return new ArrayList<>(slots.values());
    }

    public List<RoadNodeData> getPendingRoadNodes() {
        return roadNodes.values().stream()
                .filter(RoadNodeData::isPending)
                .collect(Collectors.toList());
    }

    public List<StraightRoadData> getPendingStraightRoads() {
        return straightRoads.values().stream()
                .filter(StraightRoadData::isPending)
                .collect(Collectors.toList());
    }

    public Map<String, RoadNodeData> getAllRoadNodes() {
        return Collections.unmodifiableMap(roadNodes);
    }

    public Map<String, StraightRoadData> getAllStraightRoads() {
        return Collections.unmodifiableMap(straightRoads);
    }

    public double completionRatio() {
        int total = slots.size() + roadNodes.size() + straightRoads.size();
        if (total == 0) return 0;
        long completed = slots.values().stream().filter(SlotData::isCompleted).count()
                + roadNodes.values().stream().filter(n -> SlotStatus.COMPLETED.name().equals(n.status)).count()
                + straightRoads.values().stream().filter(r -> SlotStatus.COMPLETED.name().equals(r.status)).count();
        return (double) completed / total;
    }

    public void markCityCompleted() {
        this.cityStatus = CityStatus.COMPLETED.name();
        CityJournalWriter.forceWrite(this);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: [JOURNAL] City [{}, {}] marked as COMPLETED", centerX, centerZ);
        }
    }

    public void save() {
        try {
            Path journalsDir = journalPath.getParent();
            if (!Files.exists(journalsDir)) {
                Files.createDirectories(journalsDir);
            }

            List<SlotData> slotsSnapshot = new ArrayList<>(this.slots.values());
            List<RoadNodeData> nodesSnapshot = new ArrayList<>(this.roadNodes.values());
            List<StraightRoadData> straightSnapshot = new ArrayList<>(this.straightRoads.values());

            JournalData data = new JournalData();
            data.centerX = centerX;
            data.centerZ = centerZ;
            data.worldId = worldId;
            data.typeId = typeId;
            data.seed = seed;
            data.baseY = baseY;
            data.targetHeight = targetHeight;
            data.cityStatus = cityStatus;
            data.slots = slotsSnapshot;
            data.roadNodes = nodesSnapshot;
            data.straightRoads = straightSnapshot;
            data.roadNetworkData = roadNetworkData;

            String json = GSON.toJson(data);

            Path tmpPath = journalPath.resolveSibling(journalPath.getFileName() + ".tmp");
            Files.writeString(tmpPath, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            try {
                Files.move(tmpPath, journalPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, journalPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (RorcikStructuresModConfig.enableLogging) {
                long pendingSlots = slotsSnapshot.stream().filter(SlotData::isPending).count();
                long completedSlots = slotsSnapshot.stream().filter(SlotData::isCompleted).count();
                LOGGER.info("Info: [JOURNAL] Saved journal for {} city [{}, {}] in world {}: {} slots ({} pending, {} completed), {} road nodes, {} straight roads",
                        typeId, centerX, centerZ, worldId, slotsSnapshot.size(), pendingSlots, completedSlots, nodesSnapshot.size(), straightSnapshot.size());
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to save journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }
    }

    public void delete() {
        try {
            if (Files.exists(journalPath)) {
                Files.delete(journalPath);
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Success: [JOURNAL] Deleted journal for {} city [{}, {}] in world {} (100% complete)", typeId, centerX, centerZ, worldId);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to delete journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }
    }

    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public String getWorldId() { return worldId; }
    public String getTypeId() { return typeId; }
    public long getSeed() { return seed; }
    public int getBaseY() { return baseY; }
    public int getTargetHeight() { return targetHeight; }
    public String getCityStatus() { return cityStatus; }
    public int getTotalSlots() { return slots.size() + roadNodes.size() + straightRoads.size(); }
}