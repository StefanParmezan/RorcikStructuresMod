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

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType) {
        long seed = calculateSeed(origin, houseType, level.getSeed());
        generateHouse(level, origin, houseType, seed);
    }

    public static void generateHouse(ServerLevel level, BlockPos origin, String houseType, long seed) {
        houseType = houseType.toLowerCase();
        HouseConfig config = HouseConfigLoader.getConfig(houseType);
        if (config == null) {
            LOGGER.error("Config not found: {}", houseType);
            return;
        }

        RandomSource random = RandomSource.create(seed);
        BlockPos houseOrigin = origin.offset(config.offset()[0], config.offset()[1], config.offset()[2]);

        int houseDecor = random.nextInt(5);
        String houseNbt = houseType + "_" + houseDecor;
        LOGGER.info("House: {}", houseNbt);
        placeStructure(level, houseOrigin, "rorcik_structures:houses/" + houseType + "/" + houseNbt, Rotation.NONE);

        for (RoomSlot slot : config.rooms()) {
            Rotation roomRotation = Rotation.values()[slot.rotation() / 90];

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

                BlockPos roomOrigin = houseOrigin.offset(slot.position()[0], y, slot.position()[2]);
                BlockPos adjustedOrigin = roomOrigin.offset(slot.offset()[0], slot.offset()[1], slot.offset()[2]);

                LOGGER.info("Room: {} at floor {} position {}", roomNbt, floor, adjustedOrigin.toShortString());

                placeStructure(level, adjustedOrigin, "rorcik_structures:rooms/" + houseType + "/" + roomNbt, roomRotation);
            }
        }
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