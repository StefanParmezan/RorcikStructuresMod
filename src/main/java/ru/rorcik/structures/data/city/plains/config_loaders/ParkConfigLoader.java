package ru.rorcik.structures.data.city.plains.config_loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IConfigLoader;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.containers.ParkConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParkConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final Gson GSON = new GsonBuilder().create();
    private static final List<ParkConfig> PARKS = new ArrayList<>();
    private static final int DEFAULT_WORLD_COUNT = -1;

    public static void loadConfigs(ResourceManager resourceManager) {
        PARKS.clear();

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_PARKS, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        ParkConfig rawConfig = GSON.fromJson(reader, ParkConfig.class);

                        int worldCount = rawConfig.worldCount() != 0
                                ? rawConfig.worldCount()
                                : DEFAULT_WORLD_COUNT;

                        ParkConfig config = new ParkConfig(
                                rawConfig.name(),
                                rawConfig.size(),
                                rawConfig.yOffset(),
                                worldCount
                        );

                        PARKS.add(config);
                        String limit = worldCount < 0 ? "unlimited" : "max " + worldCount + " per world";
                        LOGGER.info("Loaded park config '{}' (size: {}x{}x{}, yOffset: {}, {})",
                                config.name(), config.size()[0], config.size()[1], config.size()[2],
                                config.yOffset(), limit);

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

    public static List<ParkConfig> getLargeParks() {
        return PARKS.stream()
                .filter(ParkConfig::isLargePark)
                .toList();
    }

    public static void clear() {
        PARKS.clear();
    }
}