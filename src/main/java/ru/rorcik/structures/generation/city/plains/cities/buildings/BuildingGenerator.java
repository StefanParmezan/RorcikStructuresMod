package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IStructureGenerator;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.config_loaders.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.RoomSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuildingGenerator implements IStructureGenerator {

    @Override
    public void generate(ServerLevel level, BlockPos origin, String type, net.minecraft.world.level.block.Rotation rotation) {
        String nbtPath = ModPaths.buildingNbtPath("other", type);
        ru.rorcik.structures.generation.StructureUtils.placeStructure(level, origin, nbtPath, rotation, false, false);
    }

    public static List<Runnable> collectBuildActions(ServerLevel level, List<BuildingSlot> slots) {
        List<Runnable> actions = new ArrayList<>();
        for (BuildingSlot slot : slots) {
            actions.add(() -> BuildingPlacer.placeBuildingShell(level, slot));
        }
        return actions;
    }

    public static Set<ResourceLocation> collectRequiredBuildTemplates(List<BuildingSlot> slots) {
        Set<ResourceLocation> templates = new HashSet<>();
        for (BuildingSlot slot : slots) {
            if (slot.category().equals("houses")) {
                String houseType = slot.buildingName().toLowerCase();
                HouseConfig config = HouseConfigLoader.getConfig(houseType);
                if (config != null) {
                    for (int decor = 0; decor < Math.max(1, config.houseDecorCount()); decor++) {
                        addTemplate(templates, ModPaths.houseNbtPath(houseType + "_" + decor));
                    }
                }
            } else if (slot.category().equals("parks")) {
                addTemplate(templates, ModPaths.parkNbtPath(slot.buildingName()));
            } else if (slot.category().equals("platforms")) {
                addTemplate(templates, ModPaths.platformNbtPath(slot.buildingName()));
            } else {
                addTemplate(templates, ModPaths.buildingNbtPath(slot.category(), slot.buildingName()));
            }
        }
        return templates;
    }

    public static List<Runnable> collectRoomPlacementActions(ServerLevel level, List<BuildingSlot> slots, RandomSource random) {
        List<Runnable> actions = new ArrayList<>();
        for (BuildingSlot slot : slots) {
            if (slot.category().equals("houses")) {
                actions.add(() -> BuildingPlacer.placeHouseRoomsOnly(level, slot, random));
            }
        }
        return actions;
    }

    public static Set<ResourceLocation> collectRequiredRoomTemplates(List<BuildingSlot> slots) {
        Set<ResourceLocation> templates = new HashSet<>();
        for (BuildingSlot slot : slots) {
            if (slot.category().equals("houses")) {
                String houseType = slot.buildingName().toLowerCase();
                HouseConfig config = HouseConfigLoader.getConfig(houseType);
                if (config != null) {
                    for (RoomSlot room : config.rooms()) {
                        int maxDecor = Math.max(1, room.decorCount());
                        for (int decor = 0; decor < maxDecor; decor++) {
                            if (!room.variant().isEmpty()) {
                                addTemplate(templates, ModPaths.roomNbtPath(config.type(), room.nbt() + "_" + decor + "_" + room.variant()));
                            } else if (room.baseDecors().contains(decor)) {
                                addTemplate(templates, ModPaths.roomNbtPath(config.type(), room.nbt() + "_" + decor));
                            } else {
                                addTemplate(templates, ModPaths.roomNbtPath(config.type(), room.nbt() + "_" + decor + "_v"));
                            }
                        }
                    }
                }
            }
        }
        return templates;
    }

    private static void addTemplate(Set<ResourceLocation> templates, String nbtPath) {
        ResourceLocation loc = ResourceLocation.tryParse(nbtPath);
        if (loc != null) {
            templates.add(loc);
        }
    }
}