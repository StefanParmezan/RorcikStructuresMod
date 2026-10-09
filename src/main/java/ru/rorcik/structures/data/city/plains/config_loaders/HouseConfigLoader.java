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
import java.util.*;

public class HouseConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, HouseConfig> CONFIGS = new HashMap<>();

    private static final Map<String, Map<String, Integer>> MAX_DECOR_INDEX = new HashMap<>();
    private static final Map<String, Map<String, Set<Integer>>> BASE_DECORS_INDEX = new HashMap<>();

    public static void loadConfigs(ResourceManager resourceManager) {
        CONFIGS.clear();
        MAX_DECOR_INDEX.clear();
        BASE_DECORS_INDEX.clear();

        buildRoomDecorIndex(resourceManager);

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_HOUSES, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        HouseConfig config = GSON.fromJson(reader, HouseConfig.class);
                        int houseDecorCount = countHouseDecorations(resourceManager, config.type());
                        int totalRooms = 0;
                        List<RoomSlot> updatedRooms = new ArrayList<>(config.rooms().size());
                        for (RoomSlot room : config.rooms()) {
                            RoomDecorInfo roomDecorInfo = getRoomDecorInfo(config.type(), room.nbt());
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

    private static void buildRoomDecorIndex(ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                ModPaths.STRUCTURES_CITIES_PLAINS_ROOMS,
                path -> path.getPath().endsWith(".nbt")
        );

        String basePath = ModPaths.STRUCTURES_CITIES_PLAINS_ROOMS + "/";

        for (ResourceLocation location : resources.keySet()) {
            String path = location.getPath();
            if (!path.startsWith(basePath)) continue;

            String relative = path.substring(basePath.length());
            int slashIdx = relative.indexOf('/');
            if (slashIdx < 0) continue;

            String houseType = relative.substring(0, slashIdx);
            String fileName = relative.substring(slashIdx + 1);
            if (!fileName.endsWith(".nbt")) continue;
            fileName = fileName.substring(0, fileName.length() - 4);

            ParsedRoom parsed = parseRoomFileName(fileName);
            if (parsed == null) continue;

            Map<String, Integer> houseMaxDecor = MAX_DECOR_INDEX.computeIfAbsent(houseType, k -> new HashMap<>());
            int currentMax = houseMaxDecor.getOrDefault(parsed.roomNbt, -1);
            if (parsed.decor > currentMax) {
                houseMaxDecor.put(parsed.roomNbt, parsed.decor);
            }

            if (parsed.isBaseDecor) {
                Map<String, Set<Integer>> houseBaseDecors = BASE_DECORS_INDEX.computeIfAbsent(houseType, k -> new HashMap<>());
                Set<Integer> baseDecors = houseBaseDecors.computeIfAbsent(parsed.roomNbt, k -> new HashSet<>());
                baseDecors.add(parsed.decor);
            }
        }
    }

    private static ParsedRoom parseRoomFileName(String fileName) {
        int searchFrom = fileName.length();
        while (searchFrom > 0) {
            int lastUnderscore = fileName.lastIndexOf('_', searchFrom - 1);
            if (lastUnderscore < 0) break;

            String afterLast = fileName.substring(lastUnderscore + 1);

            int numberEnd = 0;
            while (numberEnd < afterLast.length() && Character.isDigit(afterLast.charAt(numberEnd))) {
                numberEnd++;
            }
            if (numberEnd > 0) {
                int decor = Integer.parseInt(afterLast.substring(0, numberEnd));
                String roomNbt = fileName.substring(0, lastUnderscore);
                String suffix = afterLast.substring(numberEnd);
                boolean isBaseDecor = suffix.isEmpty();
                return new ParsedRoom(roomNbt, decor, isBaseDecor);
            }

            searchFrom = lastUnderscore;
        }
        return null;
    }

    private record ParsedRoom(String roomNbt, int decor, boolean isBaseDecor) {}

    private static RoomDecorInfo getRoomDecorInfo(String houseType, String roomNbt) {
        Map<String, Integer> houseMaxDecor = MAX_DECOR_INDEX.get(houseType);
        Map<String, Set<Integer>> houseBaseDecors = BASE_DECORS_INDEX.get(houseType);

        int maxDecor = (houseMaxDecor != null) ? houseMaxDecor.getOrDefault(roomNbt, -1) : -1;
        Set<Integer> baseDecors = (houseBaseDecors != null)
                ? houseBaseDecors.getOrDefault(roomNbt, Collections.emptySet())
                : Collections.emptySet();

        return new RoomDecorInfo(maxDecor + 1, baseDecors);
    }

    public static void clear() {
        CONFIGS.clear();
        MAX_DECOR_INDEX.clear();
        BASE_DECORS_INDEX.clear();
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

    public static HouseConfig getConfig(String type) {
        return CONFIGS.get(type);
    }
}