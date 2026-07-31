package ru.rorcik.structures.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.data.HouseConfigLoader;
import ru.rorcik.structures.data.HouseConfig;
import ru.rorcik.structures.data.RoomSlot;

import java.util.Optional;

public class StructureGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation) {
        generateHouse(level, origin, houseType, level.getSeed(), houseRotation, -1);
    }

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation, int forcedDecor) {
        generateHouse(level, origin, houseType, level.getSeed(), houseRotation, forcedDecor);
    }

    public static void generateRoomsOnly(ServerLevel level, BlockPos origin, String houseType, Rotation houseRotation) {
        generateRoomsOnly(level, origin, houseType, level.getSeed(), houseRotation);
    }

    private static void generateHouse(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation, int forcedDecor) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        LOGGER.info("=== GENERATION START ===");
        LOGGER.info("House: {} rotation: {} degrees", houseType, houseRotation.ordinal() * 90);
        LOGGER.info("Command origin: {}", origin.toShortString());

        RandomSource random = RandomSource.create(seed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];
        LOGGER.info("House size: {}x{}x{}", houseSizeX, houseSizeY, houseSizeZ);

        BlockPos houseOrigin = origin.offset(config.offset()[0], config.offset()[1], config.offset()[2]);
        LOGGER.info("Fixed house anchor: {}", houseOrigin.toShortString());

        BlockPos housePlacementOrigin = getCompensatedOrigin(houseOrigin, houseRotation, houseSizeX, houseSizeZ);
        LOGGER.info("Compensated house placement origin: {}", housePlacementOrigin.toShortString());

        int houseDecor = (forcedDecor >= 0) ? forcedDecor : random.nextInt(config.houseDecorCount());
        String houseNbt = houseType + "_" + houseDecor;
        LOGGER.info("House NBT: {} (decor count: {})", houseNbt, config.houseDecorCount());

        placeStructure(level, housePlacementOrigin, "rorcik_structures:houses/" + houseType + "/" + houseNbt, houseRotation, false, false);

        generateRooms(level, config, houseOrigin, houseRotation, random);

        LOGGER.info("=== GENERATION END ===");
    }

    private static void generateRoomsOnly(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        LOGGER.info("=== ROOMS ONLY GENERATION START ===");
        LOGGER.info("House: {} rotation: {} degrees", houseType, houseRotation.ordinal() * 90);
        LOGGER.info("Command origin: {}", origin.toShortString());

        RandomSource random = RandomSource.create(seed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];
        LOGGER.info("House size: {}x{}x{}", houseSizeX, houseSizeY, houseSizeZ);

        BlockPos houseOrigin = origin.offset(config.offset()[0], config.offset()[1], config.offset()[2]);
        LOGGER.info("Fixed house anchor: {}", houseOrigin.toShortString());

        generateRooms(level, config, houseOrigin, houseRotation, random);

        LOGGER.info("=== ROOMS ONLY GENERATION END ===");
    }

    private static void generateRooms(ServerLevel level, HouseConfig config, BlockPos houseOrigin, Rotation houseRotation, RandomSource random) {
        int houseSizeX = config.size()[0];
        int houseSizeZ = config.size()[2];

        for (RoomSlot slot : config.rooms()) {
            LOGGER.info("---");
            LOGGER.info("Room slot: {} variant: {} (decor count: {})", slot.nbt(), slot.variant(), slot.decorCount());
            LOGGER.info("Config position: [{}, {}, {}]", slot.position()[0], slot.position()[1], slot.position()[2]);
            LOGGER.info("Config offset: [{}, {}, {}]", slot.offset()[0], slot.offset()[1], slot.offset()[2]);
            LOGGER.info("Config rotation: {} degrees", slot.rotation());
            LOGGER.info("Mirror: X={}, Z={}", slot.mirrorX(), slot.mirrorZ());

            int roomRotationDegrees = (slot.rotation() + houseRotation.ordinal() * 90) % 360;
            Rotation roomRotation = Rotation.values()[roomRotationDegrees / 90];
            LOGGER.info("Final room rotation: {} degrees", roomRotationDegrees);

            int[] rotatedRoomPos = rotatePositionInHouse(
                    slot.position()[0], slot.position()[1], slot.position()[2],
                    houseRotation, houseSizeX, houseSizeZ
            );
            LOGGER.info("Rotated room position: [{}, {}, {}]",
                    rotatedRoomPos[0], rotatedRoomPos[1], rotatedRoomPos[2]);

            int[] rotatedRoomOffset = rotateOffset(
                    slot.offset()[0], slot.offset()[1], slot.offset()[2],
                    houseRotation
            );
            LOGGER.info("Rotated room offset: [{}, {}, {}]",
                    rotatedRoomOffset[0], rotatedRoomOffset[1], rotatedRoomOffset[2]);

            for (int i = 0; i < slot.repeat(); i++) {
                int floor = slot.startFloor() + i;
                int y = config.firstFloorOffset() + (floor * config.floorHeight());

                int decor = random.nextInt(slot.decorCount());
                String variant = slot.variant();

                String roomNbt;
                if (variant.isEmpty()) {
                    roomNbt = slot.nbt() + "_" + decor;
                } else {
                    roomNbt = slot.nbt() + "_" + decor + "_" + variant;
                }

                BlockPos roomOrigin = houseOrigin.offset(
                        rotatedRoomPos[0] + rotatedRoomOffset[0],
                        y + rotatedRoomOffset[1],
                        rotatedRoomPos[2] + rotatedRoomOffset[2]
                );

                LOGGER.info("Floor {}: y={}", floor, y);
                LOGGER.info("Room StructureBlock origin: {}", roomOrigin.toShortString());
                LOGGER.info("Room NBT: {}", roomNbt);

                placeStructure(level, roomOrigin, "rorcik_structures:rooms/" + config.type() + "/" + roomNbt, roomRotation, slot.mirrorX(), slot.mirrorZ());
            }
        }
    }

    private static BlockPos getCompensatedOrigin(BlockPos targetOrigin, Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case NONE -> targetOrigin;
            case CLOCKWISE_90 -> targetOrigin.offset(sizeZ - 1, 0, 0);
            case CLOCKWISE_180 -> targetOrigin.offset(sizeX - 1, 0, sizeZ - 1);
            case COUNTERCLOCKWISE_90 -> targetOrigin.offset(0, 0, sizeX - 1);
        };
    }

    private static int[] rotatePositionInHouse(int x, int y, int z, Rotation rotation, int houseSizeX, int houseSizeZ) {
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{houseSizeZ - 1 - z, y, x};
            case CLOCKWISE_180 -> new int[]{houseSizeX - 1 - x, y, houseSizeZ - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, houseSizeX - 1 - x};
        };
    }

    private static int[] rotateOffset(int x, int y, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{-z, y, x};
            case CLOCKWISE_180 -> new int[]{-x, y, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, -x};
        };
    }

    private static void placeStructure(ServerLevel level, BlockPos origin, String nbtPath, Rotation rotation, boolean mirrorX, boolean mirrorZ) {
        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.error("Invalid resource location: {}", nbtPath);
            return;
        }

        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(location);

        if (templateOpt.isEmpty()) {
            LOGGER.error("Structure not found: {}", nbtPath);
            return;
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setKnownShape(true);

        if (mirrorX && mirrorZ) {
            settings.setMirror(Mirror.LEFT_RIGHT);
            origin = origin.offset(0, 0, template.getSize().getZ() - 1);
        } else if (mirrorX) {
            settings.setMirror(Mirror.LEFT_RIGHT);
        } else if (mirrorZ) {
            settings.setMirror(Mirror.FRONT_BACK);
        }

        BlockPos size = new BlockPos(template.getSize());
        template.placeInWorld(level, origin, origin.offset(size), settings, level.getRandom(), 3);
    }
}