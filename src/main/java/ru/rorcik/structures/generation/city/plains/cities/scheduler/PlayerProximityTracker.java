package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.generation.GeneratorConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerProximityTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, CityInfo> ACTIVE_CITIES = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    public enum CityState { PENDING, GENERATING, PAUSED, COMPLETED }

    public static class CityInfo {
        public final String worldId;
        public final String cityId;
        public final int centerX;
        public final int centerZ;
        public final Set<Long> affectedChunks;
        public CityState state;

        public CityInfo(String worldId, int centerX, int centerZ, Set<Long> affectedChunks) {
            this.worldId = worldId;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.affectedChunks = Collections.unmodifiableSet(new HashSet<>(affectedChunks));
            
            this.cityId = worldId + "_city@" + centerX + "," + centerZ;
            this.state = CityState.PENDING;
        }
    }

    public static void registerCity(String worldId, int centerX, int centerZ, Set<Long> affectedChunks) {
        CityInfo info = new CityInfo(worldId, centerX, centerZ, affectedChunks);
        ACTIVE_CITIES.put(info.cityId, info);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [PROXIMITY] Registered city '{}' for proximity tracking", info.cityId);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < RorcikStructuresModConfig.proximityCheckIntervalTicks) return;
        tickCounter = 0;

        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        if (player == null) return;

        int playerX = player.getBlockX();
        int playerZ = player.getBlockZ();
        int bufferDistance = RorcikStructuresModConfig.bufferDistance;
        int abandonDistance = RorcikStructuresModConfig.abandonDistance;

        Iterator<Map.Entry<String, CityInfo>> iterator = ACTIVE_CITIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CityInfo> entry = iterator.next();
            CityInfo city = entry.getValue();

            if (!city.worldId.equals(CityJournal.getWorldId(level))) {
                continue;
            }

            CityGenerationSavedData genData = CityGenerationSavedData.get(level);
            CityGenerationSavedData.CityGenState state = genData.getState(city.centerX, city.centerZ);
            if (state != null && state.isComplete()) {
                city.state = CityState.COMPLETED;
                iterator.remove(); 
                continue;
            }

            double distance = calculateDistance(playerX, playerZ, city.centerX, city.centerZ, city.affectedChunks);

            switch (city.state) {
                case PENDING:
                    if (distance <= bufferDistance) {
                        if (RorcikStructuresModConfig.enableLogging) {
                            LOGGER.info("Info: [PROXIMITY] Player approached city '{}' (distance={} <= {})", city.cityId, distance, bufferDistance);
                        }
                        city.state = CityState.GENERATING;
                        if (!startGeneration(city, level, playerX, playerZ)) {
                            LOGGER.warn("Warn: [PROXIMITY] startGeneration failed for '{}', reverting to PENDING", city.cityId);
                            city.state = CityState.PENDING;
                        }
                    }
                    break;
                case GENERATING:
                    if (distance > abandonDistance) {
                        if (RorcikStructuresModConfig.enableLogging) {
                            LOGGER.info("Info: [PROXIMITY] Player left city '{}' (distance={} > {}) — pausing", city.cityId, distance, abandonDistance);
                        }
                        city.state = CityState.PAUSED;
                        CityBuildScheduler.pauseCityGeneration(city, level);
                    }
                    break;
                case PAUSED:
                    if (distance <= bufferDistance) {
                        if (RorcikStructuresModConfig.enableLogging) {
                            LOGGER.info("Info: [PROXIMITY] Player returned to city '{}' — resuming", city.cityId);
                        }
                        city.state = CityState.GENERATING;
                        if (!CityBuildScheduler.resumeCityGeneration(city, level, playerX, playerZ)) {
                            LOGGER.warn("Warn: [PROXIMITY] resumeCityGeneration failed for '{}', reverting to PAUSED", city.cityId);
                            city.state = CityState.PAUSED;
                        }
                    }
                    break;
                case COMPLETED:
                    iterator.remove();
                    break;
            }
        }
    }

    private static boolean startGeneration(CityInfo city, ServerLevel level, int playerX, int playerZ) {
        CityGenerationContext context = CityGenerationContextRegistry.get(city.worldId, city.centerX, city.centerZ);
        return CityBuildScheduler.startCityGeneration(city, level, context, playerX, playerZ);
    }

    private static double calculateDistance(int playerX, int playerZ, int centerX, int centerZ, Set<Long> affectedChunks) {
        double minDist = Double.MAX_VALUE;
        for (long chunkKey : affectedChunks) {
            int cx = (int)(chunkKey >> 32);
            int cz = (int)(chunkKey & 0xFFFFFFFFL);
            int blockX = (cx << 4) + 8;
            int blockZ = (cz << 4) + 8;
            double dist = Math.sqrt(Math.pow(playerX - blockX, 2) + Math.pow(playerZ - blockZ, 2));
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist == Double.MAX_VALUE ? Math.sqrt(Math.pow(playerX - centerX, 2) + Math.pow(playerZ - centerZ, 2)) : minDist;
    }

    public static void reset() {
        ACTIVE_CITIES.clear();
        tickCounter = 0;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [PROXIMITY] Tracker reset");
        }
    }
}