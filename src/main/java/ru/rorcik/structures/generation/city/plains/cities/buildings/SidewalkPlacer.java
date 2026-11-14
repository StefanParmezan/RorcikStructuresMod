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
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.*;

public class SidewalkPlacer {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final String[] SIDEWALK_NAMES = {"a", "b", "c", "d", "e", "f", "g"};
    private static final String PARKS_CATEGORY = "parks";
    private static final String PLATFORM_CATEGORY = "platforms";

    public static void placeSidewalkForSlot(ServerLevel level, BuildingSlot slot, RandomSource random) {
        placeSidewalkForSlot(level, slot, random, null);
    }

    public static void placeSidewalkForSlot(ServerLevel level, BuildingSlot slot, RandomSource random, Set<Long> cityChunks) {
        if (slot.category().equals(PARKS_CATEGORY) || slot.category().equals(PLATFORM_CATEGORY)) {
            return;
        }

        Direction rightSide = getRightSide(slot.roadSide());
        BlockPos neighborPos = slot.position().relative(rightSide, GeneratorConfig.ROAD_STEP);

        if (slot.position().hashCode() > neighborPos.hashCode()) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.debug("Debug: [SIDEWALK] Skipping sidewalk placement at {}, neighbor will handle it or it's city edge", slot.position().toShortString());
            }
            return;
        }

        placeSidewalk(level, slot, random, cityChunks);
    }

    private static void placeSidewalk(ServerLevel level, BuildingSlot slot, RandomSource random,
                                      Set<Long> cityChunks) {
        String sidewalkName = SIDEWALK_NAMES[random.nextInt(SIDEWALK_NAMES.length)];
        String nbtPath = ModPaths.sidewalkNbtPath(sidewalkName);
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);

        if (location == null) {
            LOGGER.warn("Warn: [SIDEWALK] Invalid sidewalk path: {}", nbtPath);
            return;
        }

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) {
            boolean placed = false;
            for (String fallbackName : SIDEWALK_NAMES) {
                String fallbackPath = ModPaths.sidewalkNbtPath(fallbackName);
                ResourceLocation fallbackLoc = ResourceLocation.tryParse(fallbackPath);
                if (fallbackLoc != null && StructureCache.get(fallbackLoc).isPresent()) {
                    nbtPath = fallbackPath;
                    location = fallbackLoc;
                    templateOpt = StructureCache.get(fallbackLoc);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                LOGGER.warn("Warn: [SIDEWALK] No sidewalk templates available, skipping");
                return;
            }
        }

        StructureTemplate template = templateOpt.get();
        BlockPos size = new BlockPos(template.getSize());
        Rotation sidewalkRotation = getSidewalkRotation(slot.roadSide());

        BlockPos sidewalkPos = calculateSidewalkPosition(slot.position(), slot.roadSide());
        BlockPos compensated = StructureUtils.getCompensatedOrigin(
                sidewalkPos, sidewalkRotation, size.getX(), size.getZ()
        );

        if (cityChunks != null && !isInsideCity(cityChunks, compensated, sidewalkRotation, size)) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.debug("Debug: [SIDEWALK] Skipping sidewalk outside city at {}", compensated.toShortString());
            }
            return;
        }

        StructureUtils.placeStructure(level, compensated, nbtPath, sidewalkRotation, false, false);

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: [SIDEWALK] Placed sidewalk at {} (roadSide: {})", compensated.toShortString(), slot.roadSide());
        }
    }

    private static BlockPos calculateSidewalkPosition(BlockPos slotPos, Direction roadSide) {
        return switch (roadSide) {
            case SOUTH -> slotPos.offset(61, -1, 0);
            case NORTH -> slotPos.offset(-61, -1, 0);
            case EAST  -> slotPos.offset(0, -1, -61);
            case WEST  -> slotPos.offset(0, -1, 61);  
            default    -> slotPos;
        };
    }

    private static Direction getRightSide(Direction roadSide) {
        return switch (roadSide) {
            case SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case EAST  -> Direction.NORTH;
            case WEST  -> Direction.SOUTH;
            default    -> Direction.EAST;
        };
    }

    private static Rotation getSidewalkRotation(Direction roadSide) {
        return switch (roadSide) {
            case SOUTH, NORTH -> Rotation.NONE;
            case EAST, WEST   -> Rotation.CLOCKWISE_90;
            default           -> Rotation.NONE;
        };
    }

    private static boolean isInsideCity(Set<Long> cityChunks, BlockPos origin, Rotation rotation, BlockPos size) {
        boolean rotated = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        int width = rotated ? size.getZ() : size.getX();
        int depth = rotated ? size.getX() : size.getZ();

        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + width - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + depth - 1) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                if (!cityChunks.contains(key)) return false;
            }
        }
        return true;
    }

    public static Set<ResourceLocation> collectRequiredTemplates() {
        Set<ResourceLocation> templates = new HashSet<>();
        for (String name : SIDEWALK_NAMES) {
            ResourceLocation loc = ResourceLocation.tryParse(ModPaths.sidewalkNbtPath(name));
            if (loc != null) {
                templates.add(loc);
            }
        }
        return templates;
    }
}