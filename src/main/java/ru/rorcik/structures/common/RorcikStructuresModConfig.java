package ru.rorcik.structures.common;

import com.google.common.collect.Lists;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RorcikStructuresModConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOGGING;
    public static final ForgeConfigSpec.BooleanValue LOG_CACHE_MISSES;
    public static final ForgeConfigSpec.BooleanValue LOG_GENERATION_DETAILS;

    public static final ForgeConfigSpec.BooleanValue USE_CACHING;
    public static final ForgeConfigSpec.IntValue BUILDING_CACHE_SIZE;
    public static final ForgeConfigSpec.IntValue ROOM_CACHE_SIZE;
    public static final ForgeConfigSpec.BooleanValue UNLOAD_AFTER_GENERATION;
    public static final ForgeConfigSpec.IntValue BUILDING_MEMORY_BUDGET_PERCENT;
    public static final ForgeConfigSpec.IntValue ROOM_MEMORY_BUDGET_PERCENT;
    public static final ForgeConfigSpec.LongValue RESERVED_RAM_MB;

    public static final ForgeConfigSpec.IntValue INITIAL_STRUCTURES_PER_TICK;
    public static final ForgeConfigSpec.IntValue MIN_STRUCTURES_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_STRUCTURES_PER_TICK;
    public static final ForgeConfigSpec.LongValue TARGET_TICK_TIME_MS;
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue PRELOAD_PER_TICK_DURING_CHUNKS;
    public static final ForgeConfigSpec.IntValue PRELOAD_PER_TICK_BEFORE_BUILD;

    public static final ForgeConfigSpec.IntValue PLAINS_ROAD_STEP;
    public static final ForgeConfigSpec.IntValue PLAINS_BUILDING_DISTANCE;
    public static final ForgeConfigSpec.IntValue PLAINS_STRAIGHT_BETWEEN_NODES;
    public static final ForgeConfigSpec.IntValue PLAINS_MAX_GRAPH_DEPTH;
    public static final ForgeConfigSpec.IntValue PLAINS_MAX_NODES;
    public static final ForgeConfigSpec.IntValue PLAINS_BUILDING_MIN_DEPTH;
    public static final ForgeConfigSpec.IntValue PLAINS_BUILDING_MAX_DEPTH;
    public static final ForgeConfigSpec.IntValue PLAINS_HOUSES_CHANCE;
    public static final ForgeConfigSpec.IntValue PLAINS_OTHER_CHANCE;
    public static final ForgeConfigSpec.IntValue PLAINS_LOOTABLE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue PLAINS_SPAWN_PARKS;
    public static final ForgeConfigSpec.BooleanValue PLAINS_PARK_ONLY_RECT;
    public static final ForgeConfigSpec.IntValue PLAINS_CHUNK_LOAD_RADIUS;
    public static final ForgeConfigSpec.IntValue PLAINS_MIN_TERMINAL_DISTANCE;

    public static final ForgeConfigSpec.IntValue DESERT_BUILDING_COUNT;
    public static final ForgeConfigSpec.IntValue DESERT_STEP_MIN;
    public static final ForgeConfigSpec.IntValue DESERT_STEP_MAX;
    public static final ForgeConfigSpec.IntValue DESERT_MIN_DIRECTIONS;
    public static final ForgeConfigSpec.IntValue DESERT_MAX_DIRECTIONS;
    public static final ForgeConfigSpec.IntValue DESERT_Y_OFFSET;

    public static final ForgeConfigSpec.IntValue WORKER_POOL_SIZE;
    public static final ForgeConfigSpec.IntValue BUFFER_DISTANCE;
    public static final ForgeConfigSpec.IntValue ABANDON_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_TASKS;
    public static final ForgeConfigSpec.IntValue PROXIMITY_CHECK_INTERVAL_TICKS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ROAD_DECORATIONS;

    // >>> FIX: Координаты спавна городов из конфига
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAINS_CITY_COORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DESERT_CITY_COORDS;

    public static final ForgeConfigSpec SPEC;

    public static boolean debugMode;
    public static boolean enableLogging;
    public static boolean logCacheMisses;
    public static boolean logGenerationDetails;

    public static boolean useCaching;
    public static int buildingCacheSize;
    public static int roomCacheSize;
    public static boolean unloadAfterGeneration;
    public static int buildingMemoryBudgetPercent;
    public static int roomMemoryBudgetPercent;
    public static long reservedRamMb;

    public static int initialStructuresPerTick;
    public static int minStructuresPerTick;
    public static int maxStructuresPerTick;
    public static long targetTickTimeMs;
    public static int chunksPerTick;
    public static int preloadPerTickDuringChunks;
    public static int preloadPerTickBeforeBuild;

    public static int plainsRoadStep;
    public static int plainsBuildingDistance;
    public static int plainsStraightBetweenNodes;
    public static int plainsMaxGraphDepth;
    public static int plainsMaxNodes;
    public static int plainsBuildingMinDepth;
    public static int plainsBuildingMaxDepth;
    public static int plainsHousesChance;
    public static int plainsOtherChance;
    public static int plainsLootableChance;
    public static boolean plainsSpawnParks;
    public static boolean plainsParkOnlyRect;
    public static int plainsChunkLoadRadius;
    public static int plainsMinTerminalDistance;

    public static int desertBuildingCount;
    public static int desertStepMin;
    public static int desertStepMax;
    public static int desertMinDirections;
    public static int desertMaxDirections;
    public static int desertYOffset;

    public static int workerPoolSize;
    public static int bufferDistance;
    public static int abandonDistance;
    public static int maxPendingTasks;
    public static int proximityCheckIntervalTicks;

    public static boolean enableRoadDecorations;

    // >>> FIX: Публичные списки для использования в листенере
    public static List<String> plainsCityCoords;
    public static List<String> desertCityCoords;

    static {
        BUILDER.comment("General mod settings").push("general");
        DEBUG_MODE = BUILDER.comment("Enable debug mode (extra logging and checks)").define("debugMode", false);
        ENABLE_LOGGING = BUILDER.comment("Master switch for ALL mod logging. Set to false to silence the mod completely.").define("enableLogging", false);
        LOG_CACHE_MISSES = BUILDER.comment("Log warnings when a template is not found in cache").define("logCacheMisses", false);
        LOG_GENERATION_DETAILS = BUILDER.comment("Log detailed generation info (slots, phases, timings)").define("logGenerationDetails", false);
        BUILDER.pop();

        BUILDER.comment("Structure cache settings").push("cache");
        USE_CACHING = BUILDER.comment("Enable structure caching (HIGHLY recommended)").define("use_caching", true);
        BUILDING_CACHE_SIZE = BUILDER.comment("Max compiled BUILDING templates in memory. -1 = auto-detect, >0 = manual override").defineInRange("buildingCacheSize", -1, -1, 8192);
        ROOM_CACHE_SIZE = BUILDER.comment("Max compiled ROOM templates in memory. -1 = auto-detect, >0 = manual override").defineInRange("roomCacheSize", -1, -1, 32768);
        UNLOAD_AFTER_GENERATION = BUILDER.comment("Unload compiled templates from RAM after city generation completes.").define("unloadAfterGeneration", true);
        BUILDING_MEMORY_BUDGET_PERCENT = BUILDER.comment("Percent of available memory budget for building cache (auto mode)").defineInRange("buildingMemoryBudgetPercent", 70, 10, 90);
        ROOM_MEMORY_BUDGET_PERCENT = BUILDER.comment("Percent of available memory budget for room cache (auto mode)").defineInRange("roomMemoryBudgetPercent", 30, 10, 90);
        RESERVED_RAM_MB = BUILDER.comment("RAM to keep free (in MB) when auto-detecting cache sizes").defineInRange("reservedRamMb", 2048L, 512L, 16384L);
        BUILDER.pop();

        BUILDER.comment("Performance / scheduling settings").push("performance");
        INITIAL_STRUCTURES_PER_TICK = BUILDER.comment("Starting number of structures placed per tick").defineInRange("initialStructuresPerTick", 16, 1, 256);
        MIN_STRUCTURES_PER_TICK = BUILDER.comment("Minimum structures per tick (adaptive floor)").defineInRange("minStructuresPerTick", 2, 1, 64);
        MAX_STRUCTURES_PER_TICK = BUILDER.comment("Maximum structures per tick (adaptive ceiling)").defineInRange("maxStructuresPerTick", 64, 8, 512);
        TARGET_TICK_TIME_MS = BUILDER.comment("Target tick time in ms for adaptive scheduler").defineInRange("targetTickTimeMs", 30L, 10L, 100L);
        CHUNKS_PER_TICK = BUILDER.comment("How many chunks to force-load per tick during generation").defineInRange("chunksPerTick", 48, 4, 256);
        PRELOAD_PER_TICK_DURING_CHUNKS = BUILDER.comment("Templates to preload per tick while chunks are loading").defineInRange("preloadPerTickDuringChunks", 6, 1, 64);
        PRELOAD_PER_TICK_BEFORE_BUILD = BUILDER.comment("Templates to preload per tick before building phase").defineInRange("preloadPerTickBeforeBuild", 15, 1, 128);
        BUILDER.pop();

        BUILDER.comment("Plains city generation settings").push("plains_city");
        PLAINS_ROAD_STEP = BUILDER.comment("Distance between road nodes (in blocks).").defineInRange("roadStep", 64, 16, 256);
        PLAINS_BUILDING_DISTANCE = BUILDER.comment("Distance from road to building (in blocks)").defineInRange("buildingDistance", 64, 16, 256);
        PLAINS_STRAIGHT_BETWEEN_NODES = BUILDER.comment("Number of straight road segments between nodes").defineInRange("straightBetweenNodes", 2, 0, 10);
        PLAINS_MAX_GRAPH_DEPTH = BUILDER.comment("Maximum depth of road graph (affects city size)").defineInRange("maxGraphDepth", 5, 1, 20);
        PLAINS_MAX_NODES = BUILDER.comment("Maximum number of road nodes (caps city size)").defineInRange("maxNodes", 70, 10, 500);
        PLAINS_BUILDING_MIN_DEPTH = BUILDER.comment("Minimum building row depth from road").defineInRange("buildingMinDepth", 2, 1, 10);
        PLAINS_BUILDING_MAX_DEPTH = BUILDER.comment("Maximum building row depth from road").defineInRange("buildingMaxDepth", 2, 1, 10);
        PLAINS_HOUSES_CHANCE = BUILDER.comment("Chance (%) to spawn a house at a slot").defineInRange("housesChance", 90, 0, 100);
        PLAINS_OTHER_CHANCE = BUILDER.comment("Chance (%) to spawn 'other' category building").defineInRange("otherChance", 5, 0, 100);
        PLAINS_LOOTABLE_CHANCE = BUILDER.comment("Chance (%) to spawn lootable building").defineInRange("lootableChance", 3, 0, 100);
        PLAINS_SPAWN_PARKS = BUILDER.comment("Allow parks to spawn in the city").define("spawnParks", true);
        PLAINS_PARK_ONLY_RECT = BUILDER.comment("Only spawn rectangular (non-square) parks").define("parkOnlyRect", false);
        PLAINS_CHUNK_LOAD_RADIUS = BUILDER.comment("Radius (in chunks) around city to force-load").defineInRange("chunkLoadRadius", 4, 1, 16);
        PLAINS_MIN_TERMINAL_DISTANCE = BUILDER.comment("Minimum distance (in blocks) from city center before a road can terminate (st road).").defineInRange("minTerminalDistance", 400, 64, 2048);
        BUILDER.pop();

        BUILDER.comment("Desert city generation settings").push("desert_city");
        DESERT_BUILDING_COUNT = BUILDER.comment("Number of buildings to generate in a desert city").defineInRange("buildingCount", 150, 10, 1000);
        DESERT_STEP_MIN = BUILDER.comment("Minimum step size when scattering buildings").defineInRange("stepMin", 32, 8, 128);
        DESERT_STEP_MAX = BUILDER.comment("Maximum step size when scattering buildings").defineInRange("stepMax", 32, 8, 256);
        DESERT_MIN_DIRECTIONS = BUILDER.comment("Minimum number of random scatter directions per building").defineInRange("minDirections", 1, 1, 8);
        DESERT_MAX_DIRECTIONS = BUILDER.comment("Maximum number of random scatter directions per building").defineInRange("maxDirections", 4, 1, 16);
        DESERT_Y_OFFSET = BUILDER.comment("Y offset applied to desert buildings (negative = lower)").defineInRange("yOffset", -1, -32, 32);
        BUILDER.pop();

        BUILDER.comment("Road settings").push("roads");
        ENABLE_ROAD_DECORATIONS = BUILDER.comment("Enable road decorations (lampposts, etc.)").define("enableRoadDecorations", true);
        WORKER_POOL_SIZE = BUILDER.comment("Number of worker threads for parallel city generation. -1 = auto-detect").defineInRange("workerPoolSize", -1, -1, 32);
        BUFFER_DISTANCE = BUILDER.comment("Distance in blocks at which city generation starts").defineInRange("bufferDistance", 96, 32, 512);
        ABANDON_DISTANCE = BUILDER.comment("Distance in blocks at which city generation pauses").defineInRange("abandonDistance", 192, 64, 1024);
        MAX_PENDING_TASKS = BUILDER.comment("Maximum number of anchor chunk tasks in queue").defineInRange("maxPendingTasks", 512, 16, 4096);
        PROXIMITY_CHECK_INTERVAL_TICKS = BUILDER.comment("How often (in ticks) to check player position").defineInRange("proximityCheckIntervalTicks", 10, 1, 100);
        BUILDER.pop();

        // >>> FIX: Секция координат спавна городов
        BUILDER.comment("City Spawn Coordinates").push("spawn_coordinates");
        PLAINS_CITY_COORDS = BUILDER.comment(
                "List of coordinates for Plains cities in 'x,z' format.",
                "Example: ['11000,0', '-11000,0']"
        ).defineList("plains_city_coordinates", Arrays.asList("-11000,0", "11000,0", "0,11000", "0,-11000"), obj -> obj instanceof String);

        DESERT_CITY_COORDS = BUILDER.comment(
                "List of coordinates for Desert cities in 'x,z' format.",
                "Example: ['19000,0', '-19000,0']"
        ).defineList("desert_city_coordinates", Arrays.asList("-19000,0", "19000,0", "0,19000", "0,-19000"), obj -> obj instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        reload();
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        reload();
        LOGGER.info("[CONFIG] Config reloaded, applying new values...");
        GeneratorConfig.reload();
    }

    private static void reload() {
        debugMode = DEBUG_MODE.get();
        enableLogging = ENABLE_LOGGING.get();
        logCacheMisses = LOG_CACHE_MISSES.get();
        logGenerationDetails = LOG_GENERATION_DETAILS.get();

        useCaching = USE_CACHING.get();
        buildingCacheSize = BUILDING_CACHE_SIZE.get();
        roomCacheSize = ROOM_CACHE_SIZE.get();
        unloadAfterGeneration = UNLOAD_AFTER_GENERATION.get();
        buildingMemoryBudgetPercent = BUILDING_MEMORY_BUDGET_PERCENT.get();
        roomMemoryBudgetPercent = ROOM_MEMORY_BUDGET_PERCENT.get();
        reservedRamMb = RESERVED_RAM_MB.get();

        initialStructuresPerTick = INITIAL_STRUCTURES_PER_TICK.get();
        minStructuresPerTick = MIN_STRUCTURES_PER_TICK.get();
        maxStructuresPerTick = MAX_STRUCTURES_PER_TICK.get();
        targetTickTimeMs = TARGET_TICK_TIME_MS.get();
        chunksPerTick = CHUNKS_PER_TICK.get();
        preloadPerTickDuringChunks = PRELOAD_PER_TICK_DURING_CHUNKS.get();
        preloadPerTickBeforeBuild = PRELOAD_PER_TICK_BEFORE_BUILD.get();

        plainsRoadStep = PLAINS_ROAD_STEP.get();
        plainsBuildingDistance = PLAINS_BUILDING_DISTANCE.get();
        plainsStraightBetweenNodes = PLAINS_STRAIGHT_BETWEEN_NODES.get();
        plainsMaxGraphDepth = PLAINS_MAX_GRAPH_DEPTH.get();
        plainsMaxNodes = PLAINS_MAX_NODES.get();
        plainsBuildingMinDepth = PLAINS_BUILDING_MIN_DEPTH.get();
        plainsBuildingMaxDepth = PLAINS_BUILDING_MAX_DEPTH.get();
        plainsHousesChance = PLAINS_HOUSES_CHANCE.get();
        plainsOtherChance = PLAINS_OTHER_CHANCE.get();
        plainsLootableChance = PLAINS_LOOTABLE_CHANCE.get();
        plainsSpawnParks = PLAINS_SPAWN_PARKS.get();
        plainsParkOnlyRect = PLAINS_PARK_ONLY_RECT.get();
        plainsChunkLoadRadius = PLAINS_CHUNK_LOAD_RADIUS.get();
        plainsMinTerminalDistance = PLAINS_MIN_TERMINAL_DISTANCE.get();

        desertBuildingCount = DESERT_BUILDING_COUNT.get();
        desertStepMin = DESERT_STEP_MIN.get();
        desertStepMax = DESERT_STEP_MAX.get();
        desertMinDirections = DESERT_MIN_DIRECTIONS.get();
        desertMaxDirections = DESERT_MAX_DIRECTIONS.get();
        desertYOffset = DESERT_Y_OFFSET.get();

        enableRoadDecorations = ENABLE_ROAD_DECORATIONS.get();
        workerPoolSize = WORKER_POOL_SIZE.get();
        bufferDistance = BUFFER_DISTANCE.get();
        abandonDistance = ABANDON_DISTANCE.get();
        maxPendingTasks = MAX_PENDING_TASKS.get();
        proximityCheckIntervalTicks = PROXIMITY_CHECK_INTERVAL_TICKS.get();

        // >>> FIX: Чтение и очистка списков координат
        plainsCityCoords = new java.util.ArrayList<>();
        for (Object obj : PLAINS_CITY_COORDS.get()) {
            if (obj instanceof String) plainsCityCoords.add((String) obj);
        }

        desertCityCoords = new java.util.ArrayList<>();
        for (Object obj : DESERT_CITY_COORDS.get()) {
            if (obj instanceof String) desertCityCoords.add((String) obj);
        }
    }
}