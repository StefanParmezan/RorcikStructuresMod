package ru.rorcik.structures.data.city.plains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoadConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, RoadConfig> CONFIGS = new HashMap<>();

    public static void loadConfigs(ResourceManager resourceManager) {
        CONFIGS.clear();

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_ROADS, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        RoadConfig config = GSON.fromJson(reader, RoadConfig.class);
                        List<Integer> stopVariants = config.stopVariants() != null
                                ? config.stopVariants()
                                : Collections.emptyList();

                        RoadConfig finalConfig = new RoadConfig(
                                config.type(),
                                config.offset(),
                                config.size(),
                                config.decorCount(),
                                config.connections(),
                                stopVariants
                        );

                        CONFIGS.put(finalConfig.type(), finalConfig);
                        LOGGER.info("Loaded road config '{}' ({} variants, {} connections)",
                                finalConfig.type(), finalConfig.decorCount(), finalConfig.connections().size());

                    } catch (Exception e) {
                        LOGGER.error("Failed to load road config: {}", location, e);
                    }
                });
    }

    public static void clear() {
        CONFIGS.clear();
    }

    public static RoadConfig getConfig(String type) {
        return CONFIGS.get(type);
    }

    public static Map<String, RoadConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}