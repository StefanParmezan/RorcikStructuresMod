package ru.rorcik.structures.generation.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.RoadConfig;
import ru.rorcik.structures.data.city.plains.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.RoadNode;
import ru.rorcik.structures.data.city.plains.RoadSegment;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
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
    private static final int MAX_DEPTH = 8;

    private static final int FORCED_DEAD_END = -1;

    private record RoadMapEntry(String type, Rotation rotation) {}

    private static Map<Set<Direction>, RoadMapEntry> roadMap;

    public static void generateRoadNetwork(ServerLevel level, BlockPos origin, long seed, int maxSegments) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        buildRoadMap();

        Set<BlockPos> occupied = new HashSet<>();
        RoadNode root = buildGraph(random, origin, Direction.SOUTH, 0, occupied);

        RoadNetwork network = new RoadNetwork();
        placeGraph(level, network, random, root);

        BlockPos minBound = origin;
        BlockPos maxBound = origin;
        for (RoadSegment segment : network.getSegments()) {
            minBound = min(minBound, segment.position());
            maxBound = max(maxBound, segment.position().offset(63, 63, 63));
        }

        updateNeighborsAndLight(level, minBound, maxBound);
        LOGGER.info("Generated road network with {} segments", network.size());
    }

    private static void buildRoadMap() {
        roadMap = new HashMap<>();

        for (RoadConfig config : RoadConfigLoader.getAllConfigs().values()) {
            for (Rotation rotation : Rotation.values()) {
                List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);
                Set<Direction> key = new HashSet<>(connections);

                if (!roadMap.containsKey(key)) {
                    roadMap.put(key, new RoadMapEntry(config.type(), rotation));
                }
            }
        }

        LOGGER.info("Road map built with {} entries", roadMap.size());
    }

    private static RoadMapEntry findRoadForConnections(List<Direction> connections) {
        Set<Direction> key = new HashSet<>(connections);
        return roadMap.get(key);
    }

    private static RoadNode buildGraph(RandomSource random, BlockPos startPos, Direction direction, int depth, Set<BlockPos> occupied) {
        if (depth > MAX_DEPTH) {
            return new RoadNode(startPos, direction, FORCED_DEAD_END, depth);
        }

        if (occupied.contains(startPos)) {
            return new RoadNode(startPos, direction, FORCED_DEAD_END, depth);
        }

        occupied.add(startPos);

        int exitChoice = chooseExitWithDepth(random, depth);
        RoadNode node = new RoadNode(startPos, direction, exitChoice, depth);

        if (exitChoice == 0) {
            return node;
        }

        List<Direction> exits = getExitsForChoice(exitChoice, direction);
        if (exits.isEmpty()) {
            return node;
        }

        for (Direction exit : exits) {
            BlockPos currentPos = startPos;
            for (int i = 0; i < STRAIGHT_BETWEEN_NODES; i++) {
                currentPos = RoadNetwork.getNextPosition(currentPos, exit, ROAD_STEP);
                occupied.add(currentPos);
            }

            BlockPos childPos = RoadNetwork.getNextPosition(currentPos, exit, ROAD_STEP);

            if (!occupied.contains(childPos)) {
                RoadNode child = buildGraph(random, childPos, exit, depth + 1, occupied);
                node.addChild(child);
            }
        }

        return node;
    }

    private static void placeGraph(ServerLevel level, RoadNetwork network, RandomSource random, RoadNode node) {
        if (node.exitChoice() == FORCED_DEAD_END) {
            return;
        } else if (node.exitChoice() == 0) {
            Direction opposite = node.incoming().getOpposite();
            RoadMapEntry entry = findRoadForConnections(List.of(opposite));
            if (entry != null) {
                createAndPlaceRoad(level, network, entry.type(), node.position(), entry.rotation(), random);
            }
        } else {
            // Обычный узел
            Direction entryDir = node.incoming().getOpposite();
            List<Direction> exits = getExitsForChoice(node.exitChoice(), node.incoming());

            List<Direction> connections = new ArrayList<>();
            connections.add(entryDir);
            connections.addAll(exits);

            RoadMapEntry entry = findRoadForConnections(connections);
            if (entry != null) {
                createAndPlaceRoad(level, network, entry.type(), node.position(), entry.rotation(), random);
            }
        }

        for (RoadNode child : node.children()) {
            Direction exitDir = child.incoming();
            BlockPos currentPos = RoadNetwork.getNextPosition(node.position(), exitDir, ROAD_STEP);
            Rotation sRotation = directionToRotation(exitDir);

            for (int i = 0; i < STRAIGHT_BETWEEN_NODES; i++) {
                if (!network.hasSegmentAt(currentPos)) {
                    createAndPlaceRoad(level, network, "s", currentPos, sRotation, random);
                }
                currentPos = RoadNetwork.getNextPosition(currentPos, exitDir, ROAD_STEP);
            }

            placeGraph(level, network, random, child);
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

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
    }

    private static void updateNeighborsAndLight(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                level.blockUpdated(pos, state.getBlock());
                if (state.getLightEmission(level, pos) > 0) {
                    level.getLightEngine().checkBlock(pos);
                }
            }
        }
    }

    public static void generateSingleRoad(ServerLevel level, BlockPos origin, String type, Rotation rotation, int forcedDecor) {
        RoadConfig config = RoadConfigLoader.getConfig(type);
        if (config == null) {
            LOGGER.error("Road config '{}' not found", type);
            return;
        }

        int decor = forcedDecor >= 0 ? forcedDecor : level.getRandom().nextInt(config.decorCount());
        List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);

        RoadSegment segment = new RoadSegment(type, origin, rotation, decor, connections);
        placeRoad(level, segment, config);

        BlockPos maxPos = origin.offset(config.size()[0] - 1, config.size()[1] - 1, config.size()[2] - 1);
        updateNeighborsAndLight(level, origin, maxPos);
    }
}