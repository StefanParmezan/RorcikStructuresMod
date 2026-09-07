package ru.rorcik.structures.generation.city.desert;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.generation.StructureUtils;

public class DesertCityGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] DESERT_BUILDINGS = {
            "aa", "ab", "ac", "ad", "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "z"
    };

    private static final int BUILDING_COUNT = 150;

    public static void generateCity(ServerLevel level, BlockPos origin, long seed) {
        LOGGER.info("=== GENERATING DESERT CITY ===");
        LOGGER.info("Origin: {}", origin.toShortString());

        long uniqueSeed = seed ^ origin.hashCode() ^ System.nanoTime() ^ level.getRandom().nextLong();
        RandomSource random = RandomSource.create(uniqueSeed);

        for (int i = 0; i < BUILDING_COUNT; i++) {
            int dx = 0;
            int dz = 0;
            int directions = 1 + random.nextInt(4);

            for (int d = 0; d < directions; d++) {
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int step = random.nextBoolean() ? 32 : -32;
                if (dir == Direction.NORTH) dz += step;
                else if (dir == Direction.SOUTH) dz += step;
                else if (dir == Direction.EAST) dx += step;
                else if (dir == Direction.WEST) dx += step;
            }

            BlockPos pos = origin.offset(dx, 0, dz);
            String buildingName = DESERT_BUILDINGS[random.nextInt(DESERT_BUILDINGS.length)];
            Rotation rotation = Rotation.values()[random.nextInt(4)];

            String nbtPath = RorcikStructuresMod.MODID + ":cities/desert/houses/" + buildingName;
            StructureUtils.placeStructure(level, pos, nbtPath, rotation, false, false);
        }

        LOGGER.info("=== DESERT CITY GENERATION COMPLETE ===");
    }
}