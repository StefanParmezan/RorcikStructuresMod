package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.event.ZoneEvents;
import ru.rorcik.core.zone.BiomeZoneRegistry;
import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingSlotAllocator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkWorldTracker;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadGraphBuilder;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContext;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContextRegistry;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.PlayerProximityTracker;

import java.util.*;

public class CityZonePlanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean tryReserveCity(ServerLevel level, int centerX, int centerZ, CityType type) {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PLANNER] Reserving zone for {} city at [{}, {}]", type.id, centerX, centerZ);
        }

        // 1. Biome check
        BlockPos checkPos = new BlockPos(centerX, level.getSeaLevel(), centerZ);
        Holder<Biome> biomeHolder = level.getBiome(checkPos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            LOGGER.warn("Warn: [CITY-PLANNER] Failed to get biome ID at [{}, {}]", centerX, centerZ);
            return false;
        }
        TemperatureZone currentZone = getBiomeTemperatureZone(biomeId, biomeHolder.value());
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PLANNER] Biome: {} | Zone: {}", biomeId, currentZone);
        }
        if (currentZone == null || !type.allowedZones.contains(currentZone)) {
            LOGGER.warn("Warn: [CITY-PLANNER] Biome not suitable for type {}", type.id);
            return false;
        }

        // 2. Zone reservation
        ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
        boolean reserved = registry.tryReserveZone(centerX, centerZ, type.toZoneRules());
        if (!reserved) {
            LOGGER.warn("Warn: [CITY-PLANNER] Zone already occupied");
            return false;
        }
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: [CITY-PLANNER] Zone reserved");
        }

        // 3. DRY RUN to get real chunks for RWG
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PLANNER] Performing dry run...");
        }
        DryRunResult dryRun = performDryRun(level, centerX, centerZ, type.targetHeight);
        Set<Long> affectedChunks = calculateExactChunks(dryRun);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Success: [CITY-PLANNER] Dry run complete. Chunks: {}", affectedChunks.size());
        }

        // 4. Save context
        CityGenerationContext context = new CityGenerationContext(
                centerX, centerZ, type.id, type.targetHeight,
                dryRun.nodeMap, dryRun.straightPositions, dryRun.slots,
                dryRun.roadNetwork, dryRun.seed, dryRun.baseY
        );
        CityGenerationContextRegistry.register(context);

        // 5. Post event with REAL chunks (for RWG)
        MinecraftForge.EVENT_BUS.post(new ZoneEvents.ZoneReservedEvent(
                centerX, centerZ, type.toZoneRules(), type.targetHeight, affectedChunks
        ));

        // 6. Register in tracker with chunks (for bbox calculation)
        PlayerProximityTracker.registerCity(centerX, centerZ, affectedChunks);

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PLANNER] City fully prepared. Waiting for player...");
        }
        return true;
    }

    private static DryRunResult performDryRun(ServerLevel level, int centerX, int centerZ, int targetHeight) {
        int baseY = targetHeight != -1 ? targetHeight : level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        BlockPos origin = new BlockPos(centerX, baseY, centerZ);
        long seed = level.getSeed() ^ origin.hashCode() ^ System.nanoTime();
        RandomSource random = RandomSource.create(seed);

        Map<BlockPos, RoadNode> nodeMap = new HashMap<>();
        Set<BlockPos> straightPositions = new HashSet<>();
        RoadGraphBuilder.buildGraph(random, origin, Direction.SOUTH, 0, nodeMap, straightPositions);

        RoadNetwork roadNetwork = new RoadNetwork();
        ParkWorldTracker tracker = ParkWorldTracker.get(level);
        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(
                level, nodeMap, straightPositions, new HashSet<>(), random, tracker
        );

        return new DryRunResult(seed, nodeMap, straightPositions, slots, roadNetwork, baseY);
    }

    private record DryRunResult(
            long seed,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            List<BuildingSlot> slots,
            RoadNetwork roadNetwork,
            int baseY
    ) {}

    private static Set<Long> calculateExactChunks(DryRunResult dryRun) {
        Set<Long> chunks = new HashSet<>();
        for (Map.Entry<BlockPos, RoadNode> entry : dryRun.nodeMap.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadNode node = entry.getValue();
            List<Direction> connections = node.getAllConnections();
            RoadConfigLoader.RoadMapEntry roadEntry = RoadConfigLoader.findRoadForConnections(connections);
            if (roadEntry != null && "st".equals(roadEntry.type())) {
                continue;
            }
            addStructureChunks(chunks, pos);
        }
        for (BlockPos pos : dryRun.straightPositions) {
            addStructureChunks(chunks, pos);
        }
        for (BuildingSlot slot : dryRun.slots) {
            if (!slot.category().equals("platforms")) {
                addStructureChunks(chunks, slot.position());
            }
        }
        return chunks;
    }

    private static void addStructureChunks(Set<Long> chunks, BlockPos pos) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                long key = ((long)(baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                chunks.add(key);
            }
        }
    }

    private static TemperatureZone getBiomeTemperatureZone(ResourceLocation biomeId, Biome biome) {
        Optional<TemperatureZone> coreZone = BiomeZoneRegistry.getZone(biomeId);
        if (coreZone.isPresent()) return coreZone.get();
        float temp = biome.getBaseTemperature();
        if (temp < 0.15f) return TemperatureZone.COLD;
        if (temp < 0.45f) return TemperatureZone.COOL;
        if (temp < 0.75f) return TemperatureZone.NEUTRAL;
        if (temp < 0.95f) return TemperatureZone.WARM;
        return TemperatureZone.HOT;
    }
}