package ru.rorcik.structures.planning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContextRegistry;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.PlayerProximityTracker;

@Mod.EventBusSubscriber(modid = "rorcik_structures", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoCityReservationListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TEST_X = 11008;
    private static final int TEST_Z = 0;
    private static boolean hasAttempted = false;

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (hasAttempted) return;
        hasAttempted = true;

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: ==================================================");
            LOGGER.info("Info: Attempting to reserve city at X: {}, Z: {}", TEST_X, TEST_Z);
        }

        PlayerProximityTracker.reset();
        CityGenerationContextRegistry.clear();
        ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
        registry.clearAllZones();

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: Old data cleared");
        }

        boolean success = CityZonePlanner.tryReserveCity(level, TEST_X, TEST_Z, CityType.PLAINS);
        if (!success) {
            LOGGER.error("Error: Failed to reserve city zone");
        } else {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: City zone prepared and ready");
            }
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: ==================================================");
        }
    }
}