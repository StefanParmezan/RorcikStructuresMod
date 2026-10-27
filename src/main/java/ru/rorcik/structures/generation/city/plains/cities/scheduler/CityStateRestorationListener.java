package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.planning.CityZonePlanner;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityStateRestorationListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        ZoneRegistrySavedData zoneRegistry = ZoneRegistrySavedData.get(level);
        CityGenerationSavedData genData = CityGenerationSavedData.get(level);

        int restored = 0;
        int completed = 0;
        String worldId = CityJournal.getWorldId(level);

        for (ZoneRegistrySavedData.ReservedZone zone : zoneRegistry.getAllReservedZones()) {
            CityGenerationSavedData.CityGenState state = genData.getState(zone.x(), zone.z());

            if (state == null) {
                genData.registerCity(zone.x(), zone.z(), 64);
                state = genData.getState(zone.x(), zone.z());
            }

            if (state.isComplete()) {
                completed++;
                continue;
            }

            String typeId = zone.rules().id();
            CityGenerationContext context = CityGenerationContextRegistry.get(worldId, zone.x(), zone.z());

            if (context == null) {
                context = CityZonePlanner.rebuildContext(level, zone.x(), zone.z(), typeId);
                if (context != null) {
                    restored++;
                }
            }

            Set<Long> affectedChunks = calculateAffectedChunks(context);
            PlayerProximityTracker.registerCity(worldId, zone.x(), zone.z(), typeId, affectedChunks);
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [RESTORE] Restored {} incomplete cities, {} already complete", restored, completed);
        }
    }

    private static Set<Long> calculateAffectedChunks(CityGenerationContext context) {
        if (context == null) return Set.of();
        Set<Long> chunks = new java.util.HashSet<>();
        for (net.minecraft.core.BlockPos pos : context.nodeMap().keySet()) {
            addStructureChunks(chunks, pos, 4); 
        }
        for (net.minecraft.core.BlockPos pos : context.straightPositions()) {
            addStructureChunks(chunks, pos, 4);
        }
        for (ru.rorcik.structures.data.city.plains.containers.BuildingSlot slot : context.slots()) {
            if (!slot.category().equals("platforms")) {
                addStructureChunks(chunks, slot.position(), context.anchorChunkSize()); 
            }
        }
        return chunks;
    }

    private static void addStructureChunks(Set<Long> chunks, net.minecraft.core.BlockPos pos, int size) {
        int baseChunkX = pos.getX() >> 4;
        int baseChunkZ = pos.getZ() >> 4;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                long key = ((long)(baseChunkX + dx) << 32) | ((baseChunkZ + dz) & 0xFFFFFFFFL);
                chunks.add(key);
            }
        }
    }
}