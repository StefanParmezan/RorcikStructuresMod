package ru.rorcik.structures.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
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
import java.util.HashMap;
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
                        CONFIGS.put(config.type(), config);
                        LOGGER.info("Loaded house config: {}", config.type());

                    } catch (Exception e) {
                        LOGGER.error("Failed to load config: {}", location, e);
                    }
                });
    }

    public static HouseConfig getConfig(String type) {
        return CONFIGS.get(type);
    }
}