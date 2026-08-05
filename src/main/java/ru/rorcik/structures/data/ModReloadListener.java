package ru.rorcik.structures.data;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.city.plains.config_loaders.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModReloadListener {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StructureDataReloader());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        StructureCache.compileTemplates(event.getServer().overworld().registryAccess());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        StructureCache.clear();
        HouseConfigLoader.clear();
        RoadConfigLoader.clear();
        BuildingConfigLoader.clear();
    }

    private static class StructureDataReloader implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier preparationBarrier,
                ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler,
                ProfilerFiller reloadProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor
        ) {
            return CompletableFuture.runAsync(() -> {
                HouseConfigLoader.loadConfigs(resourceManager);
                RoadConfigLoader.loadConfigs(resourceManager);
                BuildingConfigLoader.loadConfigs(resourceManager);
                StructureCache.loadRawNbt(resourceManager);
            }, backgroundExecutor).thenCompose(preparationBarrier::wait);
        }
    }
}