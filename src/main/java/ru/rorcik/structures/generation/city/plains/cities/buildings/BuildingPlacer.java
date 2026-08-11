package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;
import ru.rorcik.structures.generation.city.plains.houses.HouseGenerator;

import java.util.Optional;

public class BuildingPlacer {
    private static final HouseGenerator HOUSE_GENERATOR = new HouseGenerator();
    private static final int BUILDING_Y_OFFSET = -1;
    private static final String PLATFORM_CATEGORY = "platforms";
    private static final String PARKS_CATEGORY = "parks";

    public static void placeBuilding(ServerLevel level, BuildingSlot slot) {
        if (slot.category().equals("houses")) {
            HOUSE_GENERATOR.generate(level, slot.position(), slot.buildingName(), slot.rotation());
        } else if (slot.category().equals(PLATFORM_CATEGORY)) {
            placePlatform(level, slot);
        } else if (slot.category().equals(PARKS_CATEGORY)) {
            placePark(level, slot);
        } else {
            placeSimpleBuilding(level, slot);
        }
    }

    private static void placePark(ServerLevel level, BuildingSlot slot) {
        ParkConfig park = ParkConfigLoader.getPark(slot.buildingName());
        if (park == null) {
            return;
        }

        // position хранит первую позицию парка (roadPos + side * BUILDING_DISTANCE)
        // side хранится в roadSide()
        // Нам нужно вычислить roadPos = position - side * BUILDING_DISTANCE
        BlockPos buildingPos = slot.position();
        Direction side = slot.roadSide();
        BlockPos roadPos = buildingPos.relative(side.getOpposite(), GeneratorConfig.BUILDING_DISTANCE);

        ParkPlacer.placePark(level, roadPos, side, park, slot.rotation());
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

    private static void placePlatform(ServerLevel level, BuildingSlot slot) {
        String nbtPath = ModPaths.platformNbtPath(slot.buildingName());
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