package ru.rorcik.structures.planning;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RandomCityDiscoveryListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DISCOVERY_RADIUS = 512;
    private static final Set<CityCandidate> ATTEMPTED = new HashSet<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (RorcikStructuresModConfig.enableCityCords) return;

        tickCounter++;
        if (tickCounter < RorcikStructuresModConfig.proximityCheckIntervalTicks) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;

        ServerLevel level = server.overworld();
        if (level == null) return;

        RandomCityZoneProvider provider = CityZoneProviderListener.currentProvider();
        if (provider == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int playerX = player.getBlockX();
            int playerZ = player.getBlockZ();

            for (CityCandidate candidate : provider.candidatesNear(playerX, playerZ, DISCOVERY_RADIUS)) {
                if (!ATTEMPTED.add(candidate)) continue;
                if (!provider.isCandidateValid(candidate)) continue;

                CityLayoutPlan plan = provider.getLayoutPlan(candidate);
                if (plan == null) continue;

                if (CityZonePlanner.tryReserveRandomCity(level, candidate, plan)
                        && RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Info: [CITY-DISCOVERY] Reserved random {} city at [{}, {}]",
                            candidate.type().id, candidate.centerX(), candidate.centerZ());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        ATTEMPTED.clear();
        tickCounter = 0;
    }
}
