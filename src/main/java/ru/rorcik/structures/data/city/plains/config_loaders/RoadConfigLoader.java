package ru.rorcik.structures.data.city.plains.config_loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IConfigLoader;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadDecoration;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoadConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, RoadConfig> CONFIGS = new HashMap<>();
    private static Map<Set<Direction>, RoadMapEntry> ROAD_MAP = Collections.emptyMap();

    public record RoadMapEntry(String type, Rotation rotation) {}

    public static void loadConfigs(ResourceManager resourceManager) {
        CONFIGS.clear();

        resourceManager.listResources(ModPaths.CONFIGS_CITIES_PLAINS_ROADS, path -> path.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        RoadConfig rawConfig = GSON.fromJson(reader, RoadConfig.class);

                        int decorCount = countRoadDecorations(resourceManager, rawConfig.type());
                        List<Integer> stopVariants = rawConfig.stopVariants() != null
                                ? rawConfig.stopVariants()
                                : Collections.emptyList();
                        List<String> connections = rawConfig.connections() != null
                                ? rawConfig.connections()
                                : Collections.emptyList();
                        List<RoadDecoration> decorations = rawConfig.decorations() != null
                                ? rawConfig.decorations()
                                : Collections.emptyList();

                        RoadConfig finalConfig = new RoadConfig(
                                rawConfig.type(),
                                rawConfig.offset(),
                                rawConfig.size(),
                                decorCount,
                                connections,
                                stopVariants,
                                decorations
                        );

                        CONFIGS.put(finalConfig.type(), finalConfig);
                        LOGGER.info("Loaded road config '{}' ({} variants, {} connections, {} decorations)",
                                finalConfig.type(), decorCount, connections.size(), decorations.size());

                    } catch (Exception e) {
                        LOGGER.error("Failed to load road config: {}", location, e);
                    }
                });

        buildRoadMap();
    }

    private static void buildRoadMap() {
        Map<Set<Direction>, RoadMapEntry> tempMap = new HashMap<>();

        for (RoadConfig config : CONFIGS.values()) {
            for (Rotation rotation : Rotation.values()) {
                List<Direction> connections = RoadNetwork.getRotatedConnections(config, rotation);
                Set<Direction> key = new HashSet<>(connections);

                if (!tempMap.containsKey(key)) {
                    tempMap.put(key, new RoadMapEntry(config.type(), rotation));
                }
            }
        }

        ROAD_MAP = Collections.unmodifiableMap(tempMap);
        LOGGER.info("Road map built with {} entries", ROAD_MAP.size());
    }

    public static RoadMapEntry findRoadForConnections(List<Direction> connections) {
        Set<Direction> key = new HashSet<>(connections);
        return ROAD_MAP.get(key);
    }

    public static void clear() {
        CONFIGS.clear();
        ROAD_MAP = Collections.emptyMap();
    }

    private static int countRoadDecorations(ResourceManager resourceManager, String roadType) {
        int maxDecor = -1;
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                ModPaths.STRUCTURES_CITIES_PLAINS_ROADS + "/" + roadType,
                path -> path.getPath().endsWith(".nbt")
        );

        for (ResourceLocation location : resources.keySet()) {
            String fileName = location.getPath();
            String prefix = roadType + "_";
            int prefixIndex = fileName.indexOf(prefix);

            if (prefixIndex >= 0) {
                String numberPart = fileName.substring(prefixIndex + prefix.length()).replace(".nbt", "");
                try {
                    maxDecor = Math.max(maxDecor, Integer.parseInt(numberPart));
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxDecor + 1;
    }

    public static RoadConfig getConfig(String type) {
        return CONFIGS.get(type);
    }

    public static Map<String, RoadConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}