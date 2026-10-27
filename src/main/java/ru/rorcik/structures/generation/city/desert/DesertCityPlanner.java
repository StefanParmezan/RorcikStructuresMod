package ru.rorcik.structures.generation.city.desert;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesertCityPlanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] DESERT_BUILDINGS = {
            "aa", "ab", "ac", "ad", "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "z"
    };

    public record DesertDryRunResult(
            long seed,
            List<BuildingSlot> slots,
            int baseY 
    ) {}

    public static DesertDryRunResult performDryRun(ServerLevel level, int centerX, int centerZ) {
        
        long deterministicSeed = level.getSeed() ^ (((long) centerX << 32) | (centerZ & 0xFFFFFFFFL));
        RandomSource random = RandomSource.create(deterministicSeed);

        int buildingCount = GeneratorConfig.DESERT_BUILDING_COUNT;
        int stepMin = GeneratorConfig.DESERT_STEP_MIN;
        int stepMax = Math.max(stepMin, GeneratorConfig.DESERT_STEP_MAX);
        int minDirs = GeneratorConfig.DESERT_MIN_DIRECTIONS;
        int maxDirs = Math.max(minDirs, GeneratorConfig.DESERT_MAX_DIRECTIONS);
        int yOffset = GeneratorConfig.DESERT_Y_OFFSET;

        List<BuildingSlot> slots = new ArrayList<>();

        int currentX = centerX;
        int currentZ = centerZ;
        int baseY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, currentX, currentZ) + yOffset;

        for (int i = 0; i < buildingCount; i++) {
            int dx = 0;
            int dz = 0;
            int directions = minDirs + random.nextInt(maxDirs - minDirs + 1);

            for (int d = 0; d < directions; d++) {
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int step = stepMin + random.nextInt(stepMax - stepMin + 1);
                if (!random.nextBoolean()) step = -step;

                if (dir == Direction.NORTH) dz -= step; 
                else if (dir == Direction.SOUTH) dz += step;
                else if (dir == Direction.EAST) dx += step;
                else if (dir == Direction.WEST) dx -= step;
            }

            currentX += dx;
            currentZ += dz;

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, currentX, currentZ);
            int placementY = surfaceY + yOffset;

            BlockPos pos = new BlockPos(currentX, placementY, currentZ);
            String buildingName = DESERT_BUILDINGS[random.nextInt(DESERT_BUILDINGS.length)];
            Rotation rotation = Rotation.values()[random.nextInt(4)];

            BuildingSlot slot = new BuildingSlot(pos, rotation, null, "desert", buildingName, 0);
            slots.add(slot);
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [DESERT-PLANNER] Dry-run complete for [{}, {}]. Generated {} slots. Base Y: {}",
                    centerX, centerZ, slots.size(), baseY);
        }

        return new DesertDryRunResult(deterministicSeed, slots, baseY);
    }

    public static Set<Long> calculateExactChunks(List<BuildingSlot> slots) {
        Set<Long> chunks = new HashSet<>();
        for (BuildingSlot slot : slots) {
            BlockPos pos = slot.position();
            int baseChunkX = pos.getX() >> 4;
            int baseChunkZ = pos.getZ() >> 4;

            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    long key = ((long)(baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                    chunks.add(key);
                }
            }
        }
        return chunks;
    }
}