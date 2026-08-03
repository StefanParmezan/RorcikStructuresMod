package ru.rorcik.structures.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import ru.rorcik.structures.RorcikStructuresMod;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StructuresConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE = BUILDER
            .define("debugMode", false);

    public static final ForgeConfigSpec.BooleanValue USE_CACHING = BUILDER
            .define("use_caching", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean debugMode;
    public static boolean useCaching;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        debugMode = DEBUG_MODE.get();
        useCaching = USE_CACHING.get();
    }
}