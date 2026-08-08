package ru.rorcik.structures.data.city.plains.config_loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IConfigLoader;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParkConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final List<ParkConfig> PARKS = new ArrayList<>();

    public static void loadConfigs(ResourceManager resourceManager) {
        PARKS.clear();

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_PARKS, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        ParkConfig config = GSON.fromJson(reader, ParkConfig.class);
                        PARKS.add(config);
                        LOGGER.info("Loaded park config '{}' (size: {}x{}x{}, yOffset: {})",
                                config.name(), config.size()[0], config.size()[1], config.size()[2], config.yOffset());

                    } catch (Exception e) {
                        LOGGER.error("Failed to load park config: {}", location, e);
                    }
                });
    }

    public static List<ParkConfig> getParks() {
        return Collections.unmodifiableList(PARKS);
    }

    public static ParkConfig getPark(String name) {
        return PARKS.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static void clear() {
        PARKS.clear();
    }
}