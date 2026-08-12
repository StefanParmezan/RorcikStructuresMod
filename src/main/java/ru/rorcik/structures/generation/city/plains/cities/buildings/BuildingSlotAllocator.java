package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.data.city.plains.config_loaders.BuildingConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingSlotAllocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PLATFORM_NAME = "platform_0";
    private static final String PLATFORM_CATEGORY = "platforms";
    private static final String PARKS_CATEGORY = "parks";

    public static List<BuildingSlot> allocateSlots(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            Set<BlockPos> parkOccupiedSlots,
            RandomSource random
    ) {
        Map<BlockPos, BuildingSlot> occupiedSlots = new HashMap<>();
        List<BuildingSlot> slots = new ArrayList<>();

        Set<BlockPos> roadPositions = collectRoadPositions(nodeMap, straightPositions);

        for (BlockPos parkPos : parkOccupiedSlots) {
            occupiedSlots.put(parkPos, null);
        }

        allocateFromNodes(nodeMap, occupiedSlots, slots, random, roadPositions);
        allocateFromStraights(nodeMap, straightPositions, occupiedSlots, slots, random, roadPositions);

        int layer1Count = slots.size();
        LOGGER.info("[SLOTS] Layer 1: {} slots allocated", layer1Count);

        List<BuildingSlot> currentLayer = new ArrayList<>(slots);
        for (int depth = 1; depth < GeneratorConfig.BUILDING_MAX_DEPTH; depth++) {
            int beforeCount = slots.size();
            List<BuildingSlot> nextLayer = allocateNextLayer(currentLayer, occupiedSlots, slots, random, depth, roadPositions);
            int addedCount = slots.size() - beforeCount;

            LOGGER.info("[SLOTS] Layer {}: {} new slots added (total: {})",
                    depth + 1, addedCount, slots.size());

            currentLayer = nextLayer;
        }

        List<BuildingSlot> allBuildings = new ArrayList<>(slots);
        allocatePlatforms(allBuildings, slots, occupiedSlots, roadPositions, parkOccupiedSlots);

        allocatePlatformsAlongStraights(straightPositions, nodeMap, slots, occupiedSlots, roadPositions, parkOccupiedSlots);

        int platformCount = 0;
        int parkCount = 0;
        int buildingCount = 0;
        int squareParkCount = 0;
        int rectParkCount = 0;

        for (BuildingSlot slot : slots) {
            if (slot.category().equals(PLATFORM_CATEGORY)) {
                platformCount++;
            } else if (slot.category().equals(PARKS_CATEGORY)) {
                parkCount++;
                ParkConfig park = ParkConfigLoader.getPark(slot.buildingName());
                if (park != null && park.size()[0] == park.size()[2]) {
                    squareParkCount++;
                } else {
                    rectParkCount++;
                }
            } else {
                buildingCount++;
            }
        }

        LOGGER.info("[SLOTS] Final breakdown: buildings={}, parks={} (square={}, rect={}), platforms={}, total={}",
                buildingCount, parkCount, squareParkCount, rectParkCount, platformCount, slots.size());

        return slots;
    }

    private static Set<BlockPos> collectRoadPositions(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions
    ) {
        Set<BlockPos> roads = new HashSet<>(nodeMap.keySet());
        roads.addAll(straightPositions);

        for (RoadNode node : nodeMap.values()) {
            for (Direction conn : node.getAllConnections()) {
                BlockPos adjacent = node.position().relative(conn, GeneratorConfig.ROAD_STEP);
                roads.add(adjacent);
            }
        }

        return roads;
    }

    private static void allocateFromNodes(
            Map<BlockPos, RoadNode> nodeMap,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions
    ) {
        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            RoadNode node = entry.getValue();
            BlockPos nodePos = node.position();
            List<Direction> connections = node.getAllConnections();

            if (node.isTerminal() && connections.size() == 1) {
                continue;
            }

            Direction deadEndExcludedSide = null;
            if (node.isTerminal() && !connections.isEmpty()) {
                Direction entryDir = connections.get(0);
                deadEndExcludedSide = entryDir.getOpposite();
            }

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                if (connections.contains(side)) continue;
                if (side == deadEndExcludedSide) continue;

                tryAllocateSlot(nodePos, side, occupiedSlots, slots, random, roadPositions);
            }
        }
    }

    private static void allocateFromStraights(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions
    ) {
        for (BlockPos pos : straightPositions) {
            if (nodeMap.containsKey(pos)) continue;

            Direction roadDirection = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDirection == null) continue;

            Direction left = roadDirection.getCounterClockWise();
            Direction right = roadDirection.getClockWise();

            tryAllocateSlot(pos, left, occupiedSlots, slots, random, roadPositions);
            tryAllocateSlot(pos, right, occupiedSlots, slots, random, roadPositions);
        }
    }

    private static List<BuildingSlot> allocateNextLayer(
            List<BuildingSlot> previousLayer,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            int currentDepth,
            Set<BlockPos> roadPositions
    ) {
        List<BuildingSlot> nextLayer = new ArrayList<>();

        int skippedPlatform = 0;
        int skippedPark = 0;
        int skippedMaxDepth = 0;
        int skippedOccupied = 0;
        int skippedRoad = 0;
        int added = 0;

        for (BuildingSlot prevSlot : previousLayer) {
            if (prevSlot.category().equals(PLATFORM_CATEGORY)) {
                skippedPlatform++;
                continue;
            }
            if (prevSlot.category().equals(PARKS_CATEGORY)) {
                skippedPark++;
                continue;
            }
            if (currentDepth >= prevSlot.maxDepth()) {
                skippedMaxDepth++;
                continue;
            }

            Direction roadSide = prevSlot.roadSide();
            BlockPos nextLayerPos = RoadNetwork.getNextPosition(prevSlot.position(), roadSide, GeneratorConfig.BUILDING_DISTANCE);

            if (occupiedSlots.containsKey(nextLayerPos)) {
                skippedOccupied++;
                continue;
            }

            if (roadPositions.contains(nextLayerPos)) {
                skippedRoad++;
                continue;
            }

            Rotation nextRotation = calculateOutwardRotation(roadSide);
            String category = chooseCategory(random);

            if (category.equals(PARKS_CATEGORY)) {
                category = "houses";
            }

            String buildingName = chooseBuilding(random, category);
            if (buildingName == null) continue;

            BuildingSlot nextSlot = new BuildingSlot(
                    nextLayerPos,
                    nextRotation,
                    roadSide,
                    category,
                    buildingName,
                    prevSlot.maxDepth()
            );

            nextLayer.add(nextSlot);
            slots.add(nextSlot);
            occupiedSlots.put(nextLayerPos, nextSlot);
            added++;
        }

        LOGGER.info("[LAYER-{}] Previous: {}, Added: {}, Skipped: platform={}, park={}, maxDepth={}, occupied={}, road={}",
                currentDepth + 1, previousLayer.size(), added,
                skippedPlatform, skippedPark, skippedMaxDepth, skippedOccupied, skippedRoad);

        return nextLayer;
    }

    private static void allocatePlatforms(
            List<BuildingSlot> allBuildings,
            List<BuildingSlot> slots,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            Set<BlockPos> roadPositions,
            Set<BlockPos> parkOccupiedSlots
    ) {
        int platformsAdded = 0;
        int skippedByBuilding = 0;
        int skippedByRoad = 0;
        int skippedByPark = 0;
        int skippedByPlatform = 0;

        for (BuildingSlot building : allBuildings) {
            if (building.category().equals(PLATFORM_CATEGORY)) continue;
            if (building.category().equals(PARKS_CATEGORY)) continue;

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;

                BlockPos platformPos = RoadNetwork.getNextPosition(building.position(), side, GeneratorConfig.BUILDING_DISTANCE);

                if (occupiedSlots.containsKey(platformPos)) {
                    BuildingSlot existing = occupiedSlots.get(platformPos);
                    if (existing == null || existing.category().equals(PARKS_CATEGORY)) {
                        skippedByPark++;
                    } else if (existing.category().equals(PLATFORM_CATEGORY)) {
                        skippedByPlatform++;
                    } else {
                        skippedByBuilding++;
                    }
                    continue;
                }

                if (roadPositions.contains(platformPos)) {
                    skippedByRoad++;
                    continue;
                }

                if (parkOccupiedSlots.contains(platformPos)) {
                    skippedByPark++;
                    continue;
                }

                Rotation platformRotation = calculateOutwardRotation(side);

                BuildingSlot platformSlot = new BuildingSlot(
                        platformPos,
                        platformRotation,
                        side,
                        PLATFORM_CATEGORY,
                        PLATFORM_NAME,
                        0
                );

                slots.add(platformSlot);
                occupiedSlots.put(platformPos, platformSlot);
                platformsAdded++;
            }
        }

        LOGGER.info("[PLATFORMS-BUILDING] Added: {} | Skipped: building={}, road={}, park={}, platform={}",
                platformsAdded, skippedByBuilding, skippedByRoad, skippedByPark, skippedByPlatform);
    }

    private static void allocatePlatformsAlongStraights(
            Set<BlockPos> straightPositions,
            Map<BlockPos, RoadNode> nodeMap,
            List<BuildingSlot> slots,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            Set<BlockPos> roadPositions,
            Set<BlockPos> parkOccupiedSlots
    ) {
        int added = 0;
        int skippedOccupied = 0;
        int skippedRoad = 0;
        int skippedPark = 0;

        for (BlockPos pos : straightPositions) {
            if (nodeMap.containsKey(pos)) continue;

            Direction roadDirection = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDirection == null) continue;

            Direction left = roadDirection.getCounterClockWise();
            Direction right = roadDirection.getClockWise();

            for (Direction side : new Direction[]{left, right}) {
                BlockPos platformPos = RoadNetwork.getNextPosition(pos, side, GeneratorConfig.BUILDING_DISTANCE);

                if (occupiedSlots.containsKey(platformPos)) {
                    skippedOccupied++;
                    continue;
                }

                if (roadPositions.contains(platformPos)) {
                    skippedRoad++;
                    continue;
                }

                if (parkOccupiedSlots.contains(platformPos)) {
                    skippedPark++;
                    continue;
                }

                Rotation platformRotation = calculateOutwardRotation(side);

                BuildingSlot platformSlot = new BuildingSlot(
                        platformPos,
                        platformRotation,
                        side,
                        PLATFORM_CATEGORY,
                        PLATFORM_NAME,
                        0
                );

                slots.add(platformSlot);
                occupiedSlots.put(platformPos, platformSlot);
                added++;
            }
        }

        LOGGER.info("[PLATFORMS-STRAIGHT] Added: {} | Skipped: occupied={}, road={}, park={}",
                added, skippedOccupied, skippedRoad, skippedPark);
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
            RandomSource random,
            Set<BlockPos> roadPositions
    ) {
        BlockPos buildingPos = RoadNetwork.getNextPosition(nodePos, side, GeneratorConfig.BUILDING_DISTANCE);

        if (occupiedSlots.containsKey(buildingPos)) return;

        if (roadPositions.contains(buildingPos)) {
            return;
        }

        String category = chooseCategory(random);

        if (category.equals(PARKS_CATEGORY) && GeneratorConfig.SPAWN_PARKS) {
            if (tryAllocatePark(nodePos, side, buildingPos, occupiedSlots, slots, random, roadPositions)) {
                return;
            }
            category = "houses";
        }

        String buildingName = chooseBuilding(random, category);
        if (buildingName == null) {
            LOGGER.warn("No building found for category: {}", category);
            return;
        }

        Rotation rotation = calculateBuildingRotation(side);
        int maxDepth = GeneratorConfig.BUILDING_MIN_DEPTH + random.nextInt(
                GeneratorConfig.BUILDING_MAX_DEPTH - GeneratorConfig.BUILDING_MIN_DEPTH + 1
        );

        BuildingSlot slot = new BuildingSlot(
                buildingPos,
                rotation,
                side,
                category,
                buildingName,
                maxDepth
        );

        slots.add(slot);
        occupiedSlots.put(buildingPos, slot);
    }

    private static boolean tryAllocatePark(
            BlockPos nodePos,
            Direction side,
            BlockPos buildingPos,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions
    ) {
        List<ParkConfig> parks = ParkConfigLoader.getParks();
        List<ParkConfig> availableParks = ParkPlacer.filterParks(parks);

        if (availableParks.isEmpty()) {
            return false;
        }

        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ParkConfig park = availableParks.get(random.nextInt(availableParks.size()));
            boolean isSquare = park.size()[0] == park.size()[2];
            Rotation rotation = ParkPlacer.calculateRotationForSide(side);

            Set<BlockPos> parkSlots = ParkPlacer.calculateSlots(nodePos, side, park, rotation);

            boolean canPlace = true;
            int conflicts = 0;
            for (BlockPos slot : parkSlots) {
                if (occupiedSlots.containsKey(slot) || roadPositions.contains(slot)) {
                    canPlace = false;
                    conflicts++;
                }
            }

            if (canPlace) {
                BuildingSlot parkSlot = new BuildingSlot(
                        buildingPos,
                        rotation,
                        side,
                        PARKS_CATEGORY,
                        park.name(),
                        0
                );

                slots.add(parkSlot);
                for (BlockPos slot : parkSlots) {
                    occupiedSlots.put(slot, parkSlot);
                }

                LOGGER.info("[PARK-ALLOC] Placed '{}' ({}) at {} side {} ({} slots, attempt {})",
                        park.name(), isSquare ? "SQUARE" : "RECT",
                        nodePos.toShortString(), side, parkSlots.size(), attempt + 1);
                return true;
            }

            LOGGER.debug("[PARK-ALLOC] Attempt {}: '{}' ({}) conflicts at {} side {} ({} conflicts)",
                    attempt + 1, park.name(), isSquare ? "SQUARE" : "RECT",
                    nodePos.toShortString(), side, conflicts);
        }

        LOGGER.info("[PARK-ALLOC] All {} attempts failed at {} side {}, falling back to building",
                maxAttempts, nodePos.toShortString(), side);
        return false;
    }

    private static String chooseCategory(RandomSource random) {
        int roll = random.nextInt(100);

        if (roll < GeneratorConfig.HOUSES_CHANCE) {
            return "houses";
        } else if (roll < GeneratorConfig.HOUSES_CHANCE + GeneratorConfig.OTHER_CHANCE) {
            return "other";
        } else if (roll < GeneratorConfig.HOUSES_CHANCE + GeneratorConfig.OTHER_CHANCE + GeneratorConfig.LOOTABLE_CHANCE) {
            return "lootable";
        } else {
            return PARKS_CATEGORY;
        }
    }

    private static String chooseBuilding(RandomSource random, String category) {
        List<String> buildings = BuildingConfigLoader.getBuildings(category);
        if (buildings.isEmpty()) {
            LOGGER.error("No buildings found in category: {}", category);
            return null;
        }
        return buildings.get(random.nextInt(buildings.size()));
    }

    private static Rotation calculateBuildingRotation(Direction roadSide) {
        return switch (roadSide) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.NONE;
            case NORTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
    }

    private static Rotation calculateOutwardRotation(Direction side) {
        return switch (side) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case SOUTH -> Rotation.NONE;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}