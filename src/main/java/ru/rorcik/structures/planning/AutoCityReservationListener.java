package ru.rorcik.structures.planning;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoCityReservationListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record TestCityTask(int x, int z, CityType type) {}

    private static List<TestCityTask> TEST_CITIES = new ArrayList<>();

    private static final Set<String> SUCCESSFUL_RESERVATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> ATTEMPT_COUNTS = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS_PER_POS = 20;

    private static volatile boolean isFullyInitialized = false;

    private static void rebuildTaskList() {
        List<TestCityTask> newTasks = new ArrayList<>();

        for (String coord : RorcikStructuresModConfig.plainsCityCoords) {
            String[] parts = coord.split(",");
            if (parts.length == 2) {
                try {
                    newTasks.add(new TestCityTask(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), CityType.PLAINS));
                } catch (NumberFormatException e) {
                    LOGGER.warn("Warn: [AUTO-RESERVE] Invalid plains coordinate format: '{}'. Expected 'x,z'", coord);
                }
            }
        }

        for (String coord : RorcikStructuresModConfig.desertCityCoords) {
            String[] parts = coord.split(",");
            if (parts.length == 2) {
                try {
                    newTasks.add(new TestCityTask(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), CityType.DESERT));
                } catch (NumberFormatException e) {
                    LOGGER.warn("Warn: [AUTO-RESERVE] Invalid desert coordinate format: '{}'. Expected 'x,z'", coord);
                }
            }
        }

        TEST_CITIES = newTasks;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [AUTO-RESERVE] Loaded {} city spawn coordinates from config.", TEST_CITIES.size());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!RorcikStructuresModConfig.enableCityCords) return;
        if (isFullyInitialized) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!StructureCache.isInitialized()) return;

        if (TEST_CITIES.isEmpty()) {
            rebuildTaskList();
        }

        String worldId = getWorldId(level);
        int successCount = 0;

        for (TestCityTask task : TEST_CITIES) {
            String posKey = worldId + "_" + task.x + "_" + task.z + "_" + task.type.id;

            if (SUCCESSFUL_RESERVATIONS.contains(posKey)) {
                successCount++;
                continue;
            }

            int attempts = ATTEMPT_COUNTS.getOrDefault(posKey, 0);
            if (attempts >= MAX_ATTEMPTS_PER_POS) continue;
            ATTEMPT_COUNTS.put(posKey, attempts + 1);

            if (RorcikStructuresModConfig.enableLogging && attempts == 0) {
                LOGGER.info("Info: [AUTO-RESERVE] → Attempting {} city at X: {}, Z: {} (attempt {}/{})",
                        task.type.id, task.x, task.z, attempts + 1, MAX_ATTEMPTS_PER_POS);
            }

            try {
                boolean success = CityZonePlanner.tryReserveCity(level, task.x, task.z, task.type);
                if (success) {
                    SUCCESSFUL_RESERVATIONS.add(posKey);
                    successCount++;
                    LOGGER.info("Success: [AUTO-RESERVE] ✓ Reserved {} city at X: {}, Z: {}", task.type.id, task.x, task.z);
                } else {
                    if (RorcikStructuresModConfig.enableLogging) {
                        LOGGER.warn("Warn: [AUTO-RESERVE] ✗ Failed to reserve {} city at X: {}, Z: {}", task.type.id, task.x, task.z);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error: [AUTO-RESERVE] ✗ Exception at X: {}, Z: {}", task.x, task.z, e);
            }
        }

        if (successCount == TEST_CITIES.size() && !TEST_CITIES.isEmpty()) {
            isFullyInitialized = true;
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [AUTO-RESERVE] Summary: {}/{} cities reserved. Task completed. Listener disabled.", successCount, TEST_CITIES.size());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        String worldId = getWorldId(level);

        SUCCESSFUL_RESERVATIONS.removeIf(key -> key.startsWith(worldId + "_"));
        ATTEMPT_COUNTS.keySet().removeIf(key -> key.startsWith(worldId + "_"));
        isFullyInitialized = false;
        TEST_CITIES = new ArrayList<>(); 

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [AUTO-RESERVE] Cleared attempt data and reset initialization flag for world {}", worldId);
        }
    }

    public static void reset() {
        SUCCESSFUL_RESERVATIONS.clear();
        ATTEMPT_COUNTS.clear();
        isFullyInitialized = false;
        TEST_CITIES = new ArrayList<>();
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [AUTO-RESERVE] Tracker fully reset");
        }
    }

    private static String getWorldId(ServerLevel level) {
        java.nio.file.Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }
}