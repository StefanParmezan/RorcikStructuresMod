package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.server.level.ServerLevel;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.generation.StructureUtils;
import ru.rorcik.structures.generation.city.plains.houses.HouseGenerator;

public class BuildingPlacement {
    private static final HouseGenerator HOUSE_GENERATOR = new HouseGenerator();

    public static void placeBuilding(ServerLevel level, BuildingSlot slot) {
        if (slot.category().equals("houses")) {
            HOUSE_GENERATOR.generate(level, slot.position(), slot.buildingName(), slot.rotation());
        } else {
            placeSimpleBuilding(level, slot);
        }
    }

    private static void placeSimpleBuilding(ServerLevel level, BuildingSlot slot) {
        String nbtPath = ModPaths.buildingNbtPath(slot.category(), slot.buildingName());

        StructureUtils.placeStructure(
                level,
                slot.position(),
                nbtPath,
                slot.rotation(),
                false,
                false
        );
    }
}