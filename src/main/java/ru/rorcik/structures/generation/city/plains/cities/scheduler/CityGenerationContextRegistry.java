package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CityGenerationContextRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, CityGenerationContext> CONTEXTS = new ConcurrentHashMap<>();

    public static void register(CityGenerationContext context) {
        CONTEXTS.put(context.cityId(), context);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CONTEXT-REGISTRY] Registered context for city '{}' ({} slots, {} road nodes)",
                    context.cityId(), context.slots().size(), context.nodeMap().size());
        }
    }

    public static CityGenerationContext get(String worldId, int centerX, int centerZ) {
        return CONTEXTS.get(worldId + "_city@" + centerX + "," + centerZ);
    }

    public static CityGenerationContext get(String cityId) {
        return CONTEXTS.get(cityId);
    }

    public static Collection<CityGenerationContext> getAll() {
        return CONTEXTS.values();
    }

    public static void clear() {
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [CONTEXT-REGISTRY] Clearing registry ({} contexts)", CONTEXTS.size());
        }
        CONTEXTS.clear();
    }

    public static int size() {
        return CONTEXTS.size();
    }
}