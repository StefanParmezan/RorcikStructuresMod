package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import ru.rorcik.core.city.CityZone;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingSlotAllocator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkAvailability;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadGraphBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CityLayoutPlanner {

    private CityLayoutPlanner() {
    }

    public static CityLayoutPlan planPlains(long worldSeed, int centerX, int centerZ, int seaLevel,
                                            ParkAvailability parkAvailability) {
        int baseY = targetHeight(CityType.PLAINS, seaLevel);
        long citySeed = citySeed(worldSeed, centerX, centerZ);
        RandomSource random = RandomSource.create(citySeed);

        BlockPos origin = new BlockPos(centerX, baseY, centerZ);
        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();
        RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(
                parkAvailability, nodeMap, straightPositions, new HashSet<>(), random
        );

        return buildPlan(citySeed, centerX, centerZ, CityType.PLAINS, baseY, 4,
                nodeMap, straightPositions, slots);
    }

    public static CityLayoutPlan planDesert(long worldSeed, int centerX, int centerZ, int seaLevel) {
        int baseY = targetHeight(CityType.DESERT, seaLevel);
        long citySeed = citySeed(worldSeed, centerX, centerZ);
        RandomSource random = RandomSource.create(citySeed);

        int buildingCount = GeneratorConfig.DESERT_BUILDING_COUNT;
        int stepMin = GeneratorConfig.DESERT_STEP_MIN;
        int stepMax = Math.max(stepMin, GeneratorConfig.DESERT_STEP_MAX);
        int yOffset = GeneratorConfig.DESERT_Y_OFFSET;

        List<BuildingSlot> slots = new ArrayList<>();
        List<BlockPos> placedPositions = new ArrayList<>();

        BlockPos startPos = new BlockPos(centerX, baseY + yOffset, centerZ);
        placedPositions.add(startPos);

        for (int i = 0; i < buildingCount; i++) {
            BlockPos anchorPos = placedPositions.get(random.nextInt(placedPositions.size()));
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int step = stepMin + random.nextInt(stepMax - stepMin + 1);

            int newX = anchorPos.getX() + direction.getStepX() * step;
            int newZ = anchorPos.getZ() + direction.getStepZ() * step;
            BlockPos pos = new BlockPos(newX, baseY + yOffset, newZ);

            String buildingName = CityZonePlanner.DESERT_BUILDINGS[random.nextInt(CityZonePlanner.DESERT_BUILDINGS.length)];
            Rotation rotation = Rotation.values()[random.nextInt(4)];

            slots.add(new BuildingSlot(pos, rotation, null, "desert", buildingName, 0));
            placedPositions.add(pos);
        }

        return buildPlan(citySeed, centerX, centerZ, CityType.DESERT, baseY, 2,
                new HashMap<>(), new HashSet<>(), slots);
    }

    private static CityLayoutPlan buildPlan(long citySeed, int centerX, int centerZ, CityType type,
                                            int baseY, int anchorChunkSize,
                                            Map<BlockPos, RoadNode> nodeMap,
                                            Set<BlockPos> straightPositions,
                                            List<BuildingSlot> slots) {
        Set<Long> chunks = CityZonePlanner.calculateCityChunks(
                nodeMap, straightPositions, slots, anchorChunkSize, 0
        );
        Set<Long> zoneChunks = CityZonePlanner.expandCityChunks(
                chunks, CityZonePlanner.CITY_CHUNK_BORDER
        );

        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (long chunkKey : zoneChunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) (chunkKey & 0xFFFFFFFFL);
            minChunkX = Math.min(minChunkX, chunkX);
            maxChunkX = Math.max(maxChunkX, chunkX);
            minChunkZ = Math.min(minChunkZ, chunkZ);
            maxChunkZ = Math.max(maxChunkZ, chunkZ);
        }

        CityZone zone = new CityZone(centerX, centerZ, type.toZoneRules(),
                targetHeight(type, baseY), minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        return new CityLayoutPlan(citySeed, centerX, centerZ, type, baseY, anchorChunkSize,
                nodeMap, straightPositions, slots, chunks, zoneChunks, zone);
    }

    private static int targetHeight(CityType type, int seaLevel) {
        return type.targetHeight == -1 ? seaLevel : type.targetHeight;
    }

    private static long citySeed(long worldSeed, int centerX, int centerZ) {
        return worldSeed ^ (((long) centerX << 32) | (centerZ & 0xFFFFFFFFL));
    }
}
