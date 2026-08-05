package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.config_loaders.BuildingConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingSlotAllocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<BuildingSlot> allocateSlots(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        Map<BlockPos, BuildingSlot> occupiedSlots = new HashMap<>();
        List<BuildingSlot> slots = new ArrayList<>();

        allocateFromNodes(nodeMap, occupiedSlots, slots, random);
        allocateFromStraights(nodeMap, straightPositions, occupiedSlots, slots, random);

        LOGGER.info("Allocated {} building slots ({} from nodes, total nodes: {})",
                slots.size(), slots.size(), nodeMap.size());

        return slots;
    }

    private static void allocateFromNodes(
            Map<BlockPos, RoadNode> nodeMap,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random
    ) {
        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            RoadNode node = entry.getValue();
            BlockPos nodePos = node.position();
            List<Direction> connections = node.getAllConnections();

            Direction deadEndExcludedSide = null;
            if (node.isTerminal() && !connections.isEmpty()) {
                Direction entryDir = connections.get(0);
                deadEndExcludedSide = entryDir.getOpposite();
            }

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                if (connections.contains(side)) continue;
                if (side == deadEndExcludedSide) continue;

                tryAllocateSlot(nodePos, side, occupiedSlots, slots, random);
            }
        }
    }

    private static void allocateFromStraights(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random
    ) {
        for (BlockPos pos : straightPositions) {
            if (nodeMap.containsKey(pos)) continue;

            Direction roadDirection = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDirection == null) continue;

            Direction left = roadDirection.getCounterClockWise();
            Direction right = roadDirection.getClockWise();

            tryAllocateSlot(pos, left, occupiedSlots, slots, random);
            tryAllocateSlot(pos, right, occupiedSlots, slots, random);
        }
    }

    private static Direction detectRoadDirection(
            BlockPos pos,
            Set<BlockPos> straightPositions,
            Set<BlockPos> nodePositions
    ) {
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = RoadNetwork.getNextPosition(pos, dir, GeneratorConfig.ROAD_STEP);
            if (straightPositions.contains(neighbor) || nodePositions.contains(neighbor)) {
                return dir;
            }
        }
        return null;
    }

    private static void tryAllocateSlot(
            BlockPos nodePos,
            Direction side,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random
    ) {
        BlockPos buildingPos = RoadNetwork.getNextPosition(nodePos, side, GeneratorConfig.BUILDING_DISTANCE);

        if (occupiedSlots.containsKey(buildingPos)) return;

        Rotation rotation = calculateRotation(side);
        String category = chooseCategory(random);
        String buildingName = chooseBuilding(random, category);

        if (buildingName == null) {
            LOGGER.warn("No building found for category: {}", category);
            return;
        }

        BuildingSlot slot = new BuildingSlot(
                buildingPos,
                rotation,
                side,
                category,
                buildingName
        );

        slots.add(slot);
        occupiedSlots.put(buildingPos, slot);
    }

    private static Rotation calculateRotation(Direction roadSide) {
        return switch (roadSide) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.NONE;
            case NORTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
    }

    private static String chooseCategory(RandomSource random) {
        int roll = random.nextInt(3);
        return switch (roll) {
            case 0 -> "houses";
            case 1 -> "lootable";
            default -> "other";
        };
    }

    private static String chooseBuilding(RandomSource random, String category) {
        List<String> buildings = BuildingConfigLoader.getBuildings(category);
        if (buildings.isEmpty()) {
            LOGGER.error("No buildings found in category: {}", category);
            return null;
        }
        return buildings.get(random.nextInt(buildings.size()));
    }
}