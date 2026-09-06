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

    public enum Category {
        ROOMS, HOUSES, ROADS, BUILDINGS, PARKS, SIDEWALKS, ROAD_DECOR, UNKNOWN
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DECOMPRESSION_FACTOR = GeneratorConfig.DEFAULT_DECOMPRESSION_FACTOR;
    private static Map<ResourceLocation, byte[]> COMPRESSED_RAW_CACHE = Collections.emptyMap();
    private static HolderGetter<Block> blockGetter = null;
    private static final Map<Category, Map<ResourceLocation, StructureTemplate>> CATEGORY_CACHES = new EnumMap<>(Category.class);
    private static final Map<Category, Integer> CATEGORY_CAPACITIES = new EnumMap<>(Category.class);
    private static boolean initialized = false;
    private static volatile boolean readOnly = false;
    private static long buildingTotalBytes = 0;
    private static int buildingFileCount = 0;
    private static long roomTotalBytes = 0;
    private static int roomFileCount = 0;
    private static final AtomicLong cacheHits = new AtomicLong();
    private static final AtomicLong cacheMisses = new AtomicLong();
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
                    if (path.contains("rooms/")) {
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
        LOGGER.info("[CACHE] Loaded {} raw NBTs", tempCache.size());
    }

    public static void setReadOnly(boolean value) {
        readOnly = value;
    }

    public static synchronized void initializeCompiledCaches() {
        if (initialized) return;

        int configuredBuildingSize = RorcikStructuresModConfig.buildingCacheSize > 0 ? RorcikStructuresModConfig.buildingCacheSize : 512;
        int configuredRoomSize = RorcikStructuresModConfig.roomCacheSize > 0 ? RorcikStructuresModConfig.roomCacheSize : 4096;

        for (Category cat : Category.values()) {
            int capacity = (cat == Category.ROOMS) ? configuredRoomSize : configuredBuildingSize;
            CATEGORY_CAPACITIES.put(cat, capacity);
            CATEGORY_CACHES.put(cat, Collections.synchronizedMap(new LinkedHashMap<ResourceLocation, StructureTemplate>(capacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, StructureTemplate> eldest) {
                    return size() > capacity;
                }
            }));
        }
        initialized = true;
    }

    public static void compileTemplates(HolderLookup.Provider registryAccess) {
        blockGetter = registryAccess.lookupOrThrow(Registries.BLOCK);
        initializeCompiledCaches();
        for (Map<ResourceLocation, StructureTemplate> cache : CATEGORY_CACHES.values()) {
            cache.clear();
        }
        resetStats();
    }

    public static Category getCategoryFromPath(String path) {
        if (path.contains("rooms/")) return Category.ROOMS;
        if (path.contains("houses/")) return Category.HOUSES;
        if (path.contains("roads/") && !path.contains("road_decor/")) return Category.ROADS;
        if (path.contains("road_decor/")) return Category.ROAD_DECOR;
        if (path.contains("sidewalks/")) return Category.SIDEWALKS;
        if (path.contains("parks/")) return Category.PARKS;
        return Category.BUILDINGS;
    }

    public static Optional<StructureTemplate> get(ResourceLocation location) {
        if (!initialized) initializeCompiledCaches();

        Category cat = getCategoryFromPath(location.getPath());
        Map<ResourceLocation, StructureTemplate> targetCache = CATEGORY_CACHES.get(cat);

        if (targetCache == null) return Optional.empty();

        StructureTemplate cached = targetCache.get(location);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return Optional.of(cached);
        }

        if (readOnly) {
            if (RorcikStructuresModConfig.logCacheMisses) {
                LOGGER.warn("[CACHE] Miss in read-only mode: {} (Category: {})", location, cat);
            }
            return compileOnDemand(location, cat, targetCache);
        }

        cacheMisses.incrementAndGet();
        return compileOnDemand(location, cat, targetCache);
    }

    public static void precompile(ResourceLocation location) {
        if (!initialized) initializeCompiledCaches();
        Category cat = getCategoryFromPath(location.getPath());
        Map<ResourceLocation, StructureTemplate> targetCache = CATEGORY_CACHES.get(cat);
        if (targetCache == null || targetCache.containsKey(location)) return;

        byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
        if (rawBytes == null) {
            notFoundCount.incrementAndGet();
            return;
        }
        if (blockGetter == null) return;

        try {
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);
            targetCache.put(location, template);
        } catch (Exception e) {
            LOGGER.error("[CACHE] Failed to precompile: {}", location, e);
        }
    }

    private static Optional<StructureTemplate> compileOnDemand(ResourceLocation location, Category cat, Map<ResourceLocation, StructureTemplate> targetCache) {
        byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
        if (rawBytes == null) {
            notFoundCount.incrementAndGet();
            return Optional.empty();
        }
        if (blockGetter == null) return Optional.empty();

        try {
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);
            targetCache.put(location, template);
            return Optional.of(template);
        } catch (Exception e) {
            LOGGER.error("[CACHE] Failed to compile on-demand: {}", location, e);
            return Optional.empty();
        }
    }

    public static synchronized void clearCategory(Category category) {
        Map<ResourceLocation, StructureTemplate> cache = CATEGORY_CACHES.get(category);
        if (cache != null && !cache.isEmpty()) {
            int size = cache.size();
            cache.clear();
            LOGGER.info("[CACHE] Cleared category {}: {} templates", category, size);
        }
    }

    public static synchronized void clear() {
        for (Category cat : Category.values()) {
            Map<ResourceLocation, StructureTemplate> cache = CATEGORY_CACHES.get(cat);
            if (cache != null) {
                cache.clear();
            }
        }

        COMPRESSED_RAW_CACHE = Collections.emptyMap();
        blockGetter = null;
        initialized = false;
        readOnly = false;

        resetStats();
        LOGGER.info("[CACHE] Fully cleared and reset.");
    }

    public static void logStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
        LOGGER.info("[CACHE-STATS] Total: {} hits / {} misses ({}% hit rate)", hits, misses, String.format("%.1f", hitRate));
        if (notFoundCount.get() > 0) {
            LOGGER.warn("[CACHE-STATS] {} templates NOT FOUND", notFoundCount.get());
        }
    }

    public static void resetStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
        notFoundCount.set(0);
    }
}