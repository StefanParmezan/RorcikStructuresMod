package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.planning.AutoCityReservationListener;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructuresWorldCleanupListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                
                CityGenerationContextRegistry.clear();
                PlayerProximityTracker.reset();
                CityBuildScheduler.reset();
                AutoCityReservationListener.reset();

                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Cleanup: [STRUCTURES] Static state fully reset for world unload.");
                }
            }
        }
    }
}