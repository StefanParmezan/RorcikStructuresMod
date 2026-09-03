package ru.rorcik.structures.generation.city.plains.cities.buildings;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

public class ParkWorldTracker extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "rorcik_park_counts";

    private final Map<String, Integer> parkCounts = new HashMap<>();

    public static ParkWorldTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ParkWorldTracker::load,
                ParkWorldTracker::new,
                DATA_NAME
        );
    }

    public static ParkWorldTracker load(CompoundTag tag) {
        ParkWorldTracker tracker = new ParkWorldTracker();
        CompoundTag countsTag = tag.getCompound("counts");
        for (String key : countsTag.getAllKeys()) {
            tracker.parkCounts.put(key, countsTag.getInt(key));
        }
        LOGGER.info("[PARK-TRACKER] Loaded {} park counts from world data", tracker.parkCounts.size());
        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag countsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : parkCounts.entrySet()) {
            countsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("counts", countsTag);
        return tag;
    }

    public int getCount(String parkName) {
        return parkCounts.getOrDefault(parkName, 0);
    }

    public void increment(String parkName) {
        int newCount = parkCounts.getOrDefault(parkName, 0) + 1;
        parkCounts.put(parkName, newCount);
        setDirty();
        LOGGER.info("[PARK-TRACKER] Park '{}' spawned {} times in this world",
                parkName, newCount);
    }

    public boolean canSpawn(String parkName, int limit) {
        if (limit < 0) return true;
        return getCount(parkName) < limit;
    }

    public void reset() {
        parkCounts.clear();
        setDirty();
        LOGGER.info("[PARK-TRACKER] All park counts reset");
    }

    public Map<String, Integer> getAllCounts() {
        return new HashMap<>(parkCounts);
    }

    public static void resetForLevel(ServerLevel level) {
        ParkWorldTracker tracker = get(level);
        tracker.reset();
    }
}