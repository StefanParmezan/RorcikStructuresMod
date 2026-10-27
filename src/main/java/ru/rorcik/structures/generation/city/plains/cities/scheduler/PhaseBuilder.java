package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.*;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingGenerator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.BuildingPlacer;
import ru.rorcik.structures.generation.city.plains.cities.buildings.SidewalkPlacer;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadDecorationsPlacer;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadPlacer;
import java.util.*;

public class PhaseBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final RoadNetwork roadNetwork;
    private final RandomSource baseRandom;
    private final Map<BlockPos, BuildingSlot> slotMap;
    private final int baseY;
    private final Map<BlockPos, RoadNode> nodeMap;
    private final Set<BlockPos> straightPositions;
    private final int cityCenterX;
    private final int cityCenterZ;
    private final CityJournal journal;

    public PhaseBuilder(ServerLevel level, RoadNetwork roadNetwork, RandomSource baseRandom,
                        List<BuildingSlot> slots, int baseY,
                        Map<BlockPos, RoadNode> nodeMap, Set<BlockPos> straightPositions,
                        int cityCenterX, int cityCenterZ,
                        CityJournal journal) {
        this.level = level;
        this.roadNetwork = roadNetwork;
        this.baseRandom = baseRandom;
        this.baseY = baseY;
        this.nodeMap = nodeMap;
        this.straightPositions = straightPositions;
        this.slotMap = new HashMap<>();
        for (BuildingSlot slot : slots) slotMap.put(slot.position(), slot);
        this.cityCenterX = cityCenterX;
        this.cityCenterZ = cityCenterZ;
        this.journal = journal;
    }

    public SlotWork buildForRoadNode(RoadNode node, BlockPos pos, int anchorChunkSize) {
        int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
        int anchorChunkX = (pos.getX() >> (4 + shift)) << shift;
        int anchorChunkZ = (pos.getZ() >> (4 + shift)) << shift;
        String slotId = anchorChunkX + "," + anchorChunkZ + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        List<Phase> phases = new ArrayList<>();
        phases.add(new Phase(PhaseType.ROAD, () -> placeRoadForNode(node, pos), collectRoadTemplatesForNode(node)));
        return new SlotWork(slotId, "ROAD_NODE", phases);
    }

    public SlotWork buildForStraightRoad(BlockPos pos, int anchorChunkSize) {
        int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
        int anchorChunkX = (pos.getX() >> (4 + shift)) << shift;
        int anchorChunkZ = (pos.getZ() >> (4 + shift)) << shift;
        String slotId = anchorChunkX + "," + anchorChunkZ + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        List<Phase> phases = new ArrayList<>();
        phases.add(new Phase(PhaseType.ROAD, () -> placeStraightRoad(pos), collectStraightRoadTemplates()));
        return new SlotWork(slotId, "STRAIGHT_ROAD", phases);
    }

    public SlotWork buildForBuildingSlot(BuildingSlot slot, int anchorChunkSize) {
        BlockPos pos = slot.position();
        int shift = Integer.numberOfTrailingZeros(anchorChunkSize);
        int anchorChunkX = (pos.getX() >> (4 + shift)) << shift;
        int anchorChunkZ = (pos.getZ() >> (4 + shift)) << shift;
        String slotId = anchorChunkX + "," + anchorChunkZ + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();

        List<Phase> phases = new ArrayList<>();
        String category = slot.category();

        if ("houses".equals(category)) {
            phases.add(new Phase(PhaseType.HOUSE_SHELL,
                    () -> BuildingPlacer.placeBuildingShell(level, slot),
                    BuildingGenerator.collectRequiredBuildTemplates(List.of(slot))));
            phases.add(new Phase(PhaseType.INSTALL, () -> {}, Collections.emptySet()));
            phases.add(new Phase(PhaseType.ROOMS,
                    () -> BuildingPlacer.placeHouseRoomsOnly(level, slot, createDeterministicRandom(slot.position())),
                    BuildingGenerator.collectRequiredRoomTemplates(List.of(slot))));
            phases.add(new Phase(PhaseType.SIDEWALKS,
                    () -> SidewalkPlacer.placeSidewalkForSlot(level, slot, createDeterministicRandom(slot.position())),
                    SidewalkPlacer.collectRequiredTemplates()));
        } else if ("desert".equals(category) || "parks".equals(category) || "platforms".equals(category) || "other".equals(category) || "lootable".equals(category)) {
            phases.add(new Phase(PhaseType.INSTALL,
                    () -> BuildingPlacer.placeBuilding(level, slot),
                    BuildingGenerator.collectRequiredBuildTemplates(List.of(slot))));
        }
        return new SlotWork(slotId, "BUILDING", phases);
    }

    private void placeRoadForNode(RoadNode node, BlockPos pos) {
        if (journal.hasRoadAt(pos.getX(), pos.getY(), pos.getZ())) return;
        List<Direction> connections = node.getAllConnections();
        if (connections.isEmpty()) return;
        RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
        if (entry == null) return;

        RoadSegment segment = RoadPlacer.createAndPlaceRoad(level, roadNetwork, entry.type(), pos, entry.rotation(), createDeterministicRandom(pos), -1);
        if (segment != null) {
            RoadDecorationsPlacer.placeDecorationsForSingleRoad(level, segment, createDeterministicRandom(pos));
        }
    }

    private void placeStraightRoad(BlockPos pos) {
        if (journal.hasRoadAt(pos.getX(), pos.getY(), pos.getZ())) return;
        Direction roadDir = detectRoadDirection(pos);
        if (roadDir == null) return;
        Rotation rotation = RoadPlacer.directionToRotation(roadDir);
        RandomSource random = createDeterministicRandom(pos);
        String roadTypeToPlace = "s";
        int forcedDecor = -1;
        RoadConfig sConfig = RoadConfigLoader.getConfig("s");
        if (sConfig != null && !sConfig.stopVariants().isEmpty()) {
            synchronized (roadNetwork) {
                if (roadNetwork.getAvailableStopVariants().isEmpty()) roadNetwork.initStopVariants(sConfig.stopVariants());
                if (roadNetwork.getStraightRoadCount() >= 2) {
                    Integer stopVariant = roadNetwork.pollStopVariant();
                    if (stopVariant != null) forcedDecor = stopVariant;
                }
            }
        }
        RoadSegment segment = RoadPlacer.createAndPlaceRoad(level, roadNetwork, roadTypeToPlace, pos, rotation, random, forcedDecor);
        if (segment != null) {
            roadNetwork.incrementStraightRoadCount();
            RoadDecorationsPlacer.placeDecorationsForSingleRoad(level, segment, random);
        }
    }

    private Direction detectRoadDirection(BlockPos pos) {
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = pos.relative(dir, ru.rorcik.structures.generation.GeneratorConfig.ROAD_STEP);
            if (straightPositions.contains(neighbor) || nodeMap.containsKey(neighbor)) return dir;
        }
        return Direction.SOUTH;
    }

    private RandomSource createDeterministicRandom(BlockPos pos) {
        long seed = level.getSeed() ^ pos.hashCode();
        return RandomSource.create(seed);
    }

    private Set<ResourceLocation> collectRoadTemplatesForNode(RoadNode node) {
        Set<ResourceLocation> templates = new HashSet<>();
        List<Direction> connections = node.getAllConnections();
        if (!connections.isEmpty()) {
            RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
            if (entry != null) {
                RoadConfig config = RoadConfigLoader.getConfig(entry.type());
                if (config != null) {
                    for (int decor = 0; decor < Math.max(1, config.decorCount()); decor++) {
                        ResourceLocation loc = ResourceLocation.tryParse(ru.rorcik.structures.common.ModPaths.roadNbtPath(config.type(), config.type() + "_" + decor));
                        if (loc != null) templates.add(loc);
                    }
                }
            }
        }
        return templates;
    }

    private Set<ResourceLocation> collectStraightRoadTemplates() {
        Set<ResourceLocation> templates = new HashSet<>();
        RoadConfig config = RoadConfigLoader.getConfig("s");
        if (config != null) {
            for (int decor = 0; decor < Math.max(1, config.decorCount()); decor++) {
                ResourceLocation loc = ResourceLocation.tryParse(ru.rorcik.structures.common.ModPaths.roadNbtPath("s", "s_" + decor));
                if (loc != null) templates.add(loc);
            }
        }
        return templates;
    }
}