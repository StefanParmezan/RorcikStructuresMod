package ru.rorcik.structures.generation.city.plains.cities.roads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingGenerator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkPlacer;

import java.util.*;

public class RoadNetworkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed, int maxSegments) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();

        RoadNode root = RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        preloadChunksAlongPath(level, nodeMap.keySet(), straightPositions);

        RoadNetwork network = new RoadNetwork();
        placeGraph(level, network, random, root, new HashSet<>());

        BuildingGenerator.generateBuildings(level, network, nodeMap, straightPositions, random);;

        LOGGER.info("Generated road network with {} segments ({} nodes)", network.size(), nodeMap.size());
    }

    private static void placeGraph(
            ServerLevel level,
            RoadNetwork network,
            RandomSource random,
            RoadNode node,
            Set<BlockPos> placed
    ) {
        if (placed.contains(node.position())) {
            return;
        }
        placed.add(node.position());

        List<Direction> connections = node.getAllConnections();

        if (!connections.isEmpty()) {
            RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
            if (entry != null) {
                RoadPlacer.createAndPlaceRoad(level, network, entry.type(), node.position(), entry.rotation(), random);
            }
        }

        for (RoadNode child : node.children()) {
            Direction exitDir = RoadPlacer.guessDirection(node.position(), child.position());
            BlockPos currentPos = RoadNetwork.getNextPosition(node.position(), exitDir, GeneratorConfig.ROAD_STEP);
            Rotation sRotation = RoadPlacer.directionToRotation(exitDir);

            for (int i = 0; i < GeneratorConfig.STRAIGHT_BETWEEN_NODES; i++) {
                if (!network.hasSegmentAt(currentPos)) {
                    RoadPlacer.createAndPlaceRoad(level, network, "s", currentPos, sRotation, random);
                }
                currentPos = RoadNetwork.getNextPosition(currentPos, exitDir, GeneratorConfig.ROAD_STEP);
            }

            placeGraph(level, network, random, child, placed);
        }
    }

    private static void preloadChunksAlongPath(ServerLevel level, Set<BlockPos> nodePositions, Set<BlockPos> straightPositions) {
        Set<Long> chunkKeys = new HashSet<>();

        for (BlockPos pos : nodePositions) {
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

        for (BlockPos pos : straightPositions) {
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

        int totalChunks = chunkKeys.size();
        int totalSegments = nodePositions.size() + straightPositions.size();

        LOGGER.info("Preloading {} chunks for {} segments ({} nodes, {} straights)",
                totalChunks, totalSegments, nodePositions.size(), straightPositions.size());

        int loaded = 0;
        int logInterval = Math.max(1, totalChunks / 5);

        for (long key : chunkKeys) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            loaded++;

            if (loaded % logInterval == 0) {
                LOGGER.info("Loaded {} / {} chunks ({}%)", loaded, totalChunks, (loaded * 100) / totalChunks);
            }
        }

        LOGGER.info("Chunk preloading complete: {} chunks loaded", loaded);
    }
}