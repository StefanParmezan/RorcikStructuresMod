package ru.rorcik.structures.generation.city.plains.cities.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.List;

public class RoadPlacer {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;

    static Direction guessDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static RoadSegment createAndPlaceRoad(
            ServerLevel level,
            RoadNetwork network,
            String type,
            BlockPos position,
            Rotation rotation,
            RandomSource random,
            int forcedDecor
    ) {
        RoadConfig config = RoadConfigLoader.getConfig(type);
        if (config == null) return null;
        if (network.hasSegmentAt(position)) return null;

        int decorCount = config.decorCount();
        int decor;
        if (forcedDecor >= 0 && forcedDecor < decorCount) {
            decor = forcedDecor;
        } else {
            decor = decorCount > 0 ? random.nextInt(decorCount) : 0;
        }

        List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);
        RoadSegment segment = new RoadSegment(type, position, rotation, decor, connections);
        if (!network.addSegment(segment)) return null;

        placeRoad(level, segment, config);
        return segment;
    }

    static void placeRoad(ServerLevel level, RoadSegment segment, RoadConfig config) {
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

    public static Rotation directionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static RoadSegment generateSingleRoad(ServerLevel level, BlockPos origin, String type, Rotation rotation, int forcedDecor) {
        RoadConfig config = RoadConfigLoader.getConfig(type);
        if (config == null) {
            LOGGER.error("Error: Road config '{}' not found", type);
            return null;
        }
        int decorCount = config.decorCount();
        int decor = forcedDecor >= 0 ? forcedDecor : (decorCount > 0 ? level.getRandom().nextInt(decorCount) : 0);
        List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);
        RoadSegment segment = new RoadSegment(type, origin, rotation, decor, connections);
        placeRoad(level, segment, config);
        return segment;
    }
}