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
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StructureCache {
    public enum Category {
        ROOMS, HOUSES, ROADS, BUILDINGS, PARKS, SIDEWALKS, ROAD_DECOR, UNKNOWN
    }

    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    private static final ReentrantReadWriteLock RW_LOCK = new ReentrantReadWriteLock();

    private static final Map<ResourceLocation, SoftReference<byte[]>> COMPRESSED_RAW_CACHE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> KNOWN_KEYS = ConcurrentHashMap.newKeySet();
    private static volatile ResourceManager resourceManager = null;
    private static HolderGetter<Block> blockGetter = null;
    private static final Map<Category, Map<ResourceLocation, StructureTemplate>> CATEGORY_CACHES = new EnumMap<>(Category.class);
    private static final Map<Category, Integer> CATEGORY_CAPACITIES = new EnumMap<>(Category.class);
    private static boolean initialized = false;
    private static volatile boolean readOnly = false;

    private static long buildingTotalBytes = 0;
    private static int buildingFileCount = 0;
    private static long roomTotalBytes = 0;
    private static int roomFileCount = 0;

    private static final LongAdder cacheHits = new LongAdder();
    private static final LongAdder cacheMisses = new LongAdder();
    private static final LongAdder notFoundCount = new LongAdder();
    private static final LongAdder softEvictions = new LongAdder();
    private static final LongAdder diskReloads = new LongAdder();

    public static void loadRawNbt(ResourceManager manager) {
        resourceManager = manager;
        COMPRESSED_RAW_CACHE.clear();
        KNOWN_KEYS.clear();

        buildingTotalBytes = 0;
        buildingFileCount = 0;
        roomTotalBytes = 0;
        roomFileCount = 0;

        Map<ResourceLocation, Resource> allStructures = manager.listResources(
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
                if (cacheKey == null) continue;

                KNOWN_KEYS.add(cacheKey);
                COMPRESSED_RAW_CACHE.put(cacheKey, new SoftReference<>(rawBytes));

                if (path.contains("rooms/")) {
                    roomTotalBytes += rawBytes.length;
                    roomFileCount++;
                } else {
                    buildingTotalBytes += rawBytes.length;
                    buildingFileCount++;
                }
            } catch (Exception e) {
                LOGGER.error("[CACHE] Failed to load raw structure: {}", location, e);
            }
        }

        LOGGER.info("[CACHE] Indexed {} raw NBT keys ({} buildings, {} rooms). Bytes held via SoftReference.",
                KNOWN_KEYS.size(), buildingFileCount, roomFileCount);
    }

    public static void setReadOnly(boolean value) {
        readOnly = value;
    }

    public static void initializeCompiledCaches() {
        RW_LOCK.writeLock().lock();
        try {
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
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    public static void compileTemplates(HolderLookup.Provider registryAccess) {
        RW_LOCK.writeLock().lock();
        try {
            blockGetter = registryAccess.lookupOrThrow(Registries.BLOCK);
            initializeCompiledCaches();
            for (Map<ResourceLocation, StructureTemplate> cache : CATEGORY_CACHES.values()) {
                cache.clear();
            }
            resetStats();
        } finally {
            RW_LOCK.writeLock().unlock();
        }
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

        RW_LOCK.readLock().lock();
        try {
            StructureTemplate cached = targetCache.get(location);
            if (cached != null) {
                cacheHits.increment();
                return Optional.of(cached);
            }
        } finally {
            RW_LOCK.readLock().unlock();
        }

        RW_LOCK.writeLock().lock();
        try {
            StructureTemplate cached = targetCache.get(location);
            if (cached != null) {
                cacheHits.increment();
                return Optional.of(cached);
            }

            if (readOnly) {
                if (RorcikStructuresModConfig.logCacheMisses) {
                    LOGGER.warn("[CACHE] Miss in read-only mode: {} (Category: {})", location, cat);
                }
                return compileOnDemandInternal(location, cat, targetCache);
            }

            cacheMisses.increment();
            return compileOnDemandInternal(location, cat, targetCache);
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    public static void precompile(ResourceLocation location) {
        if (!initialized) initializeCompiledCaches();

        Category cat = getCategoryFromPath(location.getPath());
        Map<ResourceLocation, StructureTemplate> targetCache = CATEGORY_CACHES.get(cat);
        if (targetCache == null || targetCache.containsKey(location)) return;

        RW_LOCK.writeLock().lock();
        try {
            if (targetCache.containsKey(location)) return;

            byte[] rawBytes = getRawBytes(location);
            if (rawBytes == null) {
                notFoundCount.increment();
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
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    private static Optional<StructureTemplate> compileOnDemandInternal(ResourceLocation location, Category cat, Map<ResourceLocation, StructureTemplate> targetCache) {
        byte[] rawBytes = getRawBytes(location);
        if (rawBytes == null) {
            notFoundCount.increment();
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

    private static byte[] getRawBytes(ResourceLocation location) {
        SoftReference<byte[]> ref = COMPRESSED_RAW_CACHE.get(location);
        if (ref != null) {
            byte[] cached = ref.get();
            if (cached != null) return cached;
            softEvictions.increment();
        }

        ResourceManager rm = resourceManager;
        if (rm == null) return null;

        String path = location.getPath();
        if (!path.endsWith(".nbt")) {
            path = path + ".nbt";
        }
        ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), ModPaths.STRUCTURES_BASE + "/" + path);

        Optional<Resource> resource = rm.getResource(resourceLocation);
        if (resource.isEmpty()) return null;

        try (InputStream stream = resource.get().open()) {
            byte[] rawBytes = stream.readAllBytes();
            COMPRESSED_RAW_CACHE.put(location, new SoftReference<>(rawBytes));
            diskReloads.increment();
            return rawBytes;
        } catch (Exception e) {
            LOGGER.debug("[CACHE] Failed to reload from disk: {}", location, e);
            return null;
        }
    }

    public static void clearCategory(Category category) {
        RW_LOCK.writeLock().lock();
        try {
            Map<ResourceLocation, StructureTemplate> cache = CATEGORY_CACHES.get(category);
            if (cache != null && !cache.isEmpty()) {
                int size = cache.size();
                cache.clear();
                LOGGER.info("[CACHE] Cleared category {}: {} templates", category, size);
            }
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    public static void releaseRawCache() {
        RW_LOCK.writeLock().lock();
        try {
            if (!COMPRESSED_RAW_CACHE.isEmpty() || !KNOWN_KEYS.isEmpty()) {
                int size = COMPRESSED_RAW_CACHE.size();
                COMPRESSED_RAW_CACHE.clear();
                KNOWN_KEYS.clear();
                LOGGER.info("[CACHE] Released raw cache: {} entries", size);
            }
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    public static void clear() {
        RW_LOCK.writeLock().lock();
        try {
            for (Category cat : Category.values()) {
                Map<ResourceLocation, StructureTemplate> cache = CATEGORY_CACHES.get(cat);
                if (cache != null) {
                    cache.clear();
                }
            }
            COMPRESSED_RAW_CACHE.clear();
            KNOWN_KEYS.clear();
            resourceManager = null;
            blockGetter = null;
            initialized = false;
            readOnly = false;
            resetStats();
            LOGGER.info("[CACHE] Fully cleared and reset.");
        } finally {
            RW_LOCK.writeLock().unlock();
        }
    }

    public static void logStats() {
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
        LOGGER.info("[CACHE-STATS] Total: {} hits / {} misses ({}% hit rate)", hits, misses, String.format("%.1f", hitRate));
        long nf = notFoundCount.sum();
        if (nf > 0) {
            LOGGER.warn("[CACHE-STATS] {} templates NOT FOUND", nf);
        }
        long evictions = softEvictions.sum();
        long reloads = diskReloads.sum();
        if (evictions > 0 || reloads > 0) {
            LOGGER.info("[CACHE-STATS] SoftReference evictions: {}, disk reloads: {}", evictions, reloads);
        }
    }

    public static void resetStats() {
        cacheHits.reset();
        cacheMisses.reset();
        notFoundCount.reset();
        softEvictions.reset();
        diskReloads.reset();
    }
}