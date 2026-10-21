package ru.rorcik.structures.planning;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContextRegistry;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.PlayerProximityTracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoCityReservationListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TEST_X = 11008;
    private static final int TEST_Z = 0;
    private static final Map<String, Boolean> ATTEMPTED_WORLDS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ServerLevel level = server.overworld();
        if (level == null) return;

        String worldId = getWorldId(level);
        if (ATTEMPTED_WORLDS.getOrDefault(worldId, false)) return;

        if (!StructureCache.isInitialized()) {
            return;
        }

        ATTEMPTED_WORLDS.put(worldId, true);

        LOGGER.info("Info: ==================================================");
        LOGGER.info("Info: [AUTO-RESERVE] Server and Cache ready for world {}. Attempting to reserve city at X: {}, Z: {}", worldId, TEST_X, TEST_Z);

        try {
            PlayerProximityTracker.reset();
            CityGenerationContextRegistry.clear();
            ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
            registry.clearAllZones();

            LOGGER.info("Cleanup: [AUTO-RESERVE] Old data cleared");

            boolean success = CityZonePlanner.tryReserveCity(level, TEST_X, TEST_Z, CityType.PLAINS);
            if (!success) {
                LOGGER.error("Error: [AUTO-RESERVE] Failed to reserve city zone");
            } else {
                LOGGER.info("Success: [AUTO-RESERVE] City zone prepared and ready");
            }
        } catch (Exception e) {
            LOGGER.error("Error: [AUTO-RESERVE] Exception during city reservation: {}", e.getMessage(), e);
        }

        LOGGER.info("Info: ==================================================");
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        String worldId = getWorldId(level);
        ATTEMPTED_WORLDS.remove(worldId);

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [AUTO-RESERVE] Cleared attempt flag for world {}", worldId);
        }
    }

    private static String getWorldId(ServerLevel level) {
        java.nio.file.Path worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }
}