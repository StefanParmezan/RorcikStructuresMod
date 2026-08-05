package ru.rorcik.structures.generation.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.RoadConfig;
import ru.rorcik.structures.data.city.plains.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.RoadNode;
import ru.rorcik.structures.data.city.plains.RoadSegment;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoadGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROAD_STEP = 64;
    private static final int INITIAL_STRAIGHT_COUNT = 2;
    private static final int STRAIGHT_BETWEEN_NODES = 2;
    private static final int MAX_DEPTH = 7;
    private static final int MAX_NODES = 70;

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed, int maxSegments) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();

        RoadNode root = buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        preloadChunksAlongPath(level, nodeMap.keySet(), straightPositions);

        RoadNetwork network = new RoadNetwork();
        placeGraph(level, network, random, root, new HashSet<>());

        LOGGER.info("Generated road network with {} segments ({} nodes)", network.size(), nodeMap.size());
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

    private static RoadNode buildGraph(
            RandomSource random,
            BlockPos startPos,
            Direction direction,
            int depth,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions
    ) {
        if (depth > MAX_DEPTH || nodeMap.size() >= MAX_NODES) {
            RoadNode deadEnd = new RoadNode(startPos, direction, depth);
            deadEnd.markTerminal();
            return deadEnd;
        }

        if (straightPositions.contains(startPos)) {
            RoadNode deadEnd = new RoadNode(startPos, direction, depth);
            deadEnd.markTerminal();
            return deadEnd;
        }

        if (nodeMap.containsKey(startPos)) {
            RoadNode existing = nodeMap.get(startPos);
            existing.addEntry(direction);
            return existing;
        }

        int exitChoice = chooseExitWithDepth(random, depth);
        RoadNode node = new RoadNode(startPos, direction, depth);
        nodeMap.put(startPos, node);

        if (exitChoice == 0) {
            node.markTerminal();
            return node;
        }

        List<Direction> exits = getExitsForChoice(exitChoice, direction);
        if (exits.isEmpty()) {
            node.markTerminal();
            return node;
        }

        for (Direction exit : exits) {
            BlockPos currentPos = startPos;
            for (int i = 0; i < STRAIGHT_BETWEEN_NODES; i++) {
                currentPos = RoadNetwork.getNextPosition(currentPos, exit, ROAD_STEP);
                straightPositions.add(currentPos);
            }

            BlockPos childPos = RoadNetwork.getNextPosition(currentPos, exit, ROAD_STEP);

            RoadNode child = buildGraph(random, childPos, exit, depth + 1, nodeMap, straightPositions);

            if (child != node) {
                node.addExit(exit, child);
            }
        }

        return node;
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

        if (node.isTerminal() || connections.isEmpty()) {
            if (!connections.isEmpty()) {
                RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
                if (entry != null) {
                    createAndPlaceRoad(level, network, entry.type(), node.position(), entry.rotation(), random);
                } else {
                    placeStopRoad(level, network, random, node.position(), connections.get(0));
                }
            } else {
                placeStopRoad(level, network, random, node.position(), Direction.NORTH);
            }
        } else {
            RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
            if (entry != null) {
                createAndPlaceRoad(level, network, entry.type(), node.position(), entry.rotation(), random);
            } else {
                placeStopRoad(level, network, random, node.position(), connections.get(0));
            }
        }

        for (RoadNode child : node.children()) {
            Direction exitDir = guessDirection(node.position(), child.position());
            BlockPos currentPos = RoadNetwork.getNextPosition(node.position(), exitDir, ROAD_STEP);
            Rotation sRotation = directionToRotation(exitDir);

            for (int i = 0; i < STRAIGHT_BETWEEN_NODES; i++) {
                if (!network.hasSegmentAt(currentPos)) {
                    createAndPlaceRoad(level, network, "s", currentPos, sRotation, random);
                }
                currentPos = RoadNetwork.getNextPosition(currentPos, exitDir, ROAD_STEP);
            }

            placeGraph(level, network, random, child, placed);
        }
    }

    private static Direction guessDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static void placeStopRoad(
            ServerLevel level,
            RoadNetwork network,
            RandomSource random,
            BlockPos position,
            Direction incomingDir
    ) {
        Direction entryConnection = incomingDir.getOpposite();
        RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(List.of(entryConnection));
        if (entry != null) {
            createAndPlaceRoad(level, network, entry.type(), position, entry.rotation(), random);
        }
    }

    private static int chooseExitWithDepth(RandomSource random, int depth) {
        double endChance = (double) depth / MAX_DEPTH;

        if (random.nextDouble() < endChance) {
            return 0;
        }

        return 1 + random.nextInt(6);
    }

    private static List<Direction> getExitsForChoice(int exitChoice, Direction direction) {
        Direction straight = direction;
        Direction left = direction.getCounterClockWise();
        Direction right = direction.getClockWise();

        return switch (exitChoice) {
            case 1 -> List.of(right);
            case 2 -> List.of(left);
            case 3 -> List.of(right, straight);
            case 4 -> List.of(left, straight);
            case 5 -> List.of(right, straight, left);
            case 6 -> List.of(straight);
            default -> List.of();
        };
    }

    private static RoadSegment createAndPlaceRoad(
            ServerLevel level,
            RoadNetwork network,
            String type,
            BlockPos position,
            Rotation rotation,
            RandomSource random
    ) {
        RoadConfig config = RoadConfigLoader.getConfig(type);
        if (config == null) {
            LOGGER.error("Road config '{}' not found", type);
            return null;
        }

        if (network.hasSegmentAt(position)) {
            return null;
        }

        int decorCount = config.decorCount();
        int decor = decorCount > 0 ? random.nextInt(decorCount) : 0;
        List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);

        RoadSegment segment = new RoadSegment(type, position, rotation, decor, connections);

        if (!network.addSegment(segment)) {
            return null;
        }

        placeRoad(level, segment, config);
        return segment;
    }

    private static void placeRoad(ServerLevel level, RoadSegment segment, RoadConfig config) {
        String roadNbt = segment.type() + "_" + segment.decorVariant();
        String nbtPath = ModPaths.roadNbtPath(segment.type(), roadNbt);

        BlockPos roadOrigin = segment.position().offset(
                config.offset()[0],
                config.offset()[1],
                config.offset()[2]
        );

        BlockPos placementOrigin = StructureUtils.getCompensatedOrigin(
                roadOrigin,
                segment.rotation(),
                config.size()[0],
                config.size()[2]
        );

        StructureUtils.placeStructure(
                level,
                placementOrigin,
                nbtPath,
                segment.rotation(),
                false,
                false
        );
    }

    private static Rotation directionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static void generateSingleRoad(ServerLevel level, BlockPos origin, String type, Rotation rotation, int forcedDecor) {
        RoadConfig config = RoadConfigLoader.getConfig(type);
        if (config == null) {
            LOGGER.error("Road config '{}' not found", type);
            return;
        }

        int decorCount = config.decorCount();
        int decor = forcedDecor >= 0 ? forcedDecor : (decorCount > 0 ? level.getRandom().nextInt(decorCount) : 0);
        List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);

        RoadSegment segment = new RoadSegment(type, origin, rotation, decor, connections);
        placeRoad(level, segment, config);
    }
}