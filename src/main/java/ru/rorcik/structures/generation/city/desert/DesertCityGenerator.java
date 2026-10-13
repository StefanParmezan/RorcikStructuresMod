package ru.rorcik.structures.generation.city.desert;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.Optional;

public class DesertCityGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] DESERT_BUILDINGS = {
            "aa", "ab", "ac", "ad", "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "z"
    };

    public static void generateCity(ServerLevel level, BlockPos origin, long seed) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("=== GENERATING DESERT CITY ===");
            LOGGER.info("Origin: {}", origin.toShortString());
            LOGGER.info("Config: buildings={}, step={}..{}, dirs={}..{}, yOffset={}",
                    GeneratorConfig.DESERT_BUILDING_COUNT,
                    GeneratorConfig.DESERT_STEP_MIN, GeneratorConfig.DESERT_STEP_MAX,
                    GeneratorConfig.DESERT_MIN_DIRECTIONS, GeneratorConfig.DESERT_MAX_DIRECTIONS,
                    GeneratorConfig.DESERT_Y_OFFSET);
        }

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        int buildingCount = GeneratorConfig.DESERT_BUILDING_COUNT;
        int stepMin = GeneratorConfig.DESERT_STEP_MIN;
        int stepMax = Math.max(stepMin, GeneratorConfig.DESERT_STEP_MAX);
        int minDirs = GeneratorConfig.DESERT_MIN_DIRECTIONS;
        int maxDirs = Math.max(minDirs, GeneratorConfig.DESERT_MAX_DIRECTIONS);
        int yOffset = GeneratorConfig.DESERT_Y_OFFSET;

        for (int i = 0; i < buildingCount; i++) {
            int dx = 0;
            int dz = 0;
            int directions = minDirs + random.nextInt(maxDirs - minDirs + 1);
            for (int d = 0; d < directions; d++) {
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int step = stepMin + random.nextInt(stepMax - stepMin + 1);
                if (!random.nextBoolean()) step = -step;
                if (dir == Direction.NORTH) dz += step;
                else if (dir == Direction.SOUTH) dz += step;
                else if (dir == Direction.EAST) dx += step;
                else if (dir == Direction.WEST) dx += step;
            }

            BlockPos pos = origin.offset(dx, yOffset, dz);
            String buildingName = DESERT_BUILDINGS[random.nextInt(DESERT_BUILDINGS.length)];
            Rotation rotation = Rotation.values()[random.nextInt(4)];
            String nbtPath = RorcikStructuresMod.MODID + ":cities/desert/houses/" + buildingName;

            ResourceLocation location = ResourceLocation.tryParse(nbtPath);
            if (location == null) continue;

            Optional<StructureTemplate> templateOpt = StructureCache.get(location);
            if (templateOpt.isEmpty()) {
                if (RorcikStructuresModConfig.logCacheMisses) {
                    LOGGER.warn("[DESERT] Template not found: {}", nbtPath);
                }
                continue;
            }

            StructureTemplate template = templateOpt.get();
            BlockPos size = new BlockPos(template.getSize());
            BlockPos compensated = StructureUtils.getCompensatedOrigin(
                    pos, rotation, size.getX(), size.getZ()
            );

            StructureUtils.placeStructure(level, compensated, nbtPath, rotation, false, false);
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("=== DESERT CITY GENERATION COMPLETE ===");
        }
    }
}