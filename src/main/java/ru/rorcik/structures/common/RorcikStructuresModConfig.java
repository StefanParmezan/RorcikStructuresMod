package ru.rorcik.structures.common;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RorcikStructuresModConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE = BUILDER
            .define("debugMode", false);

    public static final ForgeConfigSpec.BooleanValue USE_CACHING = BUILDER
            .define("use_caching", true);

    public static final ForgeConfigSpec.IntValue BUILDING_CACHE_SIZE = BUILDER
            .comment(
                    "Max compiled BUILDING templates in memory (houses, lootable, other, parks, platforms, roads).",
                    "-1 = auto-detect based on available RAM.",
                    ">0 = manual override."
            )
            .defineInRange("buildingCacheSize", -1, -1, 1024);

    public static final ForgeConfigSpec.IntValue ROOM_CACHE_SIZE = BUILDER
            .comment(
                    "Max compiled ROOM templates in memory (small templates, many of them).",
                    "-1 = auto-detect based on available RAM.",
                    ">0 = manual override."
            )
            .defineInRange("roomCacheSize", -1, -1, 4096);

    public static final ForgeConfigSpec.BooleanValue UNLOAD_AFTER_GENERATION = BUILDER
            .comment("Unload compiled templates from RAM after city generation completes.")
            .define("unloadAfterGeneration", true);

    public static final ForgeConfigSpec.BooleanValue LOG_CACHE_MISSES = BUILDER
            .comment("Log warnings when template is not found in cache.")
            .define("logCacheMisses", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean debugMode;
    public static boolean useCaching;
    public static int buildingCacheSize;
    public static int roomCacheSize;
    public static boolean unloadAfterGeneration;
    public static boolean logCacheMisses;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        debugMode = DEBUG_MODE.get();
        useCaching = USE_CACHING.get();
        buildingCacheSize = BUILDING_CACHE_SIZE.get();
        roomCacheSize = ROOM_CACHE_SIZE.get();
        unloadAfterGeneration = UNLOAD_AFTER_GENERATION.get();
        logCacheMisses = LOG_CACHE_MISSES.get();
    }
}