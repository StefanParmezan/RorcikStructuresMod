package ru.rorcik.structures.generation.city.plains.cities.roads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.CityBuildScheduler;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingGenerator;

import java.util.*;

public class RoadNetworkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed, int maxSegments) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();

        RoadNode root = RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        Set<Long> chunkKeys = collectChunkKeys(nodeMap.keySet(), straightPositions);

        LOGGER.info("Graph built: {} nodes, {} straights, {} chunks needed",
                nodeMap.size(), straightPositions.size(), chunkKeys.size());

        // Фаза 1: запускаем батчевую загрузку чанков
        CityBuildScheduler.scheduleChunkLoading(chunkKeys, () -> {
            // Фаза 2: когда чанки загружены — строим
            LOGGER.info("Chunks ready, building city...");

            RoadNetwork network = new RoadNetwork();
            List<Runnable> buildActions = new ArrayList<>();

            // Собираем действия для дорог
            collectRoadActions(level, network, random, root, new HashSet<>(), buildActions);

            // Собираем действия для зданий и парков
            buildActions.add(() -> {
                BuildingGenerator.generateBuildings(level, network, nodeMap, straightPositions, random);
            });

            // Запускаем батчевую постройку
            CityBuildScheduler.scheduleBuildActions(buildActions);

            LOGGER.info("Scheduled {} build actions", buildActions.size());
        });
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
                actions.add(() -> RoadPlacer.createAndPlaceRoad(level, network, type, pos, rot, random));
            }
        }

        for (RoadNode child : node.children()) {
            Direction exitDir = RoadPlacer.guessDirection(node.position(), child.position());
            BlockPos currentPos = RoadNetwork.getNextPosition(node.position(), exitDir, GeneratorConfig.ROAD_STEP);
            Rotation sRotation = RoadPlacer.directionToRotation(exitDir);

            for (int i = 0; i < GeneratorConfig.STRAIGHT_BETWEEN_NODES; i++) {
                BlockPos straightPos = currentPos;
                if (!network.hasSegmentAt(straightPos)) {
                    actions.add(() -> RoadPlacer.createAndPlaceRoad(level, network, "s", straightPos, sRotation, random));
                }
                currentPos = RoadNetwork.getNextPosition(currentPos, exitDir, GeneratorConfig.ROAD_STEP);
            }

            collectRoadActions(level, network, random, child, placed, actions);
        }
    }

    private static Set<Long> collectChunkKeys(Set<BlockPos> nodePositions, Set<BlockPos> straightPositions) {
        Set<Long> chunkKeys = new HashSet<>();

        for (BlockPos pos : nodePositions) {
            addChunksForPos(pos, chunkKeys);
        }

        for (BlockPos pos : straightPositions) {
            addChunksForPos(pos, chunkKeys);
        }

        return chunkKeys;
    }

    private static void addChunksForPos(BlockPos pos, Set<Long> chunkKeys) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;

        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int chunkX = baseChunkX + dx;
                int chunkZ = baseChunkZ + dz;
                long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                chunkKeys.add(key);
            }
        }
    }
}