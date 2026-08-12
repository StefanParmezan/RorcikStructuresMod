// FILE: ./main/java/ru/rorcik/structures/generation/city/plains/cities/buildings/BuildingGenerator.java
package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IStructureGenerator;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.HouseConfigLoader;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.data.city.plains.containers.RoomSlot;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildingGenerator implements IStructureGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void generate(ServerLevel level, BlockPos origin, String type, Rotation rotation) {
        String nbtPath = ModPaths.buildingNbtPath("other", type);
        StructureUtils.placeStructure(level, origin, nbtPath, rotation, false, false);
    }

    public static void generateBuildings(
            ServerLevel level,
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        List<Runnable> actions = collectBuildActions(level, network, nodeMap, straightPositions, random);
        for (Runnable action : actions) {
            action.run();
        }
    }

    public static List<Runnable> collectBuildActions(
            ServerLevel level,
            RoadNetwork network,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            RandomSource random
    ) {
        List<BuildingSlot> slots = BuildingSlotAllocator.allocateSlots(
                nodeMap, straightPositions, new HashSet<>(), random
        );
        LOGGER.info("[BUILD] Collected {} building slots for batched placement", slots.size());

        Set<ResourceLocation> requiredTemplates = collectRequiredTemplates(slots, nodeMap);
        if (!requiredTemplates.isEmpty()) {
            LOGGER.info("[BUILD] Preloading {} unique templates before placement", requiredTemplates.size());
            long startTime = System.currentTimeMillis();
            StructureCache.preloadTemplates(requiredTemplates);
            LOGGER.info("[BUILD] Preload complete in {} ms", System.currentTimeMillis() - startTime);
        }

        int parkCount = 0;
        int buildingCount = 0;
        int platformCount = 0;
        List<Runnable> actions = new ArrayList<>();

        for (BuildingSlot slot : slots) {
            if (slot.category().equals("parks")) {
                parkCount++;
                actions.add(() -> BuildingPlacer.placeBuilding(level, slot));
            } else if (slot.category().equals("platforms")) {
                platformCount++;
                actions.add(() -> BuildingPlacer.placeBuilding(level, slot));
            } else if (slot.category().equals("houses")) {
                buildingCount++;
                // Оболочка дома — отдельное действие
                actions.add(() -> BuildingPlacer.placeHouseShell(level, slot));
                // Каждая комната — отдельное действие для адаптивного планировщика
                HouseConfig config = HouseConfigLoader.getConfig(slot.buildingName().toLowerCase());
                if (config != null) {
                    for (RoomSlot room : config.rooms()) {
                        for (int i = 0; i < room.repeat(); i++) {
                            final int floorIndex = i;
                            actions.add(() -> BuildingPlacer.placeHouseRoom(level, slot, room, floorIndex));
                        }
                    }
                }
            } else {
                buildingCount++;
                actions.add(() -> BuildingPlacer.placeBuilding(level, slot));
            }
        }

        LOGGER.info("[BUILD] Breakdown: buildings={}, parks={}, platforms={}, total_actions={}",
                buildingCount, parkCount, platformCount, actions.size());
        return actions;
    }

    private static Set<ResourceLocation> collectRequiredTemplates(
            List<BuildingSlot> slots,
            Map<BlockPos, RoadNode> nodeMap
    ) {
        Set<ResourceLocation> templates = new HashSet<>();

        // Здания, дома, комнаты, парки, платформы
        for (BuildingSlot slot : slots) {
            if (slot.category().equals("houses")) {
                String houseType = slot.buildingName().toLowerCase();
                HouseConfig config = HouseConfigLoader.getConfig(houseType);
                if (config != null) {
                    // Все декоры оболочки дома
                    for (int decor = 0; decor < Math.max(1, config.houseDecorCount()); decor++) {
                        addTemplate(templates, ModPaths.houseNbtPath(houseType + "_" + decor));
                    }
                    // Все декоры всех комнат
                    for (RoomSlot room : config.rooms()) {
                        for (int decor = 0; decor < Math.max(1, room.decorCount()); decor++) {
                            String roomNbt = room.variant().isEmpty()
                                    ? room.nbt() + "_" + decor
                                    : room.nbt() + "_" + decor + "_" + room.variant();
                            addTemplate(templates, ModPaths.roomNbtPath(config.type(), roomNbt));
                        }
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

        // Дороги: только те типы, которые реально есть в графе
        Set<String> usedRoadTypes = new HashSet<>();
        for (RoadNode node : nodeMap.values()) {
            List<Direction> connections = node.getAllConnections();
            if (!connections.isEmpty()) {
                RoadConfigLoader.RoadMapEntry entry = RoadConfigLoader.findRoadForConnections(connections);
                if (entry != null) {
                    usedRoadTypes.add(entry.type());
                }
            }
        }
        usedRoadTypes.add("s"); // прямые участки между нодами

        for (String roadType : usedRoadTypes) {
            RoadConfig config = RoadConfigLoader.getConfig(roadType);
            if (config != null) {
                for (int decor = 0; decor < Math.max(1, config.decorCount()); decor++) {
                    addTemplate(templates, ModPaths.roadNbtPath(config.type(), config.type() + "_" + decor));
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