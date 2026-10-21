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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.event.ZoneEvents;
import ru.rorcik.core.zone.BiomeZoneRegistry;
import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingSlotAllocator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkWorldTracker;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadGraphBuilder;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContext;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContextRegistry;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationSavedData;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityJournal;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.PlayerProximityTracker;

import java.util.*;

public class CityZonePlanner {
    private static final Logger LOGGER = LogUtils.getLogger();

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
            TemperatureZone currentZone = getBiomeTemperatureZone(biomeId, biomeHolder.value());
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] Biome: {} | Zone: {}", biomeId, currentZone);
            }
            if (currentZone == null || !type.allowedZones.contains(currentZone)) {
                LOGGER.warn("Warn: [CITY-PLANNER] Biome not suitable for type {}", type.id);
                return false;
            }

            ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
            boolean reserved = registry.tryReserveZone(centerX, centerZ, type.toZoneRules());
            if (!reserved) {
                LOGGER.warn("Warn: [CITY-PLANNER] Zone already occupied");
                return false;
            }

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [CITY-PLANNER] Zone reserved");
            }

            DryRunResult dryRun = performDryRun(level, centerX, centerZ, type.targetHeight);
            Set<Long> affectedChunks = calculateExactChunks(dryRun);

            CityJournal journal = CityJournal.load(centerX, centerZ, level);
            journal.initializeCity(dryRun.seed, dryRun.baseY, type.targetHeight);

            int roadNodeCount = 0;
            for (Map.Entry<BlockPos, RoadNode> entry : dryRun.nodeMap.entrySet()) {
                BlockPos pos = entry.getKey();
                RoadNode node = entry.getValue();
                int anchorChunkX = (pos.getX() >> 6) << 2;
                int anchorChunkZ = (pos.getZ() >> 6) << 2;
                journal.addRoadNode(anchorChunkX, anchorChunkZ, node);
                roadNodeCount++;
            }

            int straightRoadCount = 0;
            for (BlockPos pos : dryRun.straightPositions) {
                int anchorChunkX = (pos.getX() >> 6) << 2;
                int anchorChunkZ = (pos.getZ() >> 6) << 2;
                journal.addStraightRoad(anchorChunkX, anchorChunkZ, pos);
                straightRoadCount++;
            }

            int buildingSlotCount = 0;
            for (BuildingSlot slot : dryRun.slots) {
                BlockPos pos = slot.position();
                int anchorChunkX = (pos.getX() >> 6) << 2;
                int anchorChunkZ = (pos.getZ() >> 6) << 2;
                journal.addSlot(anchorChunkX, anchorChunkZ, slot);
                buildingSlotCount++;
            }

            journal.setRoadNetworkData(dryRun.roadNetwork);

            // >>> FIX: персистим журнал на диск ДО регистрации контекста.
            // Без этого CityBuildScheduler.startCityGeneration позже не найдёт файл
            // и получит seed=0 (баг, из-за которого город не спавнился).
            journal.save();

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] Recorded to journal: {} road nodes, {} straight roads, {} building slots, seed={}",
                        roadNodeCount, straightRoadCount, buildingSlotCount, dryRun.seed);
                LOGGER.info("Info: [CITY-PLANNER] Journal persisted to disk");
            }

            CityGenerationContext context = new CityGenerationContext(
                    centerX, centerZ, type.id, type.targetHeight,
                    dryRun.nodeMap, dryRun.straightPositions, dryRun.slots,
                    dryRun.roadNetwork, dryRun.seed, dryRun.baseY
            );
            CityGenerationContextRegistry.register(context);

            CityGenerationSavedData genData = CityGenerationSavedData.get(level);
            genData.registerCity(centerX, centerZ, type.targetHeight);

            MinecraftForge.EVENT_BUS.post(new ZoneEvents.ZoneReservedEvent(
                    centerX, centerZ, type.toZoneRules(), type.targetHeight, affectedChunks
            ));

            PlayerProximityTracker.registerCity(centerX, centerZ, affectedChunks);

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] City fully prepared. Waiting for player...");
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Error: [CITY-PLANNER] Exception in tryReserveCity at [{}, {}]: {}", centerX, centerZ, e.getMessage(), e);
            return false;
        }
    }

    public static CityGenerationContext rebuildContext(ServerLevel level, int centerX, int centerZ) {
        CityGenerationSavedData genData = CityGenerationSavedData.get(level);
        CityGenerationSavedData.CityGenState state = genData.getState(centerX, centerZ);

        if (state == null) {
            LOGGER.error("Error: [CITY-PLANNER] Cannot rebuild context: city at [{}, {}] not registered in NBT", centerX, centerZ);
            return null;
        }

        if (state.isComplete()) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] City at [{}, {}] is already complete, skipping rebuild", centerX, centerZ);
            }
            return null;
        }

        CityJournal journal = CityJournal.load(centerX, centerZ, level);
        if (journal.getSeed() != 0) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] Rebuilding context from journal for city at [{}, {}] with seed={}", centerX, centerZ, journal.getSeed());
            }
            RoadNetwork roadNetwork = journal.restoreRoadNetwork();
            Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
            for (CityJournal.RoadNodeData nodeData : journal.getAllRoadNodes().values()) {
                RoadNode node = nodeData.toRoadNode();
                nodeMap.put(node.position(), node);
            }
            Set<BlockPos> straightPositions = new HashSet<>();
            for (CityJournal.StraightRoadData roadData : journal.getAllStraightRoads().values()) {
                straightPositions.add(roadData.toBlockPos());
            }
            List<BuildingSlot> slots = new ArrayList<>();
            for (CityJournal.SlotData slotData : journal.getPendingSlots()) {
                slots.add(slotData.toBuildingSlot());
            }

            CityGenerationContext context = new CityGenerationContext(
                    centerX, centerZ, "plains", state.targetHeight(),
                    nodeMap, straightPositions, slots,
                    roadNetwork, journal.getSeed(), journal.getBaseY()
            );
            CityGenerationContextRegistry.register(context);
            return context;
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PLANNER] Rebuilding context via dry run for city at [{}, {}]", centerX, centerZ);
        }
        DryRunResult dryRun = performDryRun(level, centerX, centerZ, state.targetHeight());

        CityGenerationContext context = new CityGenerationContext(
                centerX, centerZ, "plains", state.targetHeight(),
                dryRun.nodeMap, dryRun.straightPositions, dryRun.slots,
                dryRun.roadNetwork, dryRun.seed, dryRun.baseY
        );
        CityGenerationContextRegistry.register(context);

        // >>> FIX: сохраняем восстановленный журнал, иначе при следующем рестарте
        // сервера journal снова окажется с seed=0 и dry run будет выполняться заново.
        CityJournal rebuiltJournal = CityJournal.load(centerX, centerZ, level);
        if (rebuiltJournal.getSeed() == 0) {
            rebuiltJournal.rebuildFromContext(context);
            rebuiltJournal.save();
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-PLANNER] Rebuilt journal persisted for city at [{}, {}]", centerX, centerZ);
            }
        }

        return context;
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
        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(
                level, nodeMap, straightPositions, new HashSet<>(), random, tracker
        );

        return new DryRunResult(deterministicSeed, nodeMap, straightPositions, slots, roadNetwork, baseY);
    }

    private record DryRunResult(
            long seed,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            List<BuildingSlot> slots,
            RoadNetwork roadNetwork,
            int baseY
    ) {}

    private static Set<Long> calculateExactChunks(DryRunResult dryRun) {
        Set<Long> chunks = new HashSet<>();
        for (Map.Entry<BlockPos, RoadNode> entry : dryRun.nodeMap.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadNode node = entry.getValue();
            List<Direction> connections = node.getAllConnections();
            RoadConfigLoader.RoadMapEntry roadEntry = RoadConfigLoader.findRoadForConnections(connections);
            if (roadEntry != null && "st".equals(roadEntry.type())) {
                continue;
            }
            addStructureChunks(chunks, pos);
        }
        for (BlockPos pos : dryRun.straightPositions) {
            addStructureChunks(chunks, pos);
        }
        for (BuildingSlot slot : dryRun.slots) {
            if (!slot.category().equals("platforms")) {
                addStructureChunks(chunks, slot.position());
            }
        }
        return chunks;
    }

    private static void addStructureChunks(Set<Long> chunks, BlockPos pos) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                long key = ((long)(baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                chunks.add(key);
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