package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // Только реальные слоты парка. Без буфера.
        Set<BlockPos> occupiedByParks = new HashSet<>();
        Set<BlockPos> roadPositions = collectAllRoadPositions(network, nodeMap, straightPositions);

        List<ParkConfig> allParks = ParkConfigLoader.getParks();
        if (allParks.isEmpty()) {
            LOGGER.warn("[PARK] No park configs loaded");
            return occupiedByParks;
        }

        List<ParkConfig> availableParks = filterParks(allParks);

        LOGGER.info("[PARK] Starting placement. Nodes: {}, Straights: {}, RoadSlots: {}, ParkChance: {}%, Mode: {}",
                nodeMap.size(), straightPositions.size(), roadPositions.size(),
                GeneratorConfig.PARKS_CHANCE,
                GeneratorConfig.PARK_ONLY_RECT ? "RECT_ONLY" : "ALL");

        if (availableParks.isEmpty()) {
            LOGGER.warn("[PARK] No parks available after filtering");
            return occupiedByParks;
        }

        int nodesChecked = 0, nodesPlaced = 0;
        int straightsChecked = 0, straightsPlaced = 0;

        for (RoadNode node : nodeMap.values()) {
            List<Direction> connections = node.getAllConnections();

            // Та же логика, что и у зданий
            if (node.isTerminal() && connections.size() == 1) {
                continue;
            }

            Direction deadEndExcludedSide = null;
            if (node.isTerminal() && !connections.isEmpty()) {
                Direction entryDir = connections.get(0);
                deadEndExcludedSide = entryDir.getOpposite();
            }

            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (connections.contains(side)) continue;
                if (side == deadEndExcludedSide) continue;

                nodesChecked++;
                if (random.nextInt(100) >= GeneratorConfig.PARKS_CHANCE) continue;

                ParkConfig park = availableParks.get(random.nextInt(availableParks.size()));
                Rotation rotation = calculateRotationForSide(side);

                if (tryPlacePark(level, node.position(), side, park, rotation, roadPositions, occupiedByParks)) {
                    nodesPlaced++;
                }
            }
        }

        for (BlockPos pos : straightPositions) {
            Direction roadDir = detectRoadDirection(pos, straightPositions, nodeMap.keySet());
            if (roadDir == null) continue;

            // Запрещаем парки на прямых, которые примыкают к terminal node
            if (isStraightAdjacentToTerminal(pos, roadDir, nodeMap)) {
                continue;
            }

            for (Direction side : new Direction[]{roadDir.getClockWise(), roadDir.getCounterClockWise()}) {
                straightsChecked++;
                if (random.nextInt(100) >= GeneratorConfig.PARKS_CHANCE) continue;

                ParkConfig park = availableParks.get(random.nextInt(availableParks.size()));
                Rotation rotation = calculateRotationForSide(side);

                if (tryPlacePark(level, pos, side, park, rotation, roadPositions, occupiedByParks)) {
                    straightsPlaced++;
                }
            }
        }
        LOGGER.info("[PARK] Results — Nodes: {}/{} placed, Straights: {}/{} placed, Total occupied: {}",
                nodesPlaced, nodesChecked, straightsPlaced, straightsChecked, occupiedByParks.size());

        if (!occupiedByParks.isEmpty()) {
            LOGGER.info("[PARK] === ALL OCCUPIED SLOTS ({}) ===", occupiedByParks.size());

            List<BlockPos> sortedSlots = new ArrayList<>(occupiedByParks);
            sortedSlots.sort((a, b) -> {
                int cmpX = Integer.compare(a.getX(), b.getX());
                if (cmpX != 0) return cmpX;
                return Integer.compare(a.getZ(), b.getZ());
            });

            for (BlockPos slot : sortedSlots) {
                LOGGER.info("[PARK]   Slot: {}", slot.toShortString());
            }
        }

        return occupiedByParks;
    }

    public static Rotation calculateRotationForSide(Direction side) {
        Direction facing = side.getOpposite();
        return switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Direction calculateSideForRotation(Rotation rotation) {
        Direction facing = switch (rotation) {
            case NONE -> Direction.NORTH;
            case CLOCKWISE_90 -> Direction.EAST;
            case CLOCKWISE_180 -> Direction.SOUTH;
            case COUNTERCLOCKWISE_90 -> Direction.WEST;
        };
        return facing.getOpposite();
    }

    public static void testPlacePark(ServerLevel level, BlockPos playerPos, String parkName, Rotation rotation) {
        ParkConfig park = ParkConfigLoader.getPark(parkName);
        if (park == null) {
            LOGGER.error("[PARK-TEST] Park '{}' not found", parkName);
            return;
        }

        Direction side = calculateSideForRotation(rotation);
        BlockPos roadPos = playerPos.relative(side.getOpposite(), GeneratorConfig.BUILDING_DISTANCE);

        Set<BlockPos> slots = calculateSlots(roadPos, side, park, rotation);
        int[] baseSize = getParkBaseSize(park);
        boolean rotated90 = isRotated90(rotation);

        int worldSizeX = rotated90 ? baseSize[1] : baseSize[0];
        int worldSizeZ = rotated90 ? baseSize[0] : baseSize[1];

        LOGGER.info("[PARK-TEST] Park: {} | Rotation: {} | Facing: {} | Side: {} | BaseSize: {}x{} | WorldSize: {}x{}",
                parkName, rotation, side.getOpposite(), side, baseSize[0], baseSize[1], worldSizeX, worldSizeZ);
        LOGGER.info("[PARK-TEST] PlayerPos (first slot): {}", playerPos.toShortString());
        LOGGER.info("[PARK-TEST] Virtual roadPos: {}", roadPos.toShortString());

        for (BlockPos s : slots) {
            LOGGER.info("[PARK-TEST]   Slot: {} (dx={}, dz={})",
                    s.toShortString(), s.getX() - playerPos.getX(), s.getZ() - playerPos.getZ());
        }

        BlockPos placementPos = calculatePlacementOrigin(roadPos, side, park, rotation);
        BlockPos footprintMin = calculateMinCorner(slots, roadPos.getY() + park.yOffset());
        BlockPos footprintMax = footprintMin.offset(worldSizeX - 1, 0, worldSizeZ - 1);

        LOGGER.info("[PARK-TEST] Footprint: {} to {}",
                footprintMin.toShortString(), footprintMax.toShortString());

        LOGGER.info("[PARK-TEST] PlacementPos: {} (dx={}, dz={})",
                placementPos.toShortString(),
                placementPos.getX() - playerPos.getX(),
                placementPos.getZ() - playerPos.getZ());

        StructureUtils.placeStructureUsingOriginPivot(
                level,
                placementPos,
                ModPaths.parkNbtPath(park.name()),
                rotation,
                false,
                false
        );
    }

    private static boolean tryPlacePark(
            ServerLevel level,
            BlockPos roadPos,
            Direction side,
            ParkConfig park,
            Rotation rotation,
            Set<BlockPos> roads,
            Set<BlockPos> occupiedParks
    ) {
        Set<BlockPos> slots = calculateSlots(roadPos, side, park, rotation);

        int roadConflicts = 0;
        int parkConflicts = 0;
        Set<BlockPos> conflictingSlots = new HashSet<>();

        for (BlockPos slot : slots) {
            if (roads.contains(slot)) {
                roadConflicts++;
                conflictingSlots.add(slot);
            }
            if (occupiedParks.contains(slot)) {
                parkConflicts++;
                conflictingSlots.add(slot);
            }
        }

        int[] baseSize = getParkBaseSize(park);
        boolean isSquare = baseSize[0] == baseSize[1];

        if (roadConflicts > 0 || parkConflicts > 0) {
            LOGGER.info("[PARK] ✗ Skipped '{}' at {} side {} | Rotation: {} | Type: {} | road conflicts: {} | park conflicts: {} | conflicting slots: {}",
                    park.name(), roadPos.toShortString(), side, rotation,
                    isSquare ? "SQUARE" : "RECT", roadConflicts, parkConflicts, conflictingSlots.size());
            return false;
        }

        occupiedParks.addAll(slots);

        BlockPos placementPos = calculatePlacementOrigin(roadPos, side, park, rotation);

        StructureUtils.placeStructureUsingOriginPivot(
                level,
                placementPos,
                ModPaths.parkNbtPath(park.name()),
                rotation,
                false,
                false
        );

        boolean rotated90 = isRotated90(rotation);
        int worldSizeX = rotated90 ? baseSize[1] : baseSize[0];
        int worldSizeZ = rotated90 ? baseSize[0] : baseSize[1];

        BlockPos footprintMin = calculateMinCorner(slots, roadPos.getY() + park.yOffset());
        BlockPos footprintMax = footprintMin.offset(worldSizeX - 1, 0, worldSizeZ - 1);

        LOGGER.info("[PARK] ✓ Placed '{}' at {} side {} | Rotation: {} | Facing: {} | Type: {} | Slots: {}",
                park.name(), roadPos.toShortString(), side, rotation, side.getOpposite(),
                isSquare ? "SQUARE" : "RECT", slots.size());

        LOGGER.info("[PARK]   Footprint: {} to {} ({}x{})",
                footprintMin.toShortString(), footprintMax.toShortString(), worldSizeX, worldSizeZ);

        LOGGER.info("[PARK]   Placement origin: {}", placementPos.toShortString());

        LOGGER.info("[PARK]   Occupied slots:");
        for (BlockPos slot : slots) {
            LOGGER.info("[PARK]     {}", slot.toShortString());
        }

        return true;
    }

    private static Set<BlockPos> calculateSlots(BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
        Set<BlockPos> slots = new HashSet<>();

        int[] baseSize = getParkBaseSize(park);
        boolean rotated90 = isRotated90(rotation);

        int worldSizeX = rotated90 ? baseSize[1] : baseSize[0];
        int worldSizeZ = rotated90 ? baseSize[0] : baseSize[1];

        // ВАЖНО: ceil, а не floor
        int slotsAlongX = Math.max(1, (worldSizeX + GeneratorConfig.ROAD_STEP - 1) / GeneratorConfig.ROAD_STEP);
        int slotsAlongZ = Math.max(1, (worldSizeZ + GeneratorConfig.ROAD_STEP - 1) / GeneratorConfig.ROAD_STEP);

        Direction depthDir = side;
        Direction widthDir = side.getClockWise();

        int depthSlots;
        int widthSlots;

        if (depthDir == Direction.NORTH || depthDir == Direction.SOUTH) {
            depthSlots = slotsAlongZ;
            widthSlots = slotsAlongX;
        } else {
            depthSlots = slotsAlongX;
            widthSlots = slotsAlongZ;
        }

        BlockPos start = roadPos.relative(depthDir, GeneratorConfig.BUILDING_DISTANCE);

        for (int d = 0; d < depthSlots; d++) {
            for (int w = 0; w < widthSlots; w++) {
                BlockPos slot = start
                        .relative(depthDir, d * GeneratorConfig.ROAD_STEP)
                        .relative(widthDir, w * GeneratorConfig.ROAD_STEP);
                slots.add(slot);
            }
        }

        return slots;
    }

    private static BlockPos calculatePlacementOrigin(BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
        Set<BlockPos> slots = calculateSlots(roadPos, side, park, rotation);
        BlockPos minCorner = calculateMinCorner(slots, roadPos.getY() + park.yOffset());

        int[] baseSize = getParkBaseSize(park);

        BlockPos compensated = StructureUtils.getCompensatedOrigin(
                minCorner,
                rotation,
                baseSize[0],
                baseSize[1]
        );

        boolean isSquare = baseSize[0] == baseSize[1];

        LOGGER.info("[PARK-PLACE] {} | minCorner: {} | compensated: {} | rotation: {} | side: {}",
                isSquare ? "SQUARE" : "RECT",
                minCorner.toShortString(),
                compensated.toShortString(),
                rotation,
                side);

        return compensated;
    }

    private static List<ParkConfig> filterParks(List<ParkConfig> allParks) {
        if (!GeneratorConfig.PARK_ONLY_RECT) {
            return allParks;
        }

        List<ParkConfig> rectParks = new ArrayList<>();
        for (ParkConfig park : allParks) {
            int[] baseSize = getParkBaseSize(park);
            if (baseSize[0] != baseSize[1]) {
                rectParks.add(park);
            }
        }
        return rectParks;
    }

    private static Set<BlockPos> collectAllRoadPositions(
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodes,
            Set<BlockPos> straights
    ) {
        Set<BlockPos> all = new HashSet<>(nodes.keySet());
        all.addAll(straights);

        for (RoadSegment s : network.getSegments()) {
            all.add(s.position());
        }

        return all;
    }

    private static Direction detectRoadDirection(BlockPos pos, Set<BlockPos> straights, Set<BlockPos> nodes) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d, GeneratorConfig.ROAD_STEP);
            if (straights.contains(n) || nodes.contains(n)) {
                return d;
            }
        }
        return null;
    }

    private static boolean isRotated90(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    private static BlockPos calculateMinCorner(Set<BlockPos> slots, int y) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        for (BlockPos s : slots) {
            minX = Math.min(minX, s.getX());
            minZ = Math.min(minZ, s.getZ());
        }

        return new BlockPos(minX, y, minZ);
    }

    /**
     * Берём реальный размер из NBT, если шаблон уже загружен.
     * Иначе fallback на json config.
     *
     * Возвращает {sizeX, sizeZ}
     */
    private static int[] getParkBaseSize(ParkConfig park) {
        ResourceLocation location = ResourceLocation.tryParse(ModPaths.parkNbtPath(park.name()));
        if (location != null) {
            Optional<StructureTemplate> templateOpt = StructureCache.get(location);
            if (templateOpt.isPresent()) {
                var size = templateOpt.get().getSize();
                return new int[]{size.getX(), size.getZ()};
            }
        }

        return new int[]{park.size()[0], park.size()[2]};
    }

    private static boolean isStraightAdjacentToTerminal(
            BlockPos pos,
            Direction roadDir,
            Map<BlockPos, RoadNode> nodeMap
    ) {
        RoadNode forward = nodeMap.get(pos.relative(roadDir, GeneratorConfig.ROAD_STEP));
        if (forward != null && forward.isTerminal()) {
            return true;
        }

        RoadNode backward = nodeMap.get(pos.relative(roadDir.getOpposite(), GeneratorConfig.ROAD_STEP));
        return backward != null && backward.isTerminal();
    }
}