package ru.rorcik.structures.data.city.plains.config_loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IConfigLoader;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.containers.HouseConfig;
import ru.rorcik.structures.data.city.plains.containers.RoomSlot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HouseConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, HouseConfig> CONFIGS = new HashMap<>();

    public static void loadConfigs(ResourceManager resourceManager) {
        CONFIGS.clear();

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_HOUSES, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        HouseConfig config = GSON.fromJson(reader, HouseConfig.class);
                        int houseDecorCount = countHouseDecorations(resourceManager, config.type());
                        int totalRooms = 0;

                        List<RoomSlot> updatedRooms = new ArrayList<>();
                        for (RoomSlot room : config.rooms()) {
                            RoomDecorInfo roomDecorInfo = countRoomDecorations(resourceManager, config.type(), room.nbt());

                            RoomSlot updatedRoom = new RoomSlot(
                                    room.nbt(),
                                    room.position(),
                                    room.offset(),
                                    room.size(),
                                    room.rotation(),
                                    room.startFloor(),
                                    room.repeat(),
                                    room.variant(),
                                    room.mirrorX(),
                                    room.mirrorZ(),
                                    roomDecorInfo.count(),
                                    roomDecorInfo.baseDecors()
                            );

                            updatedRooms.add(updatedRoom);
                            totalRooms++;
                        }

                        HouseConfig finalConfig = new HouseConfig(
                                config.type(),
                                config.offset(),
                                config.size(),
                                config.floorHeight(),
                                config.firstFloorOffset(),
                                houseDecorCount,
                                updatedRooms
                        );

                        CONFIGS.put(finalConfig.type(), finalConfig);
                        LOGGER.info("Loaded config '{}' ({} decors, {} room types)", finalConfig.type(), houseDecorCount, totalRooms);

                    } catch (Exception e) {
                        LOGGER.error("Failed to load config: {}", location, e);
                    }
                });
    }

    public static void clear() {
        CONFIGS.clear();
    }

    private record RoomDecorInfo(int count, Set<Integer> baseDecors) {}

    private static int countHouseDecorations(ResourceManager resourceManager, String houseType) {
        int maxDecor = -1;
        String housesPath = ModPaths.STRUCTURES_CITIES_PLAINS + "/houses";
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                housesPath,
                path -> path.getPath().endsWith(".nbt")
        );

        String prefix = houseType + "_";

        for (ResourceLocation location : resources.keySet()) {
            String path = location.getPath();
            if (!path.startsWith(housesPath + "/")) continue;

            String simpleName = path.substring(path.lastIndexOf('/') + 1);

            if (simpleName.startsWith(prefix) && simpleName.endsWith(".nbt")) {
                String numberPart = simpleName.substring(prefix.length(), simpleName.length() - 4);
                if (numberPart.matches("\\d+")) {
                    maxDecor = Math.max(maxDecor, Integer.parseInt(numberPart));
                }
            }
        }
        return maxDecor + 1;
    }

    private static RoomDecorInfo countRoomDecorations(ResourceManager resourceManager, String houseType, String roomNbt) {
        int maxDecor = -1;
        Set<Integer> baseDecors = new HashSet<>();
        String roomsPath = ModPaths.STRUCTURES_CITIES_PLAINS_ROOMS + "/" + houseType;
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                roomsPath,
                path -> path.getPath().endsWith(".nbt") && path.getPath().contains(roomNbt + "_")
        );

        String prefix = roomNbt + "_";

        for (ResourceLocation location : resources.keySet()) {
            String path = location.getPath();
            if (!path.startsWith(roomsPath + "/")) continue;

            String simpleName = path.substring(path.lastIndexOf('/') + 1);

            if (simpleName.startsWith(prefix) && simpleName.endsWith(".nbt")) {
                String afterPrefix = simpleName.substring(prefix.length(), simpleName.length() - 4);

                boolean hasVariantSuffix = afterPrefix.matches("\\d+_[vg]+$");
                String numberPart = afterPrefix.replaceAll("_[vg]+$", "");

                if (numberPart.matches("\\d+")) {
                    int decorIndex = Integer.parseInt(numberPart);
                    maxDecor = Math.max(maxDecor, decorIndex);

                    if (!hasVariantSuffix) {
                        baseDecors.add(decorIndex);
                    }
                }
            }
        }

        return new RoomDecorInfo(maxDecor + 1, baseDecors);
    }

    public static HouseConfig getConfig(String type) {
        return CONFIGS.get(type);
    }
}