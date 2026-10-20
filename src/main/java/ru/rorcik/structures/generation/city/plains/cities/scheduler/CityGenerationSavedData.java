package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CityGenerationSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "rorcik_city_generation";
    private final Map<Long, CityGenState> cities = new ConcurrentHashMap<>();

    public record CityGenState(int targetHeight, boolean isComplete) {}

    public static CityGenerationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CityGenerationSavedData::load,
                CityGenerationSavedData::new,
                DATA_NAME
        );
    }

    public static CityGenerationSavedData load(CompoundTag tag) {
        CityGenerationSavedData data = new CityGenerationSavedData();
        ListTag list = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cityTag = list.getCompound(i);
            long key = cityTag.getLong("key");
            int targetHeight = cityTag.getInt("targetHeight");
            boolean isComplete = cityTag.getBoolean("isComplete");
            data.cities.put(key, new CityGenState(targetHeight, isComplete));
        }
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-STATE] Loaded {} city generation states from NBT", data.cities.size());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, CityGenState> entry : cities.entrySet()) {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putLong("key", entry.getKey());
            cityTag.putInt("targetHeight", entry.getValue().targetHeight());
            cityTag.putBoolean("isComplete", entry.getValue().isComplete());
            list.add(cityTag);
        }
        tag.put("cities", list);
        return tag;
    }

    public synchronized void registerCity(int x, int z, int targetHeight) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        if (!cities.containsKey(key)) {
            cities.put(key, new CityGenState(targetHeight, false));
            setDirty();
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [CITY-STATE] Registered new city at [{}, {}] with targetHeight {}", x, z, targetHeight);
            }
        }
    }

    public synchronized void markComplete(int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        CityGenState state = cities.get(key);
        if (state != null && !state.isComplete()) {
            cities.put(key, new CityGenState(state.targetHeight(), true));
            setDirty();
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [CITY-STATE] City at [{}, {}] marked as 100% complete", x, z);
            }
        }
    }

    public CityGenState getState(int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return cities.get(key);
    }

    public synchronized void clear() {
        int count = cities.size();
        cities.clear();
        setDirty();
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [CITY-STATE] Cleared {} city generation states", count);
        }
    }
}