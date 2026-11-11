package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.event.ZoneEvents;
import ru.rorcik.core.zone.BiomeZoneRegistry;
import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingSlotAllocator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkPlacer;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkWorldTracker;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadGraphBuilder;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.*;

import java.util.*;

public class CityZonePlanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int CITY_CHUNK_BORDER = 1;
    private static final String[] DESERT_BUILDINGS = {
            "aa", "ab", "ac", "ad", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "z"
    };

    public static boolean tryReserveCity(ServerLevel level, int centerX, int centerZ, CityType type) {
        try {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] Reserving zone for {} city at [{}, {}]", type.id, centerX, centerZ);
            }

            BlockPos checkPos = new BlockPos(centerX, level.getSeaLevel(), centerZ);
            Holder<Biome> biomeHolder = level.getBiome(checkPos);
            ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

            if (biomeId == null) {
                LOGGER.warn("Warn: [CITY-PLANNER] Failed to get biome ID at [{}, {}]", centerX, centerZ);
                return false;
            }

            if (!"desert".equals(type.id)) {
                if (ModList.get().isLoaded("rwg")) {
                    try {
                        Class<?> rwgConfigClass = Class.forName("ru.rorcik.rwg.RwgConfig");
                        java.lang.reflect.Method isAllowedMethod = rwgConfigClass.getMethod("isAllowedSpawnBiome", ResourceLocation.class);
                        Boolean isAllowed = (Boolean) isAllowedMethod.invoke(null, biomeId);
                        if (Boolean.FALSE.equals(isAllowed)) { return false; }
                    } catch (Exception e) {  }
                }
                TemperatureZone currentZone = getBiomeTemperatureZone(biomeId, biomeHolder.value());
                if (currentZone == null || !type.allowedZones.contains(currentZone)) {
                    LOGGER.warn("Warn: [CITY-PLANNER] Biome not suitable for type {}", type.id);
                    return false;
                }
            }

            ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
            boolean reserved = registry.tryReserveZone(centerX, centerZ, type.toZoneRules());
            if (!reserved) { return false; }

            boolean isDesert = "desert".equals(type.id);
            int anchorChunkSize = isDesert ? 2 : 4;
            DryRunResult dryRun = isDesert ? performDesertDryRun(level, centerX, centerZ, type.targetHeight) : performDryRun(level, centerX, centerZ, type.targetHeight);

            Set<Long> affectedChunks = calculateExactChunks(dryRun, anchorChunkSize);

            CityJournal journal = CityJournal.load(centerX, centerZ, type.id, level);
            journal.initializeCity(dryRun.seed, dryRun.baseY, type.targetHeight);

            int roadNodeCount = 0, straightRoadCount = 0, buildingSlotCount = 0;
            for (Map.Entry<BlockPos, RoadNode> entry : dryRun.nodeMap.entrySet()) {
                int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
                journal.addRoadNode((entry.getKey().getX() >> (4 + shift)) << shift, (entry.getKey().getZ() >> (4 + shift)) << shift, entry.getValue());
                roadNodeCount++;
            }
            for (BlockPos pos : dryRun.straightPositions) {
                int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
                journal.addStraightRoad((pos.getX() >> (4 + shift)) << shift, (pos.getZ() >> (4 + shift)) << shift, pos);
                straightRoadCount++;
            }
            for (BuildingSlot slot : dryRun.slots) {
                int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
                journal.addSlot((slot.position().getX() >> (4 + shift)) << shift, (slot.position().getZ() >> (4 + shift)) << shift, slot);
                buildingSlotCount++;
            }

            journal.setRoadNetworkData(dryRun.roadNetwork);
            journal.save();

            String worldId = CityJournal.getWorldId(level);
            CityGenerationContext context = new CityGenerationContext(worldId, centerX, centerZ, type.id, type.targetHeight, dryRun.nodeMap, dryRun.straightPositions, dryRun.slots, dryRun.roadNetwork, dryRun.seed, dryRun.baseY, anchorChunkSize);
            CityGenerationContextRegistry.register(context);
            CityGenerationSavedData.get(level).registerCity(centerX, centerZ, type.targetHeight);

            MinecraftForge.EVENT_BUS.post(new ZoneEvents.ZoneReservedEvent(centerX, centerZ, type.toZoneRules(), dryRun.baseY, affectedChunks));
            PlayerProximityTracker.registerCity(worldId, centerX, centerZ, type.id, affectedChunks);

            return true;
        } catch (Exception e) {
            LOGGER.error("Error: [CITY-PLANNER] Exception in tryReserveCity at [{}, {}]: {}", centerX, centerZ, e.getMessage(), e);
            return false;
        }
    }

    public static CityGenerationContext rebuildContext(ServerLevel level, int centerX, int centerZ, String typeId) {
        CityGenerationSavedData genData = CityGenerationSavedData.get(level);
        CityGenerationSavedData.CityGenState state = genData.getState(centerX, centerZ);
        if (state == null || state.isComplete()) return null;

        int anchorChunkSize = "desert".equals(typeId) ? 2 : 4;

        CityJournal journal = CityJournal.load(centerX, centerZ, typeId, level);

        if (journal.getSeed() != 0) {
            RoadNetwork roadNetwork = journal.restoreRoadNetwork();
            Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
            for (CityJournal.RoadNodeData nodeData : journal.getAllRoadNodes().values()) { nodeMap.put(nodeData.toRoadNode().position(), nodeData.toRoadNode()); }
            Set<BlockPos> straightPositions = new HashSet<>();
            for (CityJournal.StraightRoadData roadData : journal.getAllStraightRoads().values()) { straightPositions.add(roadData.toBlockPos()); }
            List<BuildingSlot> slots = new ArrayList<>();
            for (CityJournal.SlotData slotData : journal.getPendingSlots()) { slots.add(slotData.toBuildingSlot()); }

            CityGenerationContext context = new CityGenerationContext(CityJournal.getWorldId(level), centerX, centerZ, typeId, state.targetHeight(), nodeMap, straightPositions, slots, roadNetwork, journal.getSeed(), journal.getBaseY(), anchorChunkSize);
            CityGenerationContextRegistry.register(context);
            return context;
        }

        DryRunResult dryRun = performDryRun(level, centerX, centerZ, state.targetHeight());
        CityGenerationContext context = new CityGenerationContext(CityJournal.getWorldId(level), centerX, centerZ, typeId, state.targetHeight(), dryRun.nodeMap, dryRun.straightPositions, dryRun.slots, dryRun.roadNetwork, dryRun.seed, dryRun.baseY, anchorChunkSize);
        CityGenerationContextRegistry.register(context);

        CityJournal rebuiltJournal = CityJournal.load(centerX, centerZ, typeId, level);
        if (rebuiltJournal.getSeed() == 0) { rebuiltJournal.rebuildFromContext(context); rebuiltJournal.save(); }
        return context;
    }

    private static DryRunResult performDesertDryRun(ServerLevel level, int centerX, int centerZ, int targetHeight) {
        long deterministicSeed = level.getSeed() ^ (((long) centerX << 32) | (centerZ & 0xFFFFFFFFL));
        RandomSource random = RandomSource.create(deterministicSeed);

        int buildingCount = ru.rorcik.structures.generation.GeneratorConfig.DESERT_BUILDING_COUNT;
        int stepMin = ru.rorcik.structures.generation.GeneratorConfig.DESERT_STEP_MIN;
        int stepMax = Math.max(stepMin, ru.rorcik.structures.generation.GeneratorConfig.DESERT_STEP_MAX);
        int yOffset = ru.rorcik.structures.generation.GeneratorConfig.DESERT_Y_OFFSET;

        List<BuildingSlot> slots = new ArrayList<>();
        List<BlockPos> placedPositions = new ArrayList<>();

        int seaLevel = level.getSeaLevel();
        int minSafeY = seaLevel + 1;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
        int startY = Math.max(minSafeY, surfaceY) + yOffset;

        BlockPos startPos = new BlockPos(centerX, startY, centerZ);
        placedPositions.add(startPos);

        for (int i = 0; i < buildingCount; i++) {
            BlockPos anchorPos = placedPositions.get(random.nextInt(placedPositions.size()));

            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int step = stepMin + random.nextInt(stepMax - stepMin + 1);

            int newX = anchorPos.getX() + (dir.getStepX() * step);
            int newZ = anchorPos.getZ() + (dir.getStepZ() * step);

            int newSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, newX, newZ);
            int placementY = Math.max(minSafeY, newSurfaceY) + yOffset;

            BlockPos pos = new BlockPos(newX, placementY, newZ);
            String buildingName = DESERT_BUILDINGS[random.nextInt(DESERT_BUILDINGS.length)];
            net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.values()[random.nextInt(4)];

            slots.add(new BuildingSlot(pos, rotation, null, "desert", buildingName, 0));
            placedPositions.add(pos);
        }

        return new DryRunResult(deterministicSeed, new HashMap<>(), new HashSet<>(), slots, new RoadNetwork(), startY);
    }

    private static DryRunResult performDryRun(ServerLevel level, int centerX, int centerZ, int targetHeight) {
        int baseY = targetHeight != -1 ? targetHeight : level.getSeaLevel();
        BlockPos origin = new BlockPos(centerX, baseY, centerZ);
        long deterministicSeed = level.getSeed() ^ (((long) centerX << 32) | (centerZ & 0xFFFFFFFFL));
        RandomSource random = RandomSource.create(deterministicSeed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();
        RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        RoadNetwork roadNetwork = new RoadNetwork();
        ParkWorldTracker tracker = ParkWorldTracker.get(level);
        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(level, nodeMap, straightPositions, new HashSet<>(), random, tracker);
        return new DryRunResult(deterministicSeed, nodeMap, straightPositions, slots, roadNetwork, baseY);
    }

    private record DryRunResult(long seed, Map<BlockPos, RoadNode> nodeMap, Set<BlockPos> straightPositions, List<BuildingSlot> slots, RoadNetwork roadNetwork, int baseY) {}

    private static Set<Long> calculateExactChunks(DryRunResult dryRun, int anchorChunkSize) {
        Set<Long> chunks = new HashSet<>();

        for (Map.Entry<BlockPos, RoadNode> entry : dryRun.nodeMap.entrySet()) {
            List<Direction> connections = entry.getValue().getAllConnections();
            RoadConfigLoader.RoadMapEntry roadEntry = RoadConfigLoader.findRoadForConnections(connections);
            if (roadEntry != null && "st".equals(roadEntry.type())) continue;
            addStructureChunks(chunks, entry.getKey(), 4);
        }

        for (BlockPos pos : dryRun.straightPositions) {
            addStructureChunks(chunks, pos, 4);
        }

        for (BuildingSlot slot : dryRun.slots) {
            if (slot.category().equals("platforms")) {
                continue;
            }

            if (slot.category().equals("parks")) {
                ParkConfig park = ParkConfigLoader.getPark(slot.buildingName());
                if (park != null) {
                    
                    Set<BlockPos> parkSlots = ParkPlacer.calculateSlots(slot.position(), slot.roadSide(), park, slot.rotation());
                    for (BlockPos parkSlotPos : parkSlots) {
                        addStructureChunks(chunks, parkSlotPos, anchorChunkSize);
                    }
                } else {
                    addStructureChunks(chunks, slot.position(), anchorChunkSize);
                }
            } else {
                addStructureChunks(chunks, slot.position(), anchorChunkSize);
            }
        }

        return expandCityChunks(chunks);
    }

    public static Set<Long> expandCityChunks(Set<Long> chunks) {
        if (chunks.isEmpty()) return chunks;

        Set<Long> expanded = new HashSet<>();
        for (long chunkKey : chunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) (chunkKey & 0xFFFFFFFFL);
            for (int dx = -CITY_CHUNK_BORDER; dx <= CITY_CHUNK_BORDER; dx++) {
                for (int dz = -CITY_CHUNK_BORDER; dz <= CITY_CHUNK_BORDER; dz++) {
                    expanded.add(((long) (chunkX + dx) << 32) | ((chunkZ + dz) & 0xFFFFFFFFL));
                }
            }
        }
        return expanded;
    }

    private static void addStructureChunks(Set<Long> chunks, BlockPos pos, int size) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                chunks.add(((long)(baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL));
            }
        }
    }

    private static TemperatureZone getBiomeTemperatureZone(ResourceLocation biomeId, Biome biome) {
        Optional<TemperatureZone> coreZone = BiomeZoneRegistry.getZone(biomeId);
        if (coreZone.isPresent()) return coreZone.get();
        float temp = biome.getBaseTemperature();
        if (temp < 0.15f) return TemperatureZone.COLD;
        if (temp < 0.45f) return TemperatureZone.COOL;
        if (temp < 0.75f) return TemperatureZone.NEUTRAL;
        if (temp < 0.95f) return TemperatureZone.WARM;
        return TemperatureZone.HOT;
    }
}