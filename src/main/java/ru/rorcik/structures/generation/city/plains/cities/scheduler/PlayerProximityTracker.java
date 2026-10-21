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
import ru.rorcik.structures.planning.CityZonePlanner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerProximityTracker {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum CityState { PENDING, GENERATING, PAUSED, COMPLETED }

    public static class CityInfo {
        public final int centerX;
        public final int centerZ;
        public final String cityId;
        public final int[] boundingBox;
        public volatile CityState state = CityState.PENDING;
        public volatile int lastDistance = Integer.MAX_VALUE;

        public CityInfo(int centerX, int centerZ, int[] boundingBox) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.boundingBox = boundingBox;
            this.cityId = "city@" + centerX + "," + centerZ;
        }
    }

    private static final Map<String, CityInfo> ACTIVE_CITIES = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    public static void registerCity(int centerX, int centerZ, Set<Long> chunks) {
        int[] bbox = calculateBoundingBox(chunks);
        CityInfo info = new CityInfo(centerX, centerZ, bbox);
        ACTIVE_CITIES.put(info.cityId, info);
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [PROXIMITY] Registered city '{}' | bbox: [{},{}] - [{},{}]",
                    info.cityId, bbox[0], bbox[2], bbox[1], bbox[3]);
        }
    }

    private static int[] calculateBoundingBox(Set<Long> chunks) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : chunks) {
            int cx = (int)(key >> 32);
            int cz = (int)(key & 0xFFFFFFFFL);
            minX = Math.min(minX, cx << 4);
            maxX = Math.max(maxX, (cx << 4) | 15);
            minZ = Math.min(minZ, cz << 4);
            maxZ = Math.max(maxZ, (cz << 4) | 15);
        }
        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, 0, 0};
        return new int[]{minX, maxX, minZ, maxZ};
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < GeneratorConfig.PROXIMITY_CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;
        if (ACTIVE_CITIES.isEmpty()) return;

        ServerLevel level = event.getServer().overworld();
        if (level == null) return;

        for (ServerPlayer player : level.players()) {
            if (!player.level().dimension().equals(Level.OVERWORLD)) continue;
            int playerX = (int) player.getX();
            int playerZ = (int) player.getZ();
            CityBuildScheduler.updatePriorities(playerX, playerZ);

            for (CityInfo city : ACTIVE_CITIES.values()) {
                int distance = distanceToBoundingBox(playerX, playerZ, city.boundingBox);
                city.lastDistance = distance;
                processCity(city, distance, level, playerX, playerZ);
            }
        }
    }

    private static int distanceToBoundingBox(int playerX, int playerZ, int[] bbox) {
        int dx = Math.max(0, Math.max(bbox[0] - playerX, playerX - bbox[1]));
        int dz = Math.max(0, Math.max(bbox[2] - playerZ, playerZ - bbox[3]));
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static void processCity(CityInfo city, int distance, ServerLevel level, int playerX, int playerZ) {
        int bufferDistance = GeneratorConfig.BUFFER_DISTANCE;
        int abandonDistance = GeneratorConfig.ABANDON_DISTANCE;

        CityGenerationSavedData.CityGenState state = CityGenerationSavedData.get(level).getState(city.centerX, city.centerZ);
        if (state != null && state.isComplete()) {
            city.state = CityState.COMPLETED;
            return;
        }

        switch (city.state) {
            case PENDING:
                if (distance <= bufferDistance) {
                    if (RorcikStructuresModConfig.enableLogging) {
                        LOGGER.info("Info: [PROXIMITY] Player approached city '{}' (bbox distance={} <= {})",
                                city.cityId, distance, bufferDistance);
                    }
                    city.state = CityState.GENERATING;
                    // >>> FIX: если старт провалился (например, нет контекста),
                    // откатываем обратно в PENDING, чтобы попробовать позже.
                    if (!startGeneration(city, level, playerX, playerZ)) {
                        LOGGER.warn("Warn: [PROXIMITY] startGeneration failed for '{}', reverting to PENDING", city.cityId);
                        city.state = CityState.PENDING;
                    }
                }
                break;
            case GENERATING:
                if (distance > abandonDistance) {
                    if (RorcikStructuresModConfig.enableLogging) {
                        LOGGER.info("Info: [PROXIMITY] Player left city '{}' (bbox distance={} > {}) — entering cold mode",
                                city.cityId, distance, abandonDistance);
                    }
                    city.state = CityState.PAUSED;
                    CityBuildScheduler.pauseCityGeneration(city, level);
                }
                break;
            case PAUSED:
                if (distance <= bufferDistance) {
                    if (RorcikStructuresModConfig.enableLogging) {
                        LOGGER.info("Info: [PROXIMITY] Player returned to city '{}' — resuming from journal", city.cityId);
                    }
                    city.state = CityState.GENERATING;
                    // >>> FIX: аналогично PENDING — откатываемся при неудаче.
                    if (!CityBuildScheduler.resumeCityGeneration(city, level, playerX, playerZ)) {
                        LOGGER.warn("Warn: [PROXIMITY] resumeCityGeneration failed for '{}', reverting to PAUSED", city.cityId);
                        city.state = CityState.PAUSED;
                    }
                }
                break;
            case COMPLETED:
                break;
        }
    }

    private static boolean startGeneration(CityInfo city, ServerLevel level, int playerX, int playerZ) {
        CityGenerationContext context = CityGenerationContextRegistry.get(city.centerX, city.centerZ);

        if (context == null) {
            context = CityZonePlanner.rebuildContext(level, city.centerX, city.centerZ);
        }

        if (context == null) {
            LOGGER.error("Error: [PROXIMITY] No context available for city '{}'", city.cityId);
            return false;
        }

        return CityBuildScheduler.startCityGeneration(city, level, context, playerX, playerZ);
    }

    public static void reset() {
        ACTIVE_CITIES.clear();
        tickCounter = 0;
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [PROXIMITY] Tracker reset");
        }
    }
}