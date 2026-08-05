package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.config_loaders.BuildingConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildingSlotAllocator {
    public static List<BuildingSlot> allocateSlots(
            Map<BlockPos, RoadNode> nodeMap,
            RandomSource random
    ) {
        Map<BlockPos, BuildingSlot> occupiedSlots = new HashMap<>();
        List<BuildingSlot> slots = new ArrayList<>();

        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            RoadNode node = entry.getValue();
            BlockPos nodePos = node.position();
            List<Direction> connections = node.getAllConnections();

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                if (connections.contains(side)) continue;

                BlockPos buildingPos = RoadNetwork.getNextPosition(nodePos, side, GeneratorConfig.BUILDING_DISTANCE);

                if (occupiedSlots.containsKey(buildingPos)) continue;

                Rotation rotation = calculateRotation(side);
                String category = chooseCategory(random);
                String buildingName = chooseBuilding(random, category);

                if (buildingName == null) continue;

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
        }

        return slots;
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
        if (buildings.isEmpty()) return null;
        return buildings.get(random.nextInt(buildings.size()));
    }
}