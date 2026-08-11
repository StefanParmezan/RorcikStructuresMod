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
import ru.rorcik.structures.generation.GeneratorConfig;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StructureCache {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<ResourceLocation, byte[]> COMPRESSED_RAW_CACHE = Collections.emptyMap();
    private static HolderGetter<Block> blockGetter = null;

    private static final Map<ResourceLocation, StructureTemplate> COMPILED_LRU = Collections.synchronizedMap(
            new LinkedHashMap<>(GeneratorConfig.MAX_TEMPLATE_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, StructureTemplate> eldest) {
                    boolean shouldRemove = size() > GeneratorConfig.MAX_TEMPLATE_CACHE;
                    if (shouldRemove) {
                        LOGGER.debug("Evicting compiled template from cache: {}", eldest.getKey());
                    }
                    return shouldRemove;
                }
            }
    );

    public static void loadRawNbt(ResourceManager resourceManager) {
        Map<ResourceLocation, byte[]> tempCache = new HashMap<>();

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
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load raw structure: {}", location, e);
            }
        }

        COMPRESSED_RAW_CACHE = Collections.unmodifiableMap(tempCache);
        LOGGER.info("Loaded {} raw structure NBTs ({} KB total)",
                tempCache.size(),
                tempCache.values().stream().mapToLong(b -> b.length).sum() / 1024);
    }

    public static void compileTemplates(HolderLookup.Provider registryAccess) {
        blockGetter = registryAccess.lookupOrThrow(Registries.BLOCK);
        COMPILED_LRU.clear();

        if (GeneratorConfig.PRECOMPILE_ALL_TEMPLATES) {
            precompileAll();
        } else {
            LOGGER.info("StructureCache ready for lazy compilation ({} raw templates available)",
                    COMPRESSED_RAW_CACHE.size());
        }
    }

    /**
     * Предварительно компилирует ВСЕ шаблоны из raw cache.
     * Это делает загрузку сервера медленнее, но первую генерацию города — быстрее.
     */
    private static void precompileAll() {
        if (blockGetter == null) {
            LOGGER.error("Cannot precompile templates, blockGetter is null");
            return;
        }

        long startTime = System.currentTimeMillis();
        int compiled = 0;

        for (ResourceLocation location : COMPRESSED_RAW_CACHE.keySet()) {
            byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
            try {
                CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
                StructureTemplate template = new StructureTemplate();
                template.load(blockGetter, nbt);
                COMPILED_LRU.put(location, template);
                compiled++;
            } catch (Exception e) {
                LOGGER.error("Failed to precompile template: {}", location, e);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Precompiled {} templates in {} ms (cache size: {}/{})",
                compiled, elapsed, COMPILED_LRU.size(), GeneratorConfig.MAX_TEMPLATE_CACHE);
    }

    public static Optional<StructureTemplate> get(ResourceLocation location) {
        StructureTemplate cached = COMPILED_LRU.get(location);
        if (cached != null) {
            return Optional.of(cached);
        }

        byte[] rawBytes = COMPRESSED_RAW_CACHE.get(location);
        if (rawBytes == null) {
            return Optional.empty();
        }

        if (blockGetter == null) {
            LOGGER.error("Cannot compile template, blockGetter is null: {}", location);
            return Optional.empty();
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(rawBytes));
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);
            COMPILED_LRU.put(location, template);
            return Optional.of(template);
        } catch (Exception e) {
            LOGGER.error("Failed to compile template on-demand: {}", location, e);
            return Optional.empty();
        }
    }

    public static void precompile(ResourceLocation... locations) {
        for (ResourceLocation loc : locations) {
            get(loc);
        }
    }

    public static void clear() {
        COMPRESSED_RAW_CACHE = Collections.emptyMap();
        COMPILED_LRU.clear();
        blockGetter = null;
    }

    public static int rawSize() {
        return COMPRESSED_RAW_CACHE.size();
    }

    public static int compiledSize() {
        return COMPILED_LRU.size();
    }

    public static long rawMemoryEstimateKB() {
        return COMPRESSED_RAW_CACHE.values().stream().mapToLong(b -> b.length).sum() / 1024;
    }
}