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
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoCityReservationListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int[][] TEST_POSITIONS = {
            {11008, 0}, {11000, 11000}, {-11000, 11000}, {11000, -11000}, {-11000, -11000}
    };

    private static final Set<String> SUCCESSFUL_RESERVATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> ATTEMPT_COUNTS = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS_PER_POS = 20;

    private static volatile boolean isFullyInitialized = false;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (isFullyInitialized) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!StructureCache.isInitialized()) return;

        String worldId = getWorldId(level);
        int successCount = 0;

        for (int[] pos : TEST_POSITIONS) {
            int x = pos[0], z = pos[1];
            String posKey = worldId + "_" + x + "_" + z;

            if (SUCCESSFUL_RESERVATIONS.contains(posKey)) {
                successCount++;
                continue;
            }

            int attempts = ATTEMPT_COUNTS.getOrDefault(posKey, 0);
            if (attempts >= MAX_ATTEMPTS_PER_POS) continue;
            ATTEMPT_COUNTS.put(posKey, attempts + 1);

            if (RorcikStructuresModConfig.enableLogging && attempts == 0) {
                LOGGER.info("Info: [AUTO-RESERVE] → Attempting city at X: {}, Z: {}", x, z);
            }

            try {
                boolean success = CityZonePlanner.tryReserveCity(level, x, z, CityType.PLAINS);
                if (success) {
                    SUCCESSFUL_RESERVATIONS.add(posKey);
                    successCount++;
                    LOGGER.info("Success: [AUTO-RESERVE] ✓ Reserved at X: {}, Z: {}", x, z);
                }
            } catch (Exception e) {
                LOGGER.error("Error: [AUTO-RESERVE] ✗ Exception at X: {}, Z: {}", x, z, e);
            }
        }

        if (successCount == TEST_POSITIONS.length) {
            isFullyInitialized = true;
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [AUTO-RESERVE] Summary: {}/{} cities reserved. Task completed. Listener disabled.", successCount, TEST_POSITIONS.length);
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

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [AUTO-RESERVE] Cleared attempt data and reset initialization flag for world {}", worldId);
        }
    }

    public static void reset() {
        SUCCESSFUL_RESERVATIONS.clear();
        ATTEMPT_COUNTS.clear();
        isFullyInitialized = false;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [AUTO-RESERVE] Tracker fully reset");
        }
    }

    private static String getWorldId(ServerLevel level) {
        java.nio.file.Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }
}