package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParkPlacer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Set<BlockPos> placeParks(
            ServerLevel level,
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        Set<BlockPos> occupiedByParks = new HashSet<>();
        Set<BlockPos> roadSlots = collectAllRoadSlots(network, nodeMap, straightPositions);

        List<ParkConfig> parks = ParkConfigLoader.getParks();
        if (parks.isEmpty()) {
            return occupiedByParks;
        }

        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            RoadNode node = entry.getValue();
            BlockPos nodePos = node.position();
            List<Direction> connections = node.getAllConnections();

            if (node.isTerminal() && connections.size() == 1) {
                continue;
            }

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                if (connections.contains(side)) continue;

                ParkConfig park = chooseRandomPark(parks, random);
                if (park == null) continue;

                Rotation rotation = calculateParkRotation(side);

                Set<BlockPos> parkSlots = calculateParkSlots(nodePos, side, park, rotation);

                if (canPlacePark(parkSlots, roadSlots, occupiedByParks)) {
                    occupiedByParks.addAll(parkSlots);
                    placePark(level, nodePos, side, park, rotation);
                    LOGGER.info("Placed park '{}' at side {} (rotation: {})", park.name(), side, rotation);
                }
            }
        }

        LOGGER.info("Parks occupying {} slots", occupiedByParks.size());
        return occupiedByParks;
    }

    private static Set<BlockPos> collectAllRoadSlots(
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions
    ) {
        Set<BlockPos> occupied = new HashSet<>();

        for (RoadSegment segment : network.getSegments()) {
            occupied.add(segment.position());
        }

        occupied.addAll(nodeMap.keySet());
        occupied.addAll(straightPositions);

        return occupied;
    }

    private static boolean canPlacePark(
            Set<BlockPos> parkSlots,
            Set<BlockPos> roadSlots,
            Set<BlockPos> occupiedByParks
    ) {
        for (BlockPos pos : parkSlots) {
            if (roadSlots.contains(pos)) {
                return false;
            }
            if (occupiedByParks.contains(pos)) {
                return false;
            }
        }
        return true;
    }

    private static Rotation calculateParkRotation(Direction side) {
        return switch (side) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
    private static ParkConfig chooseRandomPark(List<ParkConfig> parks, RandomSource random) {
        if (parks.isEmpty()) return null;
        return parks.get(random.nextInt(parks.size()));
    }

    private static Set<BlockPos> calculateParkSlots(
            BlockPos nodePos,
            Direction side,
            ParkConfig park,
            Rotation rotation
    ) {
        Set<BlockPos> slots = new HashSet<>();

        int slotsX = park.size()[0] / GeneratorConfig.ROAD_STEP;
        int slotsZ = park.size()[2] / GeneratorConfig.ROAD_STEP;

        boolean rotated90 = (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90);

        if (rotated90) {
            int temp = slotsX;
            slotsX = slotsZ;
            slotsZ = temp;
        }

        Direction right = side.getClockWise();
        BlockPos parkOrigin = RoadNetwork.getNextPosition(nodePos, side, GeneratorConfig.BUILDING_DISTANCE * 2);

        Direction widthDir = rotated90 ? side : right;
        Direction lengthDir = rotated90 ? right : side;

        for (int x = 0; x < slotsX; x++) {
            for (int z = 0; z < slotsZ; z++) {
                BlockPos slotPos = parkOrigin
                        .offset(widthDir.getStepX() * x * GeneratorConfig.ROAD_STEP, 0, widthDir.getStepZ() * x * GeneratorConfig.ROAD_STEP)
                        .offset(lengthDir.getStepX() * z * GeneratorConfig.ROAD_STEP, 0, lengthDir.getStepZ() * z * GeneratorConfig.ROAD_STEP);
                slots.add(slotPos);
            }
        }

        return slots;
    }

    private static void placePark(
            ServerLevel level,
            BlockPos nodePos,
            Direction side,
            ParkConfig park,
            Rotation rotation
    ) {
        String nbtPath = ModPaths.parkNbtPath(park.name());

        BlockPos parkOrigin = RoadNetwork.getNextPosition(nodePos, side, GeneratorConfig.BUILDING_DISTANCE * 2);
        BlockPos origin = parkOrigin.offset(0, park.yOffset(), 0);

        BlockPos viewOffset = getViewOffset(park, rotation);
        origin = origin.offset(viewOffset);

        BlockPos placementOrigin = StructureUtils.getCompensatedOrigin(
                origin,
                rotation,
                park.size()[0],
                park.size()[2]
        );

        StructureUtils.placeStructure(
                level,
                placementOrigin,
                nbtPath,
                rotation,
                false,
                false
        );
    }

    private static BlockPos getViewOffset(ParkConfig park, Rotation rotation) {
        boolean isSquare = park.size()[0] == park.size()[2];

        if (isSquare) {
            return switch (rotation) {
                case NONE -> new BlockPos(0, 0, -64);
                case COUNTERCLOCKWISE_90 -> new BlockPos(-64, 0, 0);
                default -> BlockPos.ZERO;
            };
        }

        return switch (rotation) {
            case NONE -> new BlockPos(0, 0, -64);
            case CLOCKWISE_90 -> new BlockPos(+64, 0, 0);
            case CLOCKWISE_180 -> new BlockPos(0, 0, 64);
            case COUNTERCLOCKWISE_90 -> new BlockPos(-64, 0, 0);
        };
    }
}