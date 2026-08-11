package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IStructureGenerator;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingGenerator implements IStructureGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void generate(ServerLevel level, BlockPos origin, String type, Rotation rotation) {
        String nbtPath = ModPaths.buildingNbtPath("other", type);
        StructureUtils.placeStructure(level, origin, nbtPath, rotation, false, false);
    }

    public static void generateBuildings(
            ServerLevel level,
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        List<Runnable> actions = collectBuildActions(level, network, nodeMap, straightPositions, random);
        for (Runnable action : actions) {
            action.run();
        }
    }

    public static List<Runnable> collectBuildActions(
            ServerLevel level,
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        List<Runnable> actions = new ArrayList<>();

        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(
                nodeMap, straightPositions, new HashSet<>(), random
        );

        LOGGER.info("[BUILD] Collected {} building slots for batched placement", slots.size());

        int parkCount = 0;
        int buildingCount = 0;
        int platformCount = 0;

        for (BuildingSlot slot : slots) {
            if (slot.category().equals("parks")) {
                parkCount++;
            } else if (slot.category().equals("platforms")) {
                platformCount++;
            } else {
                buildingCount++;
            }
            actions.add(() -> BuildingPlacer.placeBuilding(level, slot));
        }

        LOGGER.info("[BUILD] Breakdown: buildings={}, parks={}, platforms={}",
                buildingCount, parkCount, platformCount);

        return actions;
    }
}