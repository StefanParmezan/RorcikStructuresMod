package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.config_loaders.BuildingConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingSlotAllocator {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final String PLATFORM_NAME = "platform_0";
    private static final String PLATFORM_CATEGORY = "platforms";
    private static final String PARKS_CATEGORY = "parks";

    public static List<BuildingSlot> allocateSlots(
            ServerLevel level,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            Set<BlockPos> parkOccupiedSlots,
            RandomSource random,
            ParkWorldTracker tracker
    ) {
        Map<BlockPos, BuildingSlot> occupiedSlots = new HashMap<>();
        List<BuildingSlot> slots = new ArrayList<>();
        Set<BlockPos> roadPositions = collectRoadPositions(nodeMap, straightPositions);

        for (BlockPos parkPos : parkOccupiedSlots) {
            occupiedSlots.put(parkPos, null);
        }

        allocateFromNodes(level, nodeMap, occupiedSlots, slots, random, roadPositions, tracker);
        allocateFromStraights(level, nodeMap, straightPositions, occupiedSlots, slots, random, roadPositions, tracker);

        int layer1Count = slots.size();
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SLOTS] Layer 1: {} slots allocated", layer1Count);
        }

        List<BuildingSlot> currentLayer = new ArrayList<>(slots);
        for (int depth = 1; depth < GeneratorConfig.BUILDING_MAX_DEPTH; depth++) {
            int beforeCount = slots.size();
            List<BuildingSlot> nextLayer = allocateNextLayer(currentLayer, occupiedSlots, slots, random, depth, roadPositions, tracker);
            int addedCount = slots.size() - beforeCount;
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [SLOTS] Layer {}: {} new slots added (total: {})",
                        depth + 1, addedCount, slots.size());
            }
            currentLayer = nextLayer;
        }

        List<BuildingSlot> allBuildings = new ArrayList<>(slots);
        allocatePlatforms(allBuildings, slots, occupiedSlots, roadPositions, parkOccupiedSlots);
        allocatePlatformsAlongStraights(straightPositions, nodeMap, slots, occupiedSlots, roadPositions, parkOccupiedSlots);

        int platformCount = 0, parkCount = 0, buildingCount = 0;
        int squareParkCount = 0, rectParkCount = 0;
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

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [SLOTS] Final breakdown: buildings={}, parks={} (square={}, rect={}), platforms={}, total={}",
                    buildingCount, parkCount, squareParkCount, rectParkCount, platformCount, slots.size());
        }
        return slots;
    }

    private static Set<BlockPos> collectRoadPositions(
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions
    ) {
        Set<BlockPos> roads = new HashSet<>(nodeMap.keySet());
        roads.addAll(straightPositions);
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        for (RoadNode node : nodeMap.values()) {
            for (Direction conn : node.getAllConnections()) {
                mutPos.set(node.position()).move(conn, GeneratorConfig.ROAD_STEP);
                roads.add(mutPos.immutable());
            }
        }
        return roads;
    }

    private static void allocateFromNodes(
            ServerLevel level,
            Map<BlockPos, RoadNode> nodeMap,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions,
            ParkWorldTracker tracker
    ) {
        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            RoadNode node = entry.getValue();
            BlockPos nodePos = node.position();
            List<Direction> connections = node.getAllConnections();
            if (node.isTerminal() && connections.size() == 1) continue;

            Direction deadEndExcludedSide = null;
            if (node.isTerminal() && !connections.isEmpty()) {
                Direction entryDir = connections.get(0);
                deadEndExcludedSide = entryDir.getOpposite();
            }

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                if (connections.contains(side)) continue;
                if (side == deadEndExcludedSide) continue;
                tryAllocateSlot(level, nodePos, side, occupiedSlots, slots, random, roadPositions, tracker);
            }
        }
    }

    private static void allocateFromStraights(
            ServerLevel level,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions,
            ParkWorldTracker tracker
    ) {
        for (BlockPos pos : straightPositions) {
            if (nodeMap.containsKey(pos)) continue;
            Direction roadDirection = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDirection == null) continue;
            Direction left = roadDirection.getCounterClockWise();
            Direction right = roadDirection.getClockWise();
            tryAllocateSlot(level, pos, left, occupiedSlots, slots, random, roadPositions, tracker);
            tryAllocateSlot(level, pos, right, occupiedSlots, slots, random, roadPositions, tracker);
        }
    }

    private static List<BuildingSlot> allocateNextLayer(
            List<BuildingSlot> previousLayer,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            int currentDepth,
            Set<BlockPos> roadPositions,
            ParkWorldTracker tracker
    ) {
        List<BuildingSlot> nextLayer = new ArrayList<>();
        int skippedPlatform = 0, skippedPark = 0, skippedMaxDepth = 0;
        int skippedOccupied = 0, skippedRoad = 0, added = 0;
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (BuildingSlot prevSlot : previousLayer) {
            if (prevSlot.category().equals(PLATFORM_CATEGORY)) { skippedPlatform++; continue; }
            if (prevSlot.category().equals(PARKS_CATEGORY)) { skippedPark++; continue; }
            if (currentDepth >= prevSlot.maxDepth()) { skippedMaxDepth++; continue; }

            Direction roadSide = prevSlot.roadSide();
            mutPos.set(prevSlot.position()).move(roadSide, GeneratorConfig.BUILDING_DISTANCE);
            BlockPos nextLayerPos = mutPos.immutable();

            if (occupiedSlots.containsKey(nextLayerPos)) { skippedOccupied++; continue; }
            if (roadPositions.contains(nextLayerPos)) { skippedRoad++; continue; }

            Rotation nextRotation = calculateOutwardRotation(roadSide);
            String category = chooseCategory(random);
            if (category.equals(PARKS_CATEGORY)) category = "houses";
            String buildingName = chooseBuilding(random, category);
            if (buildingName == null) continue;

            BuildingSlot nextSlot = new BuildingSlot(
                    nextLayerPos, nextRotation, roadSide, category, buildingName, prevSlot.maxDepth()
            );
            nextLayer.add(nextSlot);
            slots.add(nextSlot);
            occupiedSlots.put(nextLayerPos, nextSlot);
            added++;
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [LAYER-{}] Previous: {}, Added: {}, Skipped: platform={}, park={}, maxDepth={}, occupied={}, road={}",
                    currentDepth + 1, previousLayer.size(), added,
                    skippedPlatform, skippedPark, skippedMaxDepth, skippedOccupied, skippedRoad);
        }
        return nextLayer;
    }

    private static void allocatePlatforms(
            List<BuildingSlot> allBuildings,
            List<BuildingSlot> slots,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            Set<BlockPos> roadPositions,
            Set<BlockPos> parkOccupiedSlots
    ) {
        int platformsAdded = 0, skippedByBuilding = 0, skippedByRoad = 0;
        int skippedByPark = 0, skippedByPlatform = 0;
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (BuildingSlot building : allBuildings) {
            if (building.category().equals(PLATFORM_CATEGORY)) continue;
            if (building.category().equals(PARKS_CATEGORY)) continue;

            for (Direction side : Direction.values()) {
                if (side.getAxis().isVertical()) continue;
                mutPos.set(building.position()).move(side, GeneratorConfig.BUILDING_DISTANCE);
                BlockPos platformPos = mutPos.immutable();

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
                if (roadPositions.contains(platformPos)) { skippedByRoad++; continue; }
                if (parkOccupiedSlots.contains(platformPos)) { skippedByPark++; continue; }

                Rotation platformRotation = calculateOutwardRotation(side);
                BuildingSlot platformSlot = new BuildingSlot(
                        platformPos, platformRotation, side, PLATFORM_CATEGORY, PLATFORM_NAME, 0
                );
                slots.add(platformSlot);
                occupiedSlots.put(platformPos, platformSlot);
                platformsAdded++;
            }
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [PLATFORMS-BUILDING] Added: {} | Skipped: building={}, road={}, park={}, platform={}",
                    platformsAdded, skippedByBuilding, skippedByRoad, skippedByPark, skippedByPlatform);
        }
    }

    private static void allocatePlatformsAlongStraights(
            Set<BlockPos> straightPositions,
            Map<BlockPos, RoadNode> nodeMap,
            List<BuildingSlot> slots,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            Set<BlockPos> roadPositions,
            Set<BlockPos> parkOccupiedSlots
    ) {
        int added = 0, skippedOccupied = 0, skippedRoad = 0, skippedPark = 0;
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (BlockPos pos : straightPositions) {
            if (nodeMap.containsKey(pos)) continue;
            Direction roadDirection = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDirection == null) continue;
            Direction left = roadDirection.getCounterClockWise();
            Direction right = roadDirection.getClockWise();

            for (Direction side : new Direction[]{left, right}) {
                mutPos.set(pos).move(side, GeneratorConfig.BUILDING_DISTANCE);
                BlockPos platformPos = mutPos.immutable();

                if (occupiedSlots.containsKey(platformPos)) { skippedOccupied++; continue; }
                if (roadPositions.contains(platformPos)) { skippedRoad++; continue; }
                if (parkOccupiedSlots.contains(platformPos)) { skippedPark++; continue; }

                Rotation platformRotation = calculateOutwardRotation(side);
                BuildingSlot platformSlot = new BuildingSlot(
                        platformPos, platformRotation, side, PLATFORM_CATEGORY, PLATFORM_NAME, 0
                );
                slots.add(platformSlot);
                occupiedSlots.put(platformPos, platformSlot);
                added++;
            }
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [PLATFORMS-STRAIGHT] Added: {} | Skipped: occupied={}, road={}, park={}",
                    added, skippedOccupied, skippedRoad, skippedPark);
        }
    }

    private static Direction detectRoadDirection(
            BlockPos pos,
            Set<BlockPos> straightPositions,
            Set<BlockPos> nodePositions
    ) {
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            mutPos.set(pos).move(dir, GeneratorConfig.ROAD_STEP);
            BlockPos neighbor = mutPos.immutable();
            if (straightPositions.contains(neighbor) || nodePositions.contains(neighbor)) {
                return dir;
            }
        }
        return null;
    }

    private static void tryAllocateSlot(
            ServerLevel level,
            BlockPos nodePos,
            Direction side,
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions,
            ParkWorldTracker tracker
    ) {
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        mutPos.set(nodePos).move(side, GeneratorConfig.BUILDING_DISTANCE);
        BlockPos buildingPos = mutPos.immutable();

        if (occupiedSlots.containsKey(buildingPos)) return;
        if (roadPositions.contains(buildingPos)) return;

        String category = chooseCategory(random);
        if (category.equals(PARKS_CATEGORY) && GeneratorConfig.SPAWN_PARKS) {
            if (tryAllocatePark(level, nodePos, side, buildingPos, occupiedSlots, slots, random, roadPositions, tracker)) {
                return;
            }
            category = "houses";
        }

        String buildingName = chooseBuilding(random, category);
        if (buildingName == null) {
            LOGGER.warn("Warn: No building found for category: {}", category);
            return;
        }

        Rotation rotation = calculateBuildingRotation(side);
        int maxDepth = GeneratorConfig.BUILDING_MIN_DEPTH + random.nextInt(
                GeneratorConfig.BUILDING_MAX_DEPTH - GeneratorConfig.BUILDING_MIN_DEPTH + 1
        );
        BuildingSlot slot = new BuildingSlot(
                buildingPos, rotation, side, category, buildingName, maxDepth
        );
        slots.add(slot);
        occupiedSlots.put(buildingPos, slot);
    }

    private static boolean tryAllocatePark(
            ServerLevel level,
            BlockPos nodePos,
            Direction side,
            BlockPos buildingPos, 
            Map<BlockPos, BuildingSlot> occupiedSlots,
            List<BuildingSlot> slots,
            RandomSource random,
            Set<BlockPos> roadPositions,
            ParkWorldTracker tracker
    ) {
        List<ParkConfig> parks = ParkConfigLoader.getParks();
        List<ParkConfig> availableParks = ParkPlacer.filterAvailableParks(level, parks, tracker);
        if (availableParks.isEmpty()) return false;

        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ParkConfig park = availableParks.get(random.nextInt(availableParks.size()));
            boolean isSquare = park.size()[0] == park.size()[2];
            Rotation rotation = ParkPlacer.calculateRotationForSide(side);

            Set<BlockPos> parkSlots = ParkPlacer.calculateSlots(buildingPos, side, park, rotation);

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
                        buildingPos, rotation, side, PARKS_CATEGORY, park.name(), 0
                );
                slots.add(parkSlot);
                for (BlockPos slot : parkSlots) {
                    occupiedSlots.put(slot, parkSlot);
                }
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Info: [PARK-ALLOC] Placed '{}' ({}) at {} side {} ({} slots, attempt {})",
                            park.name(), isSquare ? "SQUARE" : "RECT",
                            nodePos.toShortString(), side, parkSlots.size(), attempt + 1);
                }
                return true;
            }

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.debug("Info: [PARK-ALLOC] Attempt {}: '{}' ({}) conflicts at {} side {} ({} conflicts)",
                        attempt + 1, park.name(), isSquare ? "SQUARE" : "RECT",
                        nodePos.toShortString(), side, conflicts);
            }
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Retry: [PARK-ALLOC] All {} attempts failed at {} side {}, falling back to building",
                    maxAttempts, nodePos.toShortString(), side);
        }
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
            LOGGER.error("Error: No buildings found in category: {}", category);
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