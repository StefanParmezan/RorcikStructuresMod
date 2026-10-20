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
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContext;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationContextRegistry;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.CityGenerationSavedData;
import ru.rorcik.structures.generation.city.plains.cities.scheduler.PlayerProximityTracker;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoCityReservationListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TEST_X = 11008;
    private static final int TEST_Z = 0;
    private static boolean hasAttempted = false;

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            LOGGER.warn("Warn: [AUTO-RESERVE] Level is not ServerLevel");
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            LOGGER.warn("Warn: [AUTO-RESERVE] Dimension is not OVERWORLD");
            return;
        }
        if (hasAttempted) {
            LOGGER.warn("Warn: [AUTO-RESERVE] Already attempted, skipping");
            return;
        }
        hasAttempted = true;

        LOGGER.info("Info: ==================================================");
        LOGGER.info("Info: [AUTO-RESERVE] Level loaded, attempting to reserve city at X: {}, Z: {}", TEST_X, TEST_Z);
        LOGGER.info("Info: [AUTO-RESERVE] StructureCache initialized: {}", StructureCache.isInitialized());

        if (!StructureCache.isInitialized()) {
            LOGGER.error("Error: [AUTO-RESERVE] StructureCache not initialized! Deferring to next tick...");
            hasAttempted = false;
            return;
        }

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

        LOGGER.info("Info: ==================================================");
    }
}