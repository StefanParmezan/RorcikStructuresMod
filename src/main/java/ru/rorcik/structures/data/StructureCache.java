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
import ru.rorcik.structures.RorcikStructuresMod;
import ru.rorcik.structures.config.StructuresConfig;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StructureCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<ResourceLocation, CompoundTag> RAW_CACHE = Collections.emptyMap();
    private static Map<ResourceLocation, StructureTemplate> COMPILED_CACHE = Collections.emptyMap();

    public static void loadRawNbt(ResourceManager resourceManager) {
        Map<ResourceLocation, CompoundTag> tempCache = new HashMap<>();

        Map<ResourceLocation, Resource> allStructures = resourceManager.listResources(
                "structures",
                path -> path.getNamespace().equals(RorcikStructuresMod.MODID) && path.getPath().endsWith(".nbt")
        );

        for (Map.Entry<ResourceLocation, Resource> entry : allStructures.entrySet()) {
            ResourceLocation location = entry.getKey();
            try (InputStream stream = entry.getValue().open()) {
                CompoundTag nbt = NbtIo.readCompressed(stream);
                String path = location.getPath();
                if (path.startsWith("structures/")) {
                    path = path.substring("structures/".length());
                }
                if (path.endsWith(".nbt")) {
                    path = path.substring(0, path.length() - 4);
                }
                ResourceLocation cacheKey = ResourceLocation.tryParse(location.getNamespace() + ":" + path);
                if (cacheKey != null) {
                    tempCache.put(cacheKey, nbt);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load raw structure: {}", location, e);
            }
        }
        RAW_CACHE = Collections.unmodifiableMap(tempCache);
    }

    public static void compileTemplates(HolderLookup.Provider registryAccess) {
        HolderGetter<Block> blockGetter = registryAccess.lookupOrThrow(Registries.BLOCK);
        Map<ResourceLocation, StructureTemplate> tempCache = new HashMap<>();

        for (Map.Entry<ResourceLocation, CompoundTag> entry : RAW_CACHE.entrySet()) {
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, entry.getValue());
            tempCache.put(entry.getKey(), template);
        }
        COMPILED_CACHE = Collections.unmodifiableMap(tempCache);
    }

    public static Optional<StructureTemplate> get(ResourceLocation location) {
        if (StructuresConfig.useCaching) {
            boolean found = COMPILED_CACHE.containsKey(location);
        }
        return Optional.ofNullable(COMPILED_CACHE.get(location));
    }

    public static void clear() {
        RAW_CACHE = Collections.emptyMap();
        COMPILED_CACHE = Collections.emptyMap();
    }

    public static int size() {
        return COMPILED_CACHE.size();
    }
}