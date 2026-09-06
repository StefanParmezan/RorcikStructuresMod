package ru.rorcik.structures.data;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class StructureCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOMS_PATH_MARKER = "rooms/";

    private static final double DECOMPRESSION_FACTOR = GeneratorConfig.DEFAULT_DECOMPRESSION_FACTOR;

    private static Map<ResourceLocation, byte[]> COMPRESSED_RAW_CACHE = Collections.emptyMap();
    private static HolderGetter<Block> blockGetter = null;
    private static Map<ResourceLocation, StructureTemplate> BUILDING_CACHE = null;
    private static Map<ResourceLocation, StructureTemplate> ROOM_CACHE = null;
    private static int buildingCapacity = 0;
    private static int roomCapacity = 0;
    private static boolean initialized = false;
    private static volatile boolean readOnly = false;

    private static long buildingTotalBytes = 0;
    private static int buildingFileCount = 0;
    private static long roomTotalBytes = 0;
    private static int roomFileCount = 0;

    private static final AtomicLong buildingHits = new AtomicLong();
    private static final AtomicLong buildingMisses = new AtomicLong();
    private static final AtomicLong roomHits = new AtomicLong();
    private static final AtomicLong roomMisses = new AtomicLong();
    private static final AtomicLong notFoundCount = new AtomicLong();

    public static void loadRawNbt(ResourceManager resourceManager) {
        Map<ResourceLocation, byte[]> tempCache = new HashMap<>();
        buildingTotalBytes = 0;
        buildingFileCount = 0;
        roomTotalBytes = 0;
        roomFileCount = 0;

        Map<ResourceLocation, Resource> allStructures = resourceManager.listResources(
                ModPaths.STRUCTURES_BASE,
                path -> path.getNamespace().equals(RorcikStructuresMod.MODID) && path.getPath().endsWith(".nbt")
        );

        for (Map.Entry<ResourceLocation, Resource> entry : allStructures.entrySet()) {
            ResourceLocation location = entry.getKey();
            try (InputStream stream = entry.getValue().open()) {
                byte[] rawBytes = stream.readAllBytes();
                String path = location.getPath();
                if (path.startsWith(ModPaths.STRUCTURES_BASE + "/")) {
                    path = path.substring((ModPaths.STRUCTURES_BASE + "/").length());
                }
                if (path.endsWith(".nbt")) {
                    path = path.substring(0, path.length() - 4);
                }
                ResourceLocation cacheKey = ResourceLocation.tryParse(location.getNamespace() + ":" + path);
                if (cacheKey != null) {
                    tempCache.put(cacheKey, rawBytes);
                    if (isRoomPath(path)) {
                        roomTotalBytes += rawBytes.length;
                        roomFileCount++;
                    } else {
                        buildingTotalBytes += rawBytes.length;
                        buildingFileCount++;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[CACHE] Failed to load raw structure: {}", location, e);
            }
        }

        COMPRESSED_RAW_CACHE = Collections.unmodifiableMap(tempCache);
        long buildingAvgKB = buildingFileCount > 0 ? (buildingTotalBytes / buildingFileCount) / 1024 : 0;
        long roomAvgKB = roomFileCount > 0 ? (roomTotalBytes / roomFileCount) / 1024 : 0;

        LOGGER.info("[CACHE] Loaded {} raw NBTs ({} KB total)",
                tempCache.size(),
                tempCache.values().stream().mapToLong(b -> b.length).sum() / 1024);
        LOGGER.info("[CACHE] Buildings: {} files, {} KB total, avg {} KB per file",
                buildingFileCount, buildingTotalBytes / 1024, buildingAvgKB);
        LOGGER.info("[CACHE] Rooms: {} files, {} KB total, avg {} KB per file",
                roomFileCount, roomTotalBytes / 1024, roomAvgKB);
    }

    private static boolean isRoomPath(String path) {
        return path.contains(ROOMS_PATH_MARKER);
    }

    public static void setReadOnly(boolean value) {
        readOnly = value;
        LOGGER.debug("[CACHE] Read-only mode: {}", value);
    }

    public static long getAverageBuildingBytesInMemory() {
        if (buildingFileCount == 0) return GeneratorConfig.FALLBACK_AVG_BUILDING_BYTES;
        long avgCompressed = buildingTotalBytes / buildingFileCount;
        return (long) (avgCompressed * DECOMPRESSION_FACTOR);
    }

    public static long getAverageRoomBytesInMemory() {
        if (roomFileCount == 0) return GeneratorConfig.FALLBACK_AVG_ROOM_BYTES;
        long avgCompressed = roomTotalBytes / roomFileCount;
        return (long) (avgCompressed * DECOMPRESSION_FACTOR);
    }

    public static int calculateAutoBuildingCacheSize() {
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        long availableMB = Math.max(0, maxMemoryMB - GeneratorConfig.RESERVED_RAM_MB);
        long budgetMB = availableMB * GeneratorConfig.BUILDING_MEMORY_BUDGET_PERCENT / 100;
        long budgetBytes = budgetMB * 1024L * 1024L;
        long avgTemplateBytes = getAverageBuildingBytesInMemory();
        if (avgTemplateBytes <= 0) return GeneratorConfig.MIN_BUILDING_CACHE;
        int cacheSize = (int) (budgetBytes / avgTemplateBytes);
        return Math.max(GeneratorConfig.MIN_BUILDING_CACHE,
                Math.min(GeneratorConfig.MAX_BUILDING_CACHE_LIMIT, cacheSize));
    }

    public static int calculateAutoRoomCacheSize() {
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        long availableMB = Math.max(0, maxMemoryMB - GeneratorConfig.RESERVED_RAM_MB);
        long budgetMB = availableMB * GeneratorConfig.ROOM_MEMORY_BUDGET_PERCENT / 100;
        long budgetBytes = budgetMB * 1024L * 1024L;
        long avgTemplateBytes = getAverageRoomBytesInMemory();
        if (avgTemplateBytes <= 0) return GeneratorConfig.MIN_ROOM_CACHE;
        int cacheSize = (int) (budgetBytes / avgTemplateBytes);
        return Math.max(GeneratorConfig.MIN_ROOM_CACHE,
                Math.min(GeneratorConfig.MAX_ROOM_CACHE_LIMIT, cacheSize));
    }

    public static synchronized void initializeCompiledCaches() {
        if (initialized) return;

        int configuredBuildingSize = RorcikStructuresModConfig.buildingCacheSize;
        int configuredRoomSize = RorcikStructuresModConfig.roomCacheSize;
        int autoBuildingSize = calculateAutoBuildingCacheSize();
        int autoRoomSize = calculateAutoRoomCacheSize();
        long avgBuildingKB = getAverageBuildingBytesInMemory() / 1024L;
        long avgRoomKB = getAverageRoomBytesInMemory() / 1024L;

        if (configuredBuildingSize > 0) {
            buildingCapacity = configuredBuildingSize;
            LOGGER.info("[CACHE] Building cache: {} templates (manual, auto would be {}), avg ~{} KB",
                    configuredBuildingSize, autoBuildingSize, avgBuildingKB);
        } else {
            buildingCapacity = autoBuildingSize;
            LOGGER.info("[CACHE] Building cache: {} templates (auto-detected), avg ~{} KB",
                    autoBuildingSize, avgBuildingKB);
        }

        if (configuredRoomSize > 0) {
            roomCapacity = configuredRoomSize;
            LOGGER.info("[CACHE] Room cache: {} templates (manual, auto would be {}), avg ~{} KB",
                    configuredRoomSize, autoRoomSize, avgRoomKB);
        } else {
            roomCapacity = autoRoomSize;
            LOGGER.info("[CACHE] Room cache: {} templates (auto-detected), avg ~{} KB",
                    autoRoomSize, avgRoomKB);
        }

        BUILDING_CACHE = Collections.synchronizedMap(
                new LinkedHashMap<>(buildingCapacity, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<ResourceLocation, StructureTemplate> eldest) {
                        boolean shouldRemove = size() > buildingCapacity;
                        if (shouldRemove) {
                            LOGGER.debug("[CACHE-BUILDING] Evicting: {}", eldest.getKey());
                        }
                        return shouldRemove;
                    }
                }
        );

        ROOM_CACHE = Collections.synchronizedMap(
                new LinkedHashMap<>(roomCapacity, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<ResourceLocation, StructureTemplate> eldest) {
                        boolean shouldRemove = size() > roomCapacity;
                        if (shouldRemove) {
                            LOGGER.debug("[CACHE-ROOM] Evicting: {}", eldest.getKey());
                        }
                        return shouldRemove;
                    }
                }
        );

        initialized = true;
    }

    public static void compileTemplates(HolderLookup.Provider registryAccess) {
        blockGetter = registryAccess.lookupOrThrow(Registries.BLOCK);
        initializeCompiledCaches();
        if (BUILDING_CACHE != null) BUILDING_CACHE.clear();
        if (ROOM_CACHE != null) ROOM_CACHE.clear();
        resetStats();
        LOGGER.info("[CACHE] StructureCache ready ({} raw templates, building: {}, room: {})",
                COMPRESSED_RAW_CACHE.size(), buildingCapacity, roomCapacity);
    }

    public static Optional<StructureTemplate> get(ResourceLocation location) {
        if (!initialized) {
            initializeCompiledCaches();
        }
        boolean isRoom = isRoomPath(location.getPath());
        Map<ResourceLocation, StructureTemplate> targetCache = isRoom ? ROOM_CACHE : BUILDING_CACHE;

        if (targetCache == null) {
            LOGGER.error("[CACHE] Cache not initialized for: {}", location);
            return Optional.empty();
        }

        StructureTemplate cached = targetCache.get(location);
        if (cached != null) {
            if (isRoom) roomHits.incrementAndGet();
            else buildingHits.incrementAndGet();
            return Optional.of(cached);
        }

        if (readOnly) {
            if (RorcikStructuresModConfig.logCacheMisses) {
                LOGGER.warn("[CACHE] MISS in READ-ONLY mode: {} (Preloader failed to load this! Skipping placement.)", location);
            }
            return Optional.empty();
        }

        if (isRoom) roomMisses.incrementAndGet();
        else buildingMisses.incrementAndGet();
        return compileOnDemand(location, isRoom, targetCache);
    }

    public static void precompile(ResourceLocation location) {
        if (!initialized) {
            initializeCompiledCaches();
        }

        boolean isRoom = isRoomPath(location.getPath());
        Map<ResourceLocation, StructureTemplate> targetCache = isRoom ? ROOM_CACHE : BUILDING_CACHE;

        if (targetCache == null) return;

        if (targetCache.containsKey(location)) return;

        byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
        if (rawBytes == null) {
            notFoundCount.incrementAndGet();
            return;
        }

        if (blockGetter == null) {
            LOGGER.error("[CACHE] Cannot precompile, blockGetter is null: {}", location);
            return;
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);

            targetCache.put(location, template);
        } catch (Exception e) {
            LOGGER.error("[CACHE] Failed to precompile template in background: {}", location, e);
        }
    }

    private static Optional<StructureTemplate> compileOnDemand(
            ResourceLocation location, boolean isRoom,
            Map<ResourceLocation, StructureTemplate> targetCache
    ) {
        byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
        if (rawBytes == null) {
            notFoundCount.incrementAndGet();
            if (RorcikStructuresModConfig.logCacheMisses) {
                LOGGER.warn("[CACHE] Structure NOT FOUND in raw cache: {} (type: {})",
                        location, isRoom ? "ROOM" : "BUILDING");
            }
            return Optional.empty();
        }

        if (blockGetter == null) {
            LOGGER.error("[CACHE] Cannot compile template, blockGetter is null: {}", location);
            return Optional.empty();
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);
            targetCache.put(location, template);
            return Optional.of(template);
        } catch (Exception e) {
            LOGGER.error("[CACHE] Failed to compile template on-demand: {}", location, e);
            return Optional.empty();
        }
    }

    public static synchronized void unloadCompiledTemplates() {
        int buildings = 0;
        int rooms = 0;

        if (BUILDING_CACHE != null && !BUILDING_CACHE.isEmpty()) {
            buildings = BUILDING_CACHE.size();
            BUILDING_CACHE.clear();
        }
        if (ROOM_CACHE != null && !ROOM_CACHE.isEmpty()) {
            rooms = ROOM_CACHE.size();
            ROOM_CACHE.clear();
        }

        if (buildings > 0 || rooms > 0) {
            LOGGER.info("[CACHE] Unloaded {} building + {} room templates from RAM", buildings, rooms);
            logStats();
            resetStats();
        }
    }

    public static void logStats() {
        long bHits = buildingHits.get();
        long bMisses = buildingMisses.get();
        long rHits = roomHits.get();
        long rMisses = roomMisses.get();
        long notFound = notFoundCount.get();

        double bHitRate = (bHits + bMisses) > 0 ? (double) bHits / (bHits + bMisses) * 100 : 0;
        double rHitRate = (rHits + rMisses) > 0 ? (double) rHits / (rHits + rMisses) * 100 : 0;

        LOGGER.info("[CACHE-STATS] Buildings: {} hits / {} misses ({}% hit rate) | size: {}/{}",
                bHits, bMisses, String.format("%.1f", bHitRate),
                BUILDING_CACHE == null ? 0 : BUILDING_CACHE.size(), buildingCapacity);
        LOGGER.info("[CACHE-STATS] Rooms: {} hits / {} misses ({}% hit rate) | size: {}/{}",
                rHits, rMisses, String.format("%.1f", rHitRate),
                ROOM_CACHE == null ? 0 : ROOM_CACHE.size(), roomCapacity);
        LOGGER.info("[CACHE-STATS] Decompression factor: {} (static)",
                String.format("%.2f", DECOMPRESSION_FACTOR));

        if (notFound > 0) {
            LOGGER.warn("[CACHE-STATS] {} templates were NOT FOUND in raw cache!", notFound);
        }
    }

    public static void resetStats() {
        buildingHits.set(0);
        buildingMisses.set(0);
        roomHits.set(0);
        roomMisses.set(0);
        notFoundCount.set(0);
    }

    public static void clear() {
        COMPRESSED_RAW_CACHE = Collections.emptyMap();
        if (BUILDING_CACHE != null) { BUILDING_CACHE.clear(); BUILDING_CACHE = null; }
        if (ROOM_CACHE != null) { ROOM_CACHE.clear(); ROOM_CACHE = null; }
        blockGetter = null;
        buildingCapacity = 0;
        roomCapacity = 0;
        initialized = false;
        buildingTotalBytes = 0;
        buildingFileCount = 0;
        roomTotalBytes = 0;
        roomFileCount = 0;
        resetStats();
    }

    public static int rawSize() { return COMPRESSED_RAW_CACHE.size(); }
    public static int buildingCacheSize() { return BUILDING_CACHE == null ? 0 : BUILDING_CACHE.size(); }
    public static int roomCacheSize() { return ROOM_CACHE == null ? 0 : ROOM_CACHE.size(); }
    public static int buildingCapacity() { return buildingCapacity; }
    public static int roomCapacity() { return roomCapacity; }
    public static long rawMemoryEstimateKB() {
        return COMPRESSED_RAW_CACHE.values().stream().mapToLong(b -> b.length).sum() / 1024;
    }
    public static Set<ResourceLocation> getRawCacheKeys() { return COMPRESSED_RAW_CACHE.keySet(); }
}