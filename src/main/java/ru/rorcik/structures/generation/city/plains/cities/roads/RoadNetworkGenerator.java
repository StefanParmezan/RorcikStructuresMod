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
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.CityBuildScheduler;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingGenerator;

import java.util.*;

public class RoadNetworkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_LOAD_RADIUS = 8;

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed, int maxSegments) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);
        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();
        RoadNode root = RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        Set<Long> chunkKeys = collectChunkKeys(nodeMap.keySet(), straightPositions);
        LOGGER.info("Graph built: {} nodes, {} straights, {} chunks needed",
                nodeMap.size(), straightPositions.size(), chunkKeys.size());

        Set<ResourceLocation> roadTemplates = collectRoadTemplates(nodeMap);
        CityBuildScheduler.queuePreload(roadTemplates);

        CityBuildScheduler.scheduleChunkLoading(chunkKeys, () -> {
            LOGGER.info("Chunks ready, building city...");

            RoadNetwork network = new RoadNetwork();
            List<Runnable> allActions = new ArrayList<>();

            collectRoadActions(level, network, random, root, new HashSet<>(), allActions);
            int roadCount = allActions.size();

            List<ResourceLocation> decorTemplates = RoadDecorationsPlacer.collectRequiredDecorTemplates(network);
            if (!decorTemplates.isEmpty()) {
                CityBuildScheduler.queuePreload(decorTemplates);
                LOGGER.info("[BUILD] Queued {} road decor templates for preloading", decorTemplates.size());
            }

            RoadDecorationsPlacer.collectDecorationActions(level, network, random, allActions);
            int decorCount = allActions.size() - roadCount;

            List<Runnable> buildingActions = BuildingGenerator.collectBuildActions(
                    level, network, nodeMap, straightPositions, random
            );
            allActions.addAll(buildingActions);

            LOGGER.info("Scheduled {} road actions + {} decor actions + {} building actions = {} total",
                    roadCount, decorCount, buildingActions.size(), allActions.size());

            CityBuildScheduler.scheduleBuildActions(allActions);
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

        LOGGER.info("[ROAD-PRELOAD] Collected {} road templates for types: {}", templates.size(), usedRoadTypes);
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
                        RoadSegment segment = RoadPlacer.createAndPlaceRoad(level, network, "s", straightPos, sRotation, random);
                        if (segment != null) {
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
            addChunksInRadius(pos, CHUNK_LOAD_RADIUS, chunkKeys);
        }
        for (BlockPos pos : straightPositions) {
            addChunksInRadius(pos, CHUNK_LOAD_RADIUS, chunkKeys);
        }
        return chunkKeys;
    }

    private static void addChunksInRadius(BlockPos pos, int radiusChunks, Set<Long> chunkKeys) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                long key = ((long) (baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                chunkKeys.add(key);
            }
        }
    }
}