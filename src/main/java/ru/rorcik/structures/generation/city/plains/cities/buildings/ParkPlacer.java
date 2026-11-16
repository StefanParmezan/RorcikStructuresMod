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
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ParkPlacer {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;

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

    public static Set<BlockPos> calculateSlots(BlockPos startPos, Direction side, ParkConfig park, Rotation rotation) {
        Set<BlockPos> slots = new HashSet<>();
        int[] baseSize = getParkBaseSize(park);
        boolean rotated90 = isRotated90(rotation);
        int worldSizeX = rotated90 ? baseSize[1] : baseSize[0];
        int worldSizeZ = rotated90 ? baseSize[0] : baseSize[1];

        int slotsAlongX = Math.max(1, (worldSizeX + GeneratorConfig.ROAD_STEP - 1) / GeneratorConfig.ROAD_STEP);
        int slotsAlongZ = Math.max(1, (worldSizeZ + GeneratorConfig.ROAD_STEP - 1) / GeneratorConfig.ROAD_STEP);

        Direction depthDir = side;
        Direction widthDir = side.getClockWise();
        int depthSlots, widthSlots;
        if (depthDir == Direction.NORTH || depthDir == Direction.SOUTH) {
            depthSlots = slotsAlongZ;
            widthSlots = slotsAlongX;
        } else {
            depthSlots = slotsAlongX;
            widthSlots = slotsAlongZ;
        }

        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        mutPos.set(startPos); 
        BlockPos start = mutPos.immutable();

        for (int d = 0; d < depthSlots; d++) {
            for (int w = 0; w < widthSlots; w++) {
                mutPos.set(start)
                        .move(depthDir, d * GeneratorConfig.ROAD_STEP)
                        .move(widthDir, w * GeneratorConfig.ROAD_STEP);
                slots.add(mutPos.immutable());
            }
        }
        return slots;
    }

    public static void placePark(ServerLevel level, BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
        ParkWorldTracker tracker = ParkWorldTracker.get(level);
        if (park.hasWorldLimit() && !tracker.canSpawn(park.name(), park.worldCount())) {
            ParkConfig replacement = findReplacementParkSameSize(level, park, tracker, level.getRandom());
            if (replacement == null) {
                LOGGER.warn("Warn: [PARK] '{}' reached world limit and no same-size replacement is available",
                        park.name());
                return;
            }
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [PARK] '{}' reached world limit, using '{}' instead",
                        park.name(), replacement.name());
            }
            park = replacement;
        }

        BlockPos placementPos = calculatePlacementOrigin(roadPos, side, park, rotation);
        StructureUtils.placeStructureUsingOriginPivot(
                level, placementPos, ModPaths.parkNbtPath(park.name()), rotation, false, false
        );
        tracker.increment(park.name());
    }

    public static ParkConfig findReplacementParkSameSize(ServerLevel level, ParkConfig original,
                                                         ParkWorldTracker tracker, RandomSource random) {
        List<ParkConfig> candidates = new ArrayList<>();
        for (ParkConfig park : ParkConfigLoader.getParks()) {
            if (park.name().equals(original.name())) continue;
            if (park.size()[0] != original.size()[0] || park.size()[2] != original.size()[2]) continue;
            if (!park.canSpawn(tracker.getCount(park.name()))) continue;
            candidates.add(park);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static BlockPos calculatePlacementOrigin(BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
        
        BlockPos startPos = roadPos.relative(side, GeneratorConfig.BUILDING_DISTANCE);
        Set<BlockPos> slots = calculateSlots(startPos, side, park, rotation);
        BlockPos minCorner = calculateMinCorner(slots, roadPos.getY() + park.yOffset());
        int[] baseSize = getParkBaseSize(park);
        return StructureUtils.getCompensatedOrigin(minCorner, rotation, baseSize[0], baseSize[1]);
    }

    public static List<ParkConfig> filterParks(List<ParkConfig> allParks) {
        if (!GeneratorConfig.PARK_ONLY_RECT) return allParks;
        List<ParkConfig> rectParks = new ArrayList<>();
        for (ParkConfig park : allParks) {
            int[] baseSize = getParkBaseSize(park);
            if (baseSize[0] != baseSize[1]) {
                rectParks.add(park);
            }
        }
        return rectParks;
    }

    public static List<ParkConfig> filterAvailableParks(ServerLevel level, List<ParkConfig> allParks, ParkWorldTracker tracker) {
        List<ParkConfig> filtered = filterParks(allParks);
        List<ParkConfig> available = new ArrayList<>();
        for (ParkConfig park : filtered) {
            int currentCount = tracker.getCount(park.name());
            if (park.canSpawn(currentCount)) {
                available.add(park);
            } else {
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.debug("Info: [PARK] '{}' reached world limit ({}/{}), skipping",
                            park.name(), currentCount, park.worldCount());
                }
            }
        }
        return available;
    }

    public static ParkConfig findReplacementPark(ServerLevel level, ParkConfig original, RandomSource random) {
        List<ParkConfig> largeParks = ParkConfigLoader.getParks().stream()
                .filter(p -> p.size()[0] >= 128 && p.size()[2] >= 128)
                .toList();
        ParkWorldTracker tracker = ParkWorldTracker.get(level);
        List<ParkConfig> available = new ArrayList<>();
        for (ParkConfig park : largeParks) {
            if (park.name().equals(original.name())) continue;
            int currentCount = tracker.getCount(park.name());
            if (park.canSpawn(currentCount)) {
                available.add(park);
            }
        }
        if (available.isEmpty()) {
            LOGGER.warn("Warn: [PARK] No replacement parks available for '{}'", original.name());
            return null;
        }
        return available.get(random.nextInt(available.size()));
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

    private static int[] getParkBaseSize(ParkConfig park) {
        if (!StructureCache.isInitialized()) {
            return new int[]{park.size()[0], park.size()[2]};
        }
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

    public static void testPlacePark(ServerLevel level, BlockPos playerPos, String parkName, Rotation rotation) {
        ParkConfig park = ParkConfigLoader.getPark(parkName);
        if (park == null) {
            LOGGER.error("Error: [PARK-TEST] Park '{}' not found", parkName);
            return;
        }
        Direction side = calculateSideForRotation(rotation);
        
        Set<BlockPos> slots = calculateSlots(playerPos, side, park, rotation);

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [PARK-TEST] Park: {} | Rotation: {} | Side: {}", parkName, rotation, side);
            LOGGER.info("Info: [PARK-TEST] StartPos: {}", playerPos.toShortString());
            for (BlockPos s : slots) {
                LOGGER.info("Info: [PARK-TEST]   Slot: {}", s.toShortString());
            }
        }

        BlockPos roadPos = playerPos.relative(side.getOpposite(), GeneratorConfig.BUILDING_DISTANCE);
        placePark(level, roadPos, side, park, rotation);
    }
}