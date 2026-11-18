package ru.rorcik.structures.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;

import java.util.Optional;

public final class StructureUtils {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final int UPDATE_CLIENTS = 2;
    private static final int UPDATE_SKIP_DROPS = 16;
    private static final int UPDATE_KNOWN_SHAPE = 64;

    private StructureUtils() {}

    public static BlockPos getCompensatedOrigin(BlockPos targetOrigin, Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case NONE -> targetOrigin;
            case CLOCKWISE_90 -> targetOrigin.offset(sizeZ - 1, 0, 0);
            case CLOCKWISE_180 -> targetOrigin.offset(sizeX - 1, 0, sizeZ - 1);
            case COUNTERCLOCKWISE_90 -> targetOrigin.offset(0, 0, sizeX - 1);
        };
    }

    public static int[] rotatePositionInStructure(int x, int y, int z, Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{sizeZ - 1 - z, y, x};
            case CLOCKWISE_180 -> new int[]{sizeX - 1 - x, y, sizeZ - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, sizeX - 1 - x};
        };
    }

    public static int[] rotateOffset(int x, int y, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{-z, y, x};
            case CLOCKWISE_180 -> new int[]{-x, y, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, -x};
        };
    }

    public static boolean placeStructure(ServerLevel level, BlockPos origin, String nbtPath, Rotation rotation, boolean mirrorX, boolean mirrorZ) {
        return placeStructureInternal(level, origin, nbtPath, rotation, mirrorX, mirrorZ, false);
    }

    public static boolean placeStructureUsingOriginPivot(ServerLevel level, BlockPos origin, String nbtPath, Rotation rotation, boolean mirrorX, boolean mirrorZ) {
        return placeStructureInternal(level, origin, nbtPath, rotation, mirrorX, mirrorZ, true);
    }

    private static boolean placeStructureInternal(
            ServerLevel level,
            BlockPos origin,
            String nbtPath,
            Rotation rotation,
            boolean mirrorX,
            boolean mirrorZ,
            boolean pivotAtOrigin
    ) {
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.error("Error: [PLACE] Invalid resource location: {}", nbtPath);
            return false;
        }

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) {
            StructureCache.Category cat = StructureCache.getCategoryFromPath(location.getPath());
            Optional<ResourceLocation> fallbackOpt = StructureCache.getFallbackLocation(location, cat, level.getRandom());

            if (fallbackOpt.isPresent()) {
                ResourceLocation fallbackLoc = fallbackOpt.get();
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Retry: [PLACE] Original missing {}, using fallback from same category: {}", nbtPath, fallbackLoc.getPath());
                }
                location = fallbackLoc;
                templateOpt = StructureCache.get(location);
            } else {
                LOGGER.error("Error: [PLACE] CRITICAL: No valid structure found in category {} for {}. This will result in an empty area!", cat, nbtPath);
                return false;
            }
        }

        if (templateOpt.isEmpty()) {
            LOGGER.error("Error: [PLACE] CRITICAL: Fallback also failed to load for: {}. Area will remain empty.", nbtPath);
            return false;
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(false)
                .setKnownShape(true);

        if (mirrorX && mirrorZ) {
            settings.setMirror(Mirror.LEFT_RIGHT);
            origin = origin.offset(0, 0, template.getSize().getZ() - 1);
        } else if (mirrorX) {
            settings.setMirror(Mirror.LEFT_RIGHT);
        } else if (mirrorZ) {
            settings.setMirror(Mirror.FRONT_BACK);
        }

        BlockPos size = new BlockPos(template.getSize());
        BlockPos pivot = pivotAtOrigin ? origin : origin.offset(size);
        int placementFlags = UPDATE_CLIENTS | UPDATE_SKIP_DROPS | UPDATE_KNOWN_SHAPE;

        template.placeInWorld(level, origin, pivot, settings, level.getRandom(), placementFlags);

        if (level.hasChunkAt(origin)) {
            LevelChunk chunk = level.getChunkAt(origin);
            chunk.setUnsaved(true);
        }
        return true;
    }
}