package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.generation.StructureUtils;
import ru.rorcik.structures.generation.city.plains.houses.HouseGenerator;

import java.util.Optional;

public class BuildingPlacer {
    private static final HouseGenerator HOUSE_GENERATOR = new HouseGenerator();
    private static final int BUILDING_Y_OFFSET = -1;

    public static void placeBuilding(ServerLevel level, BuildingSlot slot) {
        if (slot.category().equals("houses")) {
            HOUSE_GENERATOR.generate(level, slot.position(), slot.buildingName(), slot.rotation());
        } else {
            placeSimpleBuilding(level, slot);
        }
    }

    private static void placeSimpleBuilding(ServerLevel level, BuildingSlot slot) {
        String nbtPath = ModPaths.buildingNbtPath(slot.category(), slot.buildingName());
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);

        if (location == null) return;

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) return;

        StructureTemplate template = templateOpt.get();
        BlockPos size = new BlockPos(template.getSize());

        BlockPos origin = slot.position().offset(0, BUILDING_Y_OFFSET, 0);
        BlockPos compensated = StructureUtils.getCompensatedOrigin(
                origin,
                slot.rotation(),
                size.getX(),
                size.getZ()
        );

        StructureUtils.placeStructure(
                level,
                compensated,
                nbtPath,
                slot.rotation(),
                false,
                false
        );
    }
}