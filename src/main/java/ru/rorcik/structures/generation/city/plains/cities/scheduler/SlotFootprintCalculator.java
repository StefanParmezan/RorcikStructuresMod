package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkPlacer;
import ru.rorcik.structures.generation.city.plains.cities.buildings.SidewalkPlacer;
import ru.rorcik.structures.generation.city.plains.houses.HousePositionCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes the block rectangles a slot will actually write to. The scheduler
 * uses these to force-load only the chunks that placement needs.
 */
public final class SlotFootprintCalculator {

    private static final int SIDEWALK_FOOTPRINT = 32;

    private SlotFootprintCalculator() {
    }

    public static List<SlotWork.Footprint> forBuildingSlot(BuildingSlot slot) {
        List<SlotWork.Footprint> footprints = new ArrayList<>();

        switch (slot.category()) {
            case "houses" -> {
                HouseConfig config = HouseConfigLoader.getConfig(slot.buildingName().toLowerCase());
                if (config == null) return footprints;

                BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(slot.position(), config);
                addBox(footprints, houseOrigin, slot.rotation(), config.size()[0], config.size()[2]);

                BlockPos sidewalkAnchor = SidewalkPlacer.calculateSidewalkPosition(slot.position(), slot.roadSide());
                footprints.add(new SlotWork.Footprint(
                        sidewalkAnchor.getX(), sidewalkAnchor.getZ(),
                        sidewalkAnchor.getX() + SIDEWALK_FOOTPRINT - 1,
                        sidewalkAnchor.getZ() + SIDEWALK_FOOTPRINT - 1
                ));
            }
            case "parks" -> {
                ParkConfig park = ParkConfigLoader.getPark(slot.buildingName());
                if (park == null) return footprints;

                BlockPos roadPos = slot.position().relative(
                        slot.roadSide().getOpposite(), GeneratorConfig.BUILDING_DISTANCE
                );
                BlockPos placement = ParkPlacer.calculatePlacementOrigin(roadPos, slot.roadSide(), park, slot.rotation());
                int[] baseSize = ParkPlacer.getParkBaseSize(park);
                boolean rotated90 = ParkPlacer.isRotated90(slot.rotation());
                int width = rotated90 ? baseSize[1] : baseSize[0];
                int depth = rotated90 ? baseSize[0] : baseSize[1];

                footprints.add(new SlotWork.Footprint(
                        placement.getX(), placement.getZ(),
                        placement.getX() + width - 1,
                        placement.getZ() + depth - 1
                ));
            }
            case "desert" -> addTemplateBox(
                    footprints,
                    ModPaths.desertHouseNbtPath(slot.buildingName()),
                    slot.position().offset(0, -1, 0),
                    slot.rotation()
            );
            case "platforms" -> addTemplateBox(
                    footprints,
                    ModPaths.platformNbtPath(slot.buildingName()),
                    slot.position().offset(0, -1, 0),
                    slot.rotation()
            );
            default -> addTemplateBox(
                    footprints,
                    ModPaths.buildingNbtPath(slot.category(), slot.buildingName()),
                    slot.position().offset(0, -1, 0),
                    slot.rotation()
            );
        }

        return footprints;
    }

    public static List<SlotWork.Footprint> forRoad(BlockPos pos, String roadType, Rotation rotation) {
        RoadConfig config = RoadConfigLoader.getConfig(roadType);
        if (config == null) return List.of();

        BlockPos roadOrigin = pos.offset(config.offset()[0], config.offset()[1], config.offset()[2]);
        List<SlotWork.Footprint> footprints = new ArrayList<>(1);
        addBox(footprints, roadOrigin, rotation, config.size()[0], config.size()[2]);
        return footprints;
    }

    public static Rotation straightRoadRotation(Direction direction) {
        return direction == null ? Rotation.NONE : ru.rorcik.structures.generation.city.plains.cities.roads.RoadPlacer.directionToRotation(direction);
    }

    private static void addTemplateBox(List<SlotWork.Footprint> footprints, String nbtPath,
                                       BlockPos origin, Rotation rotation) {
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) return;

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) return;

        BlockPos size = new BlockPos(templateOpt.get().getSize());
        addBox(footprints, origin, rotation, size.getX(), size.getZ());
    }

    private static void addBox(List<SlotWork.Footprint> footprints, BlockPos origin,
                               Rotation rotation, int sizeX, int sizeZ) {
        BlockPos compensated = StructureUtils.getCompensatedOrigin(origin, rotation, sizeX, sizeZ);
        boolean rotated90 = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        int width = rotated90 ? sizeZ : sizeX;
        int depth = rotated90 ? sizeX : sizeZ;

        footprints.add(new SlotWork.Footprint(
                compensated.getX(), compensated.getZ(),
                compensated.getX() + Math.max(0, width - 1),
                compensated.getZ() + Math.max(0, depth - 1)
        ));
    }
}
