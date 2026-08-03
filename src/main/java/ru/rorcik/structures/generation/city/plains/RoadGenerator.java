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
import ru.rorcik.structures.data.city.plains.RoadSegment;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.List;

public class RoadGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROAD_STEP = 64;

    public static void generateRoadLine(ServerLevel level, BlockPos origin, Direction direction, int count, long seed) {
        RoadConfig straightConfig = RoadConfigLoader.getConfig("s");
        if (straightConfig == null) {
            LOGGER.error("Road config 's' not found");
            return;
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        Rotation rotation = directionToRotation(direction);
        RoadNetwork network = new RoadNetwork();
        BlockPos currentPos = origin;

        BlockPos minBound = origin;
        BlockPos maxBound = origin;

        for (int i = 0; i < count; i++) {
            int decor = chooseDecor(straightConfig, random);
            List<Direction> connections = RoadNetwork.getRotatedConnections(straightConfig, rotation);

            RoadSegment segment = new RoadSegment(
                    straightConfig.type(),
                    currentPos,
                    rotation,
                    decor,
                    connections
            );

            if (!network.addSegment(segment)) {
                LOGGER.warn("Failed to place road segment at {}", currentPos);
                break;
            }

            placeRoad(level, segment, straightConfig);

            minBound = min(minBound, currentPos);
            maxBound = max(maxBound, currentPos.offset(straightConfig.size()[0] - 1, straightConfig.size()[1] - 1, straightConfig.size()[2] - 1));

            currentPos = RoadNetwork.getNextPosition(currentPos, direction, ROAD_STEP);
        }

        updateNeighborsAndLight(level, minBound, maxBound);
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

    private static int chooseDecor(RoadConfig config, RandomSource random) {
        List<Integer> stopVariants = config.stopVariants();
        if (stopVariants != null && !stopVariants.isEmpty()) {
            if (random.nextInt(3) == 0) {
                return stopVariants.get(random.nextInt(stopVariants.size()));
            }
        }
        return random.nextInt(config.decorCount());
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
}