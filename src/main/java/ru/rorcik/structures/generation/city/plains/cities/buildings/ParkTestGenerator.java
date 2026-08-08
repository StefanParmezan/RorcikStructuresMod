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
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadPlacer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParkTestGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROAD_LENGTH = 5;

    public static void generateTestPark(
            ServerLevel level,
            BlockPos origin,
            long seed,
            Direction roadDirection,
            int parkIndex
    ) {
        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        RoadNetwork network = new RoadNetwork();
        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();

        Rotation sRotation = RoadPlacer.directionToRotation(roadDirection);

        BlockPos currentPos = origin;
        for (int i = 0; i < ROAD_LENGTH; i++) {
            RoadPlacer.createAndPlaceRoad(level, network, "s", currentPos, sRotation, random);
            straightPositions.add(currentPos);
            currentPos = RoadNetwork.getNextPosition(currentPos, roadDirection, GeneratorConfig.ROAD_STEP);
        }

        int middleIndex = ROAD_LENGTH / 2;
        BlockPos middlePos = origin.offset(
                roadDirection.getStepX() * middleIndex * GeneratorConfig.ROAD_STEP,
                0,
                roadDirection.getStepZ() * middleIndex * GeneratorConfig.ROAD_STEP
        );

        RoadNode testNode = new RoadNode(middlePos, roadDirection, 0);
        testNode.addExit(roadDirection, null);
        nodeMap.put(middlePos, testNode);

        List<ParkConfig> parks = ParkConfigLoader.getParks();
        if (parks.isEmpty()) {
            LOGGER.warn("No park configs loaded");
            return;
        }

        if (parkIndex < 0 || parkIndex >= parks.size()) {
            LOGGER.warn("Park index {} out of range (0-{})", parkIndex, parks.size() - 1);
            return;
        }

        ParkConfig park = parks.get(parkIndex);

        Direction parkSide = roadDirection.getClockWise();
        Rotation rotation = calculateParkRotation(parkSide);

        Set<BlockPos> parkSlots = calculateTestSlots(middlePos, parkSide, park, rotation);
        Set<BlockPos> roadSlots = collectRoadSlots(network, nodeMap, straightPositions);

        boolean canPlace = true;
        for (BlockPos slotPos : parkSlots) {
            if (roadSlots.contains(slotPos)) {
                LOGGER.warn("Park slot {} overlaps with road", slotPos.toShortString());
                canPlace = false;
                break;
            }
        }

        if (canPlace) {
            placeTestPark(level, middlePos, parkSide, park, rotation);
            LOGGER.info("Test park '{}' placed at side {} (rotation: {})", park.name(), parkSide, rotation);
        } else {
            LOGGER.warn("Cannot place park '{}' - slots occupied by roads", park.name());
        }
    }

    private static Set<BlockPos> collectRoadSlots(
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

    private static Set<BlockPos> calculateTestSlots(
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

    private static void placeTestPark(
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

    private static Rotation calculateParkRotation(Direction side) {
        return switch (side) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}