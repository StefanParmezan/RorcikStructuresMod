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

    public static List<Runnable> collectSidewalkActions(
            List<BuildingSlot> slots,
            ServerLevel level,
            RandomSource random
    ) {
        List<Runnable> actions = new ArrayList<>(slots.size());
        Map<BlockPos, BuildingSlot> slotMap = new HashMap<>(slots.size());
        for (BuildingSlot slot : slots) {
            slotMap.put(slot.position(), slot);
        }

        Set<Long> processedPairs = new HashSet<>();
        int sidewalksAdded = 0;
        int skippedPark = 0;
        int skippedPlatform = 0;
        int skippedNoNeighbor = 0;
        int skippedDifferentRow = 0;

        for (BuildingSlot slot : slots) {
            if (slot.category().equals(PARKS_CATEGORY)) {
                skippedPark++;
                continue;
            }
            if (slot.category().equals(PLATFORM_CATEGORY)) {
                skippedPlatform++;
                continue;
            }

            Direction rightSide = getRightSide(slot.roadSide());
            BlockPos neighborPos = slot.position().relative(rightSide, GeneratorConfig.ROAD_STEP);

            BuildingSlot neighbor = slotMap.get(neighborPos);
            if (neighbor == null) {
                skippedNoNeighbor++;
                continue;
            }

            if (neighbor.category().equals(PARKS_CATEGORY)
                    || neighbor.category().equals(PLATFORM_CATEGORY)) {
                continue;
            }

            if (neighbor.roadSide() != slot.roadSide()) {
                skippedDifferentRow++;
                continue;
            }

            long pairKey = pairKey(slot.position(), neighborPos);
            if (processedPairs.contains(pairKey)) {
                continue;
            }
            processedPairs.add(pairKey);

            sidewalksAdded++;
            actions.add(() -> placeSidewalk(level, slot, random));
        }

        LOGGER.info("[SIDEWALK] Collected {} sidewalk actions (skipped: park={}, platform={}, no_neighbor={}, different_row={})",
                sidewalksAdded, skippedPark, skippedPlatform, skippedNoNeighbor, skippedDifferentRow);
        return actions;
    }

    private static long pairKey(BlockPos a, BlockPos b) {
        int hashA = a.hashCode();
        int hashB = b.hashCode();
        return hashA < hashB
                ? ((long) hashA << 32) | (hashB & 0xFFFFFFFFL)
                : ((long) hashB << 32) | (hashA & 0xFFFFFFFFL);
    }

    private static void placeSidewalk(ServerLevel level, BuildingSlot slot, RandomSource random) {
        String sidewalkName = SIDEWALK_NAMES[random.nextInt(SIDEWALK_NAMES.length)];
        String nbtPath = ModPaths.sidewalkNbtPath(sidewalkName);

        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.warn("[SIDEWALK] Invalid sidewalk path: {}", nbtPath);
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
                LOGGER.warn("[SIDEWALK] No sidewalk templates available, skipping");
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

        StructureUtils.placeStructure(level, compensated, nbtPath, sidewalkRotation, false, false);
    }

    private static BlockPos calculateSidewalkPosition(BlockPos slotPos, Direction roadSide) {
        return switch (roadSide) {
            case SOUTH -> slotPos.offset(61, -1, 0);
            case NORTH -> slotPos.offset(-61, -1, 0);
            case EAST -> slotPos.offset(0, -1, -61);
            case WEST -> slotPos.offset(0, -1, 125);
            default -> slotPos;
        };
    }

    private static Direction getRightSide(Direction roadSide) {
        return switch (roadSide) {
            case SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            default -> Direction.EAST;
        };
    }

    private static Rotation getSidewalkRotation(Direction roadSide) {
        return switch (roadSide) {
            case SOUTH, NORTH -> Rotation.NONE;
            case EAST, WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
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