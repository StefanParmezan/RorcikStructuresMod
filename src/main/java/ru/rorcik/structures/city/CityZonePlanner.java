package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.event.ZoneEvents;
import ru.rorcik.core.zone.BiomeZoneRegistry;
import ru.rorcik.core.zone.TemperatureZone;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.city.CityType;

import java.util.Optional;

public class CityZonePlanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HEIGHT_TOLERANCE = 12; 

    public static boolean tryReserveCity(ServerLevel level, int centerX, int centerZ, CityType type) {
        BlockPos checkPos = new BlockPos(centerX, level.getSeaLevel(), centerZ);
        Holder<Biome> biomeHolder = level.getBiome(checkPos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        if (biomeId == null) {
            LOGGER.debug("[CITY-PLANNER] Не удалось получить ID биома на [{}, {}]", centerX, centerZ);
            return false;
        }

        TemperatureZone currentZone = getBiomeTemperatureZone(biomeId, biomeHolder.value());

        if (currentZone == null) {
            LOGGER.debug("[CITY-PLANNER] Не удалось определить температурную зону для {}", biomeId);
            return false;
        }

        if (!type.allowedZones.contains(currentZone)) {
            LOGGER.debug("[CITY-PLANNER] Биом {} (зона {}) не подходит для типа города {}", biomeId, currentZone, type.id);
            return false;
        }

        if (type.targetHeight != -1) {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
            if (Math.abs(surfaceY - type.targetHeight) > HEIGHT_TOLERANCE) {
                LOGGER.debug("[CITY-PLANNER] Высота поверхности ({}) слишком сильно отличается от целевой ({}) на [{}, {}]",
                        surfaceY, type.targetHeight, centerX, centerZ);
                return false;
            }
        }

        ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);
        boolean reserved = registry.tryReserveZone(centerX, centerZ, type.toZoneRules());

        if (reserved) {
            LOGGER.info("[CITY-PLANNER] ✅ Успешно зарезервирована зона для {} города на [{}, {}] (Высота: ~{})",
                    type.id, centerX, centerZ, type.targetHeight == -1 ? "Любая" : type.targetHeight);

            MinecraftForge.EVENT_BUS.post(new ZoneEvents.ZoneReservedEvent(
                    centerX >> 4,
                    centerZ >> 4,
                    centerX,
                    centerZ,
                    type.toZoneRules(),
                    type.targetHeight 
            ));
            return true;
        } else {
            LOGGER.debug("[CITY-PLANNER] Зона на [{}, {}] занята или находится слишком близко к другой", centerX, centerZ);
            return false;
        }
    }

    private static TemperatureZone getBiomeTemperatureZone(ResourceLocation biomeId, Biome biome) {
        
        Optional<TemperatureZone> coreZone = BiomeZoneRegistry.getZone(biomeId);
        if (coreZone.isPresent()) {
            return coreZone.get();
        }

        float temp = biome.getBaseTemperature();
        if (temp < 0.15f) return TemperatureZone.COLD;
        if (temp < 0.45f) return TemperatureZone.COOL;
        if (temp < 0.75f) return TemperatureZone.NEUTRAL;
        if (temp < 0.95f) return TemperatureZone.WARM;
        return TemperatureZone.HOT;
    }
}