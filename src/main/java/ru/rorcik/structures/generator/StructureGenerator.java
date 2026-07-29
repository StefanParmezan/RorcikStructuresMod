package ru.rorcik.structures.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
        long seed = calculateSeed(origin, houseType, level.getSeed());
        generateHouse(level, origin, houseType, seed, houseRotation);
    }

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, long seed, Rotation houseRotation) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        LOGGER.info("=== GENERATION START ===");
        LOGGER.info("House: {} rotation: {} degrees", houseType, houseRotation.ordinal() * 90);
        LOGGER.info("Origin: {}", origin.toShortString());

        RandomSource random = RandomSource.create(seed);

        int houseSizeX = config.size()[0];
        int houseSizeY = config.size()[1];
        int houseSizeZ = config.size()[2];
        LOGGER.info("House size: {}x{}x{}", houseSizeX, houseSizeY, houseSizeZ);

        BlockPos houseOrigin = origin.offset(config.offset()[0], config.offset()[1], config.offset()[2]);
        LOGGER.info("House origin (with offset): {}", houseOrigin.toShortString());

        int houseDecor = random.nextInt(5);
        String houseNbt = houseType + "_" + houseDecor;
        LOGGER.info("House NBT: {}", houseNbt);
        placeStructure(level, houseOrigin, "rorcik_structures:houses/" + houseType + "/" + houseNbt, houseRotation);

        for (RoomSlot slot : config.rooms()) {
            LOGGER.info("---");
            LOGGER.info("Room slot: {} variant: {}", slot.nbt(), slot.variant());
            LOGGER.info("Config position: [{}, {}, {}]", slot.position()[0], slot.position()[1], slot.position()[2]);
            LOGGER.info("Config offset: [{}, {}, {}]", slot.offset()[0], slot.offset()[1], slot.offset()[2]);
            LOGGER.info("Config rotation: {} degrees", slot.rotation());

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
            LOGGER.info("Rotated room offset (by house rotation): [{}, {}, {}]",
                    rotatedRoomOffset[0], rotatedRoomOffset[1], rotatedRoomOffset[2]);

            for (int i = 0; i < slot.repeat(); i++) {
                int floor = slot.startFloor() + i;
                int y = config.firstFloorOffset() + (floor * config.floorHeight());

                int decor = random.nextInt(5);
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
                LOGGER.info("Room origin: {}", roomOrigin.toShortString());
                LOGGER.info("Room NBT: {}", roomNbt);

                placeStructure(level, roomOrigin, "rorcik_structures:rooms/" + houseType + "/" + roomNbt, roomRotation);
            }
        }

        LOGGER.info("=== GENERATION END ===");
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

    private static long calculateSeed(BlockPos origin, String houseType, long worldSeed) {
        return worldSeed
                ^ (origin.getX() * 341873128712L)
                ^ (origin.getZ() * 132897987541L)
                ^ houseType.hashCode();
    }

    private static void placeStructure(ServerLevel level, BlockPos origin, String nbtPath, Rotation rotation) {
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

        BlockPos size = new BlockPos(template.getSize());
        template.placeInWorld(level, origin, origin.offset(size), settings, level.getRandom(), 3);
    }
}