package ru.rorcik.structures.data.city.plains.config_loaders;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.api.IConfigLoader;
import ru.rorcik.structures.common.ModPaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildingConfigLoader implements IConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, List<String>> BUILDING_REGISTRY = new HashMap<>();

    public static void loadConfigs(ResourceManager resourceManager) {
        BUILDING_REGISTRY.clear();

        String basePath = ModPaths.STRUCTURES_CITIES_PLAINS_BUILDINGS;
        String[] categories = {"houses", "lootable", "other"};

        for (String category : categories) {
            List<String> buildings = new ArrayList<>();
            String categoryPath = basePath + "/" + category;

            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                    categoryPath,
                    path -> path.getPath().endsWith(".nbt")
            );

            for (ResourceLocation location : resources.keySet()) {
                String fileName = location.getPath();
                String buildingName = extractBuildingName(fileName, categoryPath);
                if (buildingName != null && !buildingName.isEmpty()) {
                    buildings.add(buildingName);
                }
            }

            if (!buildings.isEmpty()) {
                BUILDING_REGISTRY.put(category, buildings);
                LOGGER.info("Loaded {} buildings in category '{}'", buildings.size(), category);
            }
        }
    }

    private static String extractBuildingName(String fullPath, String basePath) {
        if (!fullPath.startsWith(basePath + "/")) {
            return null;
        }

        String relativePath = fullPath.substring(basePath.length() + 1);
        if (relativePath.endsWith(".nbt")) {
            return relativePath.substring(0, relativePath.length() - 4);
        }
        return null;
    }

    public static List<String> getBuildings(String category) {
        return BUILDING_REGISTRY.getOrDefault(category, Collections.emptyList());
    }

    public static boolean hasCategory(String category) {
        return BUILDING_REGISTRY.containsKey(category) && !BUILDING_REGISTRY.get(category).isEmpty();
    }

    public static void clear() {
        BUILDING_REGISTRY.clear();
    }
}