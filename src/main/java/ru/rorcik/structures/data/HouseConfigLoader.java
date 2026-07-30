package ru.rorcik.structures.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.RorcikStructuresMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HouseConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, HouseConfig> CONFIGS = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ResourceManager resourceManager = event.getServer().getResourceManager();
        loadConfigs(resourceManager);
    }

    private static void loadConfigs(ResourceManager resourceManager) {
        CONFIGS.clear();

        resourceManager.listResources("configs/houses", path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        HouseConfig config = GSON.fromJson(reader, HouseConfig.class);

                        int houseDecorCount = countHouseDecorations(resourceManager, config.type());
                        LOGGER.info("House '{}' has {} decorations", config.type(), houseDecorCount);

                        List<RoomSlot> updatedRooms = new ArrayList<>();
                        for (RoomSlot room : config.rooms()) {
                            int roomDecorCount = countRoomDecorations(resourceManager, config.type(), room.nbt());
                            LOGGER.info("Room '{}' in house '{}' has {} decorations", room.nbt(), config.type(), roomDecorCount);

                            room.setDecorCount(roomDecorCount);
                            updatedRooms.add(room);
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
                        LOGGER.info("Loaded house config: {}", finalConfig.type());

                    } catch (Exception e) {
                        LOGGER.error("Failed to load config: {}", location, e);
                    }
                });
    }

    private static int countHouseDecorations(ResourceManager resourceManager, String houseType) {
        int maxDecor = -1;

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "structures/houses/" + houseType,
                path -> path.getPath().endsWith(".nbt")
        );

        for (ResourceLocation location : resources.keySet()) {
            String fileName = location.getPath();
            String prefix = houseType + "_";
            int prefixIndex = fileName.indexOf(prefix);

            if (prefixIndex >= 0) {
                String numberPart = fileName.substring(prefixIndex + prefix.length());
                numberPart = numberPart.replace(".nbt", "");
                try {
                    int decor = Integer.parseInt(numberPart);
                    maxDecor = Math.max(maxDecor, decor);
                } catch (NumberFormatException e) {
                    // Игнорируем файлы без числа
                }
            }
        }

        return maxDecor + 1;
    }

    private static int countRoomDecorations(ResourceManager resourceManager, String houseType, String roomNbt) {
        int maxDecor = -1;

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "structures/rooms/" + houseType,
                path -> path.getPath().endsWith(".nbt") && path.getPath().contains(roomNbt + "_")
        );

        for (ResourceLocation location : resources.keySet()) {
            String fileName = location.getPath();
            String prefix = roomNbt + "_";
            int prefixIndex = fileName.indexOf(prefix);

            if (prefixIndex >= 0) {
                String afterPrefix = fileName.substring(prefixIndex + prefix.length());
                afterPrefix = afterPrefix.replace(".nbt", "");
                afterPrefix = afterPrefix.replaceAll("_[vg]+$", "");

                try {
                    int decor = Integer.parseInt(afterPrefix);
                    maxDecor = Math.max(maxDecor, decor);
                } catch (NumberFormatException e) {
                    // Игнорируем
                }
            }
        }

        return maxDecor + 1;
    }

    public static HouseConfig getConfig(String type) {
        return CONFIGS.get(type);
    }
}