package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.ParkConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;
import ru.rorcik.structures.data.city.plains.containers.RoomSlot;
import ru.rorcik.structures.generation.GeneratorConfig;
import ru.rorcik.structures.generation.StructureUtils;
import ru.rorcik.structures.generation.city.plains.houses.HouseGenerator;
import ru.rorcik.structures.generation.city.plains.houses.HousePositionCalculator;

import java.util.Optional;
import java.util.Set;

public class BuildingPlacer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HouseGenerator HOUSE_GENERATOR = new HouseGenerator();
    private static final int BUILDING_Y_OFFSET = -1;
    private static final String PLATFORM_CATEGORY = "platforms";
    private static final String PARKS_CATEGORY = "parks";
    private static final StructurePlaceSettings SHARED_SETTINGS = new StructurePlaceSettings()
            .setIgnoreEntities(false)
            .setKnownShape(true);

    public static void placeBuilding(ServerLevel level, BuildingSlot slot) {
        if ("houses".equals(slot.category())) {
            HOUSE_GENERATOR.generate(level, slot.position(), slot.buildingName(), slot.rotation());
        } else if (PLATFORM_CATEGORY.equals(slot.category())) {
            placePlatform(level, slot);
        } else if (PARKS_CATEGORY.equals(slot.category())) {
            placePark(level, slot);
        } else {
            placeSimpleBuilding(level, slot);
        }
    }

    public static void placeCompleteHouse(ServerLevel level, BuildingSlot slot) {
        String houseType = slot.buildingName().toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) return;

        long uniqueSeed = level.getSeed() ^ slot.position().hashCode();
        RandomSource random = RandomSource.create(uniqueSeed);

        int houseDecor = config.houseDecorCount() > 0 ? random.nextInt(config.houseDecorCount()) : 0;
        String houseNbt = houseType + "_" + houseDecor;
        BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(slot.position(), config);
        BlockPos housePlacementOrigin = HousePositionCalculator.calculatePlacementOrigin(
                houseOrigin, slot.rotation(), config.size()[0], config.size()[2]
        );
        StructureUtils.placeStructure(level, housePlacementOrigin,
                ModPaths.houseNbtPath(houseNbt), slot.rotation(), false, false);

        int houseSizeX = config.size()[0];
        int houseSizeZ = config.size()[2];

        for (RoomSlot room : config.rooms()) {
            placeAllFloorsForRoom(level, config, room, houseOrigin, slot.rotation(),
                    random, houseSizeX, houseSizeZ);
        }
    }

    private static void placeAllFloorsForRoom(
            ServerLevel level, HouseConfig config, RoomSlot room,
            BlockPos houseOrigin, Rotation houseRotation, RandomSource random,
            int houseSizeX, int houseSizeZ
    ) {
        Rotation roomRotation = Rotation.values()[
                ((room.rotation() + houseRotation.ordinal() * 90) % 360) / 90
                ];

        int[] rotatedRoomPos = StructureUtils.rotatePositionInStructure(
                room.position()[0], room.position()[1], room.position()[2],
                houseRotation, houseSizeX, houseSizeZ
        );

        int[] rotatedRoomOffset = StructureUtils.rotateOffset(
                room.offset()[0], room.offset()[1], room.offset()[2],
                houseRotation
        );

        int baseY = config.firstFloorOffset() + rotatedRoomOffset[1];
        int roomX = houseOrigin.getX() + rotatedRoomPos[0] + rotatedRoomOffset[0];
        int roomZ = houseOrigin.getZ() + rotatedRoomPos[2] + rotatedRoomOffset[2];

        String baseNbt = room.nbt();
        String variant = room.variant();
        boolean hasVariant = !variant.isEmpty();
        boolean hasBaseDecors = !room.baseDecors().isEmpty();
        Set<Integer> baseDecors = room.baseDecors();
        int decorCount = room.decorCount();
        boolean mirrorX = room.mirrorX();
        boolean mirrorZ = room.mirrorZ();
        int floorHeight = config.floorHeight();
        String configType = config.type();

        for (int i = 0; i < room.repeat(); i++) {
            int floor = room.startFloor() + i;
            int y = houseOrigin.getY() + baseY + floor * floorHeight;

            int decor = decorCount > 0 ? random.nextInt(decorCount) : 0;
            String roomNbt = resolveRoomNbtFast(baseNbt, variant,
                    hasVariant, hasBaseDecors, baseDecors, decor);

            BlockPos roomOrigin = new BlockPos(roomX, y, roomZ);
            StructureUtils.placeStructure(level, roomOrigin,
                    ModPaths.roomNbtPath(configType, roomNbt),
                    roomRotation, mirrorX, mirrorZ);
        }
    }

    private static String resolveRoomNbtFast(String baseName, String variant,
                                             boolean hasVariant, boolean hasBaseDecors,
                                             Set<Integer> baseDecors, int decor) {
        StringBuilder sb = new StringBuilder(baseName.length() + 16);
        sb.append(baseName).append('_').append(decor);
        if (hasVariant) {
            sb.append('_').append(variant);
        } else if (!(hasBaseDecors && baseDecors.contains(decor))) {
            sb.append("_v");
        }
        return sb.toString();
    }

    private static void placePark(ServerLevel level, BuildingSlot slot) {
        ParkConfig park = ParkConfigLoader.getPark(slot.buildingName());
        if (park == null) {
            LOGGER.error("Error: [PLACE] Park config not found for: {}", slot.buildingName());
            return;
        }
        BlockPos buildingPos = slot.position();
        Direction side = slot.roadSide();
        BlockPos roadPos = buildingPos.relative(side.getOpposite(), GeneratorConfig.BUILDING_DISTANCE);
        ParkPlacer.placePark(level, roadPos, side, park, slot.rotation());
    }

    private static void placeSimpleBuilding(ServerLevel level, BuildingSlot slot) {
        String nbtPath = ModPaths.buildingNbtPath(slot.category(), slot.buildingName());
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.error("Error: [PLACE] Invalid resource location for building: {}", nbtPath);
            return;
        }

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) {
            
            LOGGER.error("Error: [PLACE] CRITICAL: Structure template NOT FOUND in cache for: '{}'. This is why the area is empty!", nbtPath);
            return;
        }

        StructureTemplate template = templateOpt.get();
        BlockPos size = new BlockPos(template.getSize());
        BlockPos origin = slot.position().offset(0, BUILDING_Y_OFFSET, 0);
        BlockPos compensated = StructureUtils.getCompensatedOrigin(
                origin, slot.rotation(), size.getX(), size.getZ()
        );
        StructureUtils.placeStructure(level, compensated, nbtPath, slot.rotation(), false, false);
    }

    private static void placePlatform(ServerLevel level, BuildingSlot slot) {
        String nbtPath = ModPaths.platformNbtPath(slot.buildingName());
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.error("Error: [PLACE] Invalid resource location for platform: {}", nbtPath);
            return;
        }

        Optional<StructureTemplate> templateOpt = StructureCache.get(location);
        if (templateOpt.isEmpty()) {
            
            LOGGER.error("Error: [PLACE] CRITICAL: Platform template NOT FOUND in cache for: '{}'. This is why the area is empty!", nbtPath);
            return;
        }

        StructureTemplate template = templateOpt.get();
        BlockPos size = new BlockPos(template.getSize());
        BlockPos origin = slot.position().offset(0, BUILDING_Y_OFFSET, 0);
        BlockPos compensated = StructureUtils.getCompensatedOrigin(
                origin, slot.rotation(), size.getX(), size.getZ()
        );
        StructureUtils.placeStructure(level, compensated, nbtPath, slot.rotation(), false, false);
    }

    public static void placeBuildingShell(ServerLevel level, BuildingSlot slot) {
        if ("houses".equals(slot.category())) {
            placeHouseShell(level, slot);
        } else {
            placeBuilding(level, slot);
        }
    }

    private static void placeHouseShell(ServerLevel level, BuildingSlot slot) {
        String houseType = slot.buildingName().toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Error: [PLACE] House config not found for: {}", houseType);
            return;
        }

        long uniqueSeed = level.getSeed() ^ slot.position().hashCode();
        RandomSource random = RandomSource.create(uniqueSeed);

        int houseDecor = config.houseDecorCount() > 0 ? random.nextInt(config.houseDecorCount()) : 0;
        String houseNbt = houseType + "_" + houseDecor;

        BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(slot.position(), config);
        BlockPos housePlacementOrigin = HousePositionCalculator.calculatePlacementOrigin(
                houseOrigin, slot.rotation(), config.size()[0], config.size()[2]
        );

        StructureUtils.placeStructure(level, housePlacementOrigin, ModPaths.houseNbtPath(houseNbt), slot.rotation(), false, false);
    }

    public static void placeHouseRoomsOnly(ServerLevel level, BuildingSlot slot, RandomSource random) {
        String houseType = slot.buildingName().toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) return;

        BlockPos houseOrigin = HousePositionCalculator.calculateHouseOrigin(slot.position(), config);
        int houseSizeX = config.size()[0];
        int houseSizeZ = config.size()[2];

        for (RoomSlot room : config.rooms()) {
            placeAllFloorsForRoom(level, config, room, houseOrigin, slot.rotation(), random, houseSizeX, houseSizeZ);
        }
    }
}