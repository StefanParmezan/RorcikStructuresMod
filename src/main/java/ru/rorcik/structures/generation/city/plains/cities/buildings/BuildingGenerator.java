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
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingGenerator implements IStructureGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void generate(ServerLevel level, BlockPos origin, String type, Rotation rotation) {
        String nbtPath = ModPaths.buildingNbtPath("other", type);

        StructureUtils.placeStructure(
                level,
                origin,
                nbtPath,
                rotation,
                false,
                false
        );
    }

    public static void generateBuildings(
            ServerLevel level,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(nodeMap, straightPositions, random);

        for (BuildingSlot slot : slots) {
            BuildingPlacer.placeBuilding(level, slot);
        }
    }
}