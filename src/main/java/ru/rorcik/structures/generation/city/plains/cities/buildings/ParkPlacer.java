package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
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
    private static final Logger LOGGER = LogUtils.getLogger();

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

    public static Set<BlockPos> calculateSlots(BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
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
        mutPos.set(roadPos).move(depthDir, GeneratorConfig.BUILDING_DISTANCE);
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
        BlockPos placementPos = calculatePlacementOrigin(roadPos, side, park, rotation);
        StructureUtils.placeStructureUsingOriginPivot(
                level, placementPos, ModPaths.parkNbtPath(park.name()), rotation, false, false
        );
    }

    private static BlockPos calculatePlacementOrigin(BlockPos roadPos, Direction side, ParkConfig park, Rotation rotation) {
        Set<BlockPos> slots = calculateSlots(roadPos, side, park, rotation);
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
            LOGGER.error("[PARK-TEST] Park '{}' not found", parkName);
            return;
        }

        Direction side = calculateSideForRotation(rotation);
        BlockPos roadPos = playerPos.relative(side.getOpposite(), GeneratorConfig.BUILDING_DISTANCE);
        Set<BlockPos> slots = calculateSlots(roadPos, side, park, rotation);

        LOGGER.info("[PARK-TEST] Park: {} | Rotation: {} | Side: {}", parkName, rotation, side);
        LOGGER.info("[PARK-TEST] PlayerPos: {}", playerPos.toShortString());
        LOGGER.info("[PARK-TEST] Virtual roadPos: {}", roadPos.toShortString());
        for (BlockPos s : slots) {
            LOGGER.info("[PARK-TEST]   Slot: {}", s.toShortString());
        }

        placePark(level, roadPos, side, park, rotation);
    }
}