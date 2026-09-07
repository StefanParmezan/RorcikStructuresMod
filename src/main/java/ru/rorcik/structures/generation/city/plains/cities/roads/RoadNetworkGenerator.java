package ru.rorcik.structures.generation.city.plains.cities.roads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.*;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.CityBuildScheduler;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingGenerator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingSlotAllocator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkWorldTracker;
import ru.rorcik.structures.generation.city.plains.cities.buildings.SidewalkPlacer;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RoadNetworkGenerator {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final int CHUNK_LOAD_RADIUS = 4;

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();
        RoadNode root = RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        Set<Long> chunkKeys = collectChunkKeys(nodeMap.keySet(), straightPositions);

        LOGGER.info("Graph built: {} nodes, {} straights, {} chunks needed",
                nodeMap.size(), straightPositions.size(), chunkKeys.size());

        ParkWorldTracker tracker = ParkWorldTracker.get(level);

        CompletableFuture<List<BuildingSlot>> allocationFuture = CompletableFuture.supplyAsync(
                () -> BuildingSlotAllocator.allocateSlots(level, nodeMap, straightPositions, new HashSet<>(), random, tracker),
                CityBuildScheduler.getBackgroundExecutor()
        );

        CityBuildScheduler.scheduleChunkLoading(chunkKeys, () -> {
            CityBuildScheduler.scheduleBackgroundTask(allocationFuture, () -> {
                LOGGER.info("Chunks ready and allocation complete, building city...");
                List<BuildingSlot> slots = allocationFuture.join();

                List<CityBuildScheduler.GenerationPhase> phases = new ArrayList<>();

                Set<ResourceLocation> roadTemplates = collectRoadTemplates(nodeMap);
                List<Runnable> roadActions = new ArrayList<>();
                RoadNetwork network = new RoadNetwork();
                collectRoadActions(level, network, random, root, new HashSet<>(), roadActions);
                phases.add(new CityBuildScheduler.GenerationPhase(
                        "Roads & Decorations",
                        roadTemplates,
                        roadActions,
                        StructureCache.Category.ROADS
                ));

                Set<ResourceLocation> buildingTemplates = BuildingGenerator.collectRequiredBuildTemplates(slots);
                List<Runnable> buildingActions = BuildingGenerator.collectBuildActions(level, slots);
                phases.add(new CityBuildScheduler.GenerationPhase(
                        "Buildings & Platforms",
                        buildingTemplates,
                        buildingActions,
                        StructureCache.Category.HOUSES
                ));

                Set<ResourceLocation> roomTemplates = BuildingGenerator.collectRequiredRoomTemplates(slots);
                List<Runnable> roomActions = BuildingGenerator.collectRoomPlacementActions(level, slots, random);
                phases.add(new CityBuildScheduler.GenerationPhase(
                        "Rooms (Interiors)",
                        roomTemplates,
                        roomActions,
                        StructureCache.Category.ROOMS
                ));

                Set<ResourceLocation> sidewalkTemplates = SidewalkPlacer.collectRequiredTemplates();
                List<Runnable> sidewalkActions = SidewalkPlacer.collectSidewalkActions(slots, level, random);
                phases.add(new CityBuildScheduler.GenerationPhase(
                        "Sidewalks",
                        sidewalkTemplates,
                        sidewalkActions,
                        StructureCache.Category.SIDEWALKS
                ));

                CityBuildScheduler.startPhasedGeneration(chunkKeys, phases);
            });
        });
    }

    private static Set<ResourceLocation> collectRoadTemplates(Map<BlockPos, RoadNode> nodeMap) {
        Set<ResourceLocation> templates = new HashSet<>();
        Set<String> usedRoadTypes = new HashSet<>();

        for (RoadNode node : nodeMap.values()) {
            List<Direction> connections = node.getAllConnections();
            if (!connections.isEmpty()) {
                RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
                if (entry != null) {
                    usedRoadTypes.add(entry.type());
                }
            }
        }
        usedRoadTypes.add("s");

        for (String roadType : usedRoadTypes) {
            RoadConfig config = RoadConfigLoader.getConfig(roadType);
            if (config != null) {
                for (int decor = 0; decor < Math.max(1, config.decorCount()); decor++) {
                    ResourceLocation loc = ResourceLocation.tryParse(
                            ModPaths.roadNbtPath(config.type(), config.type() + "_" + decor)
                    );
                    if (loc != null) templates.add(loc);
                }
            }
        }

        for (String decorName : RoadDecorationsPlacer.DECOR_NAMES) {
            ResourceLocation loc = ResourceLocation.tryParse(ModPaths.roadDecorNbtPath(decorName));
            if (loc != null) {
                templates.add(loc);
            }
        }

        LOGGER.info("[ROAD-PRELOAD] Collected {} road + decor templates for types: {}",
                templates.size(), usedRoadTypes);
        return templates;
    }

    private static void collectRoadActions(
            ServerLevel level,
            RoadNetwork network,
            RandomSource random,
            RoadNode node,
            Set<BlockPos> placed,
            List<Runnable> actions
    ) {
        if (placed.contains(node.position())) return;
        placed.add(node.position());

        List<Direction> connections = node.getAllConnections();
        if (!connections.isEmpty()) {
            RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
            if (entry != null) {
                BlockPos pos = node.position();
                String type = entry.type();
                Rotation rot = entry.rotation();
                actions.add(() -> {
                    RoadSegment segment = RoadPlacer.createAndPlaceRoad(level, network, type, pos, rot, random);
                    if (segment != null) {
                        RoadDecorationsPlacer.placeDecorationsForSingleRoad(level, segment, random);
                    }
                });
            }
        }

        for (RoadNode child : node.children()) {
            Direction exitDir = RoadPlacer.guessDirection(node.position(), child.position());
            BlockPos currentPos = RoadNetwork.getNextPosition(node.position(), exitDir, GeneratorConfig.ROAD_STEP);
            Rotation sRotation = RoadPlacer.directionToRotation(exitDir);

            for (int i = 0; i < GeneratorConfig.STRAIGHT_BETWEEN_NODES; i++) {
                BlockPos straightPos = currentPos;
                if (!network.hasSegmentAt(straightPos)) {
                    actions.add(() -> {
                        String roadTypeToPlace = "s";
                        int forcedDecor = -1;

                        RoadConfig sConfig = RoadConfigLoader.getConfig("s");
                        if (sConfig != null && !sConfig.stopVariants().isEmpty()) {
                            if (network.getAvailableStopVariants().isEmpty()) {
                                network.initStopVariants(sConfig.stopVariants());
                            }

                            if (network.getStraightRoadCount() >= 2) {
                                Integer stopVariant = network.pollStopVariant();
                                if (stopVariant != null) {
                                    forcedDecor = stopVariant;
                                }
                            }
                        }

                        RoadSegment segment = RoadPlacer.createAndPlaceRoad(level, network, roadTypeToPlace, straightPos, sRotation, random, forcedDecor);
                        if (segment != null) {
                            network.incrementStraightRoadCount();
                            RoadDecorationsPlacer.placeDecorationsForSingleRoad(level, segment, random);
                        }
                    });
                }
                currentPos = RoadNetwork.getNextPosition(currentPos, exitDir, GeneratorConfig.ROAD_STEP);
            }
            collectRoadActions(level, network, random, child, placed, actions);
        }
    }

    private static Set<Long> collectChunkKeys(Set<BlockPos> nodePositions, Set<BlockPos> straightPositions) {
        Set<Long> chunkKeys = new HashSet<>();
        for (BlockPos pos : nodePositions) {
            addChunksInRadius(pos, chunkKeys);
        }
        for (BlockPos pos : straightPositions) {
            addChunksInRadius(pos, chunkKeys);
        }
        return chunkKeys;
    }

    private static void addChunksInRadius(BlockPos pos, Set<Long> chunkKeys) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = -RoadNetworkGenerator.CHUNK_LOAD_RADIUS; dx <= RoadNetworkGenerator.CHUNK_LOAD_RADIUS; dx++) {
            for (int dz = -RoadNetworkGenerator.CHUNK_LOAD_RADIUS; dz <= RoadNetworkGenerator.CHUNK_LOAD_RADIUS; dz++) {
                long key = ((long) (baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                chunkKeys.add(key);
            }
        }
    }
}