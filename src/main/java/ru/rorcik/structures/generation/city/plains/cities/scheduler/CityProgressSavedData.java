package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CityProgressSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "rorcik_city_progress";
    private static final int SAVE_INTERVAL = 10;
    private static final double COMPLETION_THRESHOLD = 0.9;

    private final Map<Long, CityProgress> cities = new ConcurrentHashMap<>();
    private int tasksSinceLastSave = 0;

    public static class CityProgress {
        public final int centerX;
        public final int centerZ;
        public final Set<Long> pendingTasks = ConcurrentHashMap.newKeySet();
        public final List<RoadSegmentData> roadSegments = new ArrayList<>();
        public int totalTasks;

        public CityProgress(int centerX, int centerZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        public double completionRatio() {
            if (totalTasks == 0) return 0;
            int completed = totalTasks - pendingTasks.size();
            return (double) completed / totalTasks;
        }
    }

    public record RoadSegmentData(String type, int x, int y, int z, int rotation, int decor) {}

    public static CityProgressSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CityProgressSavedData::load,
                CityProgressSavedData::new,
                DATA_NAME
        );
    }

    public static CityProgressSavedData load(CompoundTag tag) {
        CityProgressSavedData data = new CityProgressSavedData();

        ListTag citiesList = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < citiesList.size(); i++) {
            CompoundTag cityTag = citiesList.getCompound(i);
            int centerX = cityTag.getInt("centerX");
            int centerZ = cityTag.getInt("centerZ");
            int totalTasks = cityTag.getInt("totalTasks");

            CityProgress progress = new CityProgress(centerX, centerZ);
            progress.totalTasks = totalTasks;

            long[] pendingArray = cityTag.getLongArray("pendingTasks");
            for (long taskKey : pendingArray) {
                progress.pendingTasks.add(taskKey);
            }

            ListTag roadsList = cityTag.getList("roads", Tag.TAG_COMPOUND);
            for (int j = 0; j < roadsList.size(); j++) {
                CompoundTag roadTag = roadsList.getCompound(j);
                progress.roadSegments.add(new RoadSegmentData(
                        roadTag.getString("type"),
                        roadTag.getInt("x"),
                        roadTag.getInt("y"),
                        roadTag.getInt("z"),
                        roadTag.getInt("rotation"),
                        roadTag.getInt("decor")
                ));
            }

            long cityKey = cityKey(centerX, centerZ);
            data.cities.put(cityKey, progress);
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PROGRESS] Loaded progress for {} cities", data.cities.size());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag citiesList = new ListTag();

        for (CityProgress progress : cities.values()) {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putInt("centerX", progress.centerX);
            cityTag.putInt("centerZ", progress.centerZ);
            cityTag.putInt("totalTasks", progress.totalTasks);

            long[] pendingArray = new long[progress.pendingTasks.size()];
            int idx = 0;
            for (long taskKey : progress.pendingTasks) {
                pendingArray[idx++] = taskKey;
            }
            cityTag.putLongArray("pendingTasks", pendingArray);

            ListTag roadsList = new ListTag();
            for (RoadSegmentData seg : progress.roadSegments) {
                CompoundTag roadTag = new CompoundTag();
                roadTag.putString("type", seg.type());
                roadTag.putInt("x", seg.x());
                roadTag.putInt("y", seg.y());
                roadTag.putInt("z", seg.z());
                roadTag.putInt("rotation", seg.rotation());
                roadTag.putInt("decor", seg.decor());
                roadsList.add(roadTag);
            }
            cityTag.put("roads", roadsList);

            citiesList.add(cityTag);
        }

        tag.put("cities", citiesList);
        return tag;
    }

    public synchronized CityProgress startCity(int centerX, int centerZ, int totalTasks) {
        long key = cityKey(centerX, centerZ);
        CityProgress progress = new CityProgress(centerX, centerZ);
        progress.totalTasks = totalTasks;
        cities.put(key, progress);
        setDirty();
        scheduleSave();

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CITY-PROGRESS] Started tracking city [{}, {}] with {} tasks", centerX, centerZ, totalTasks);
        }
        return progress;
    }

    public synchronized void markTaskCompleted(int cityX, int cityZ, int anchorChunkX, int anchorChunkZ) {
        long cityKey = cityKey(cityX, cityZ);
        CityProgress progress = cities.get(cityKey);
        if (progress == null) return;

        long taskKey = taskKey(anchorChunkX, anchorChunkZ);
        progress.pendingTasks.remove(taskKey);

        if (progress.completionRatio() >= COMPLETION_THRESHOLD) {
            cities.remove(cityKey);
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Success: [CITY-PROGRESS] City [{}, {}] reached {}% completion, removed from progress tracking",
                        cityX, cityZ, (int)(progress.completionRatio() * 100));
            }
        }

        setDirty();
        scheduleSave();
    }

    public synchronized void addRoadSegment(int cityX, int cityZ, RoadSegmentData segment) {
        long cityKey = cityKey(cityX, cityZ);
        CityProgress progress = cities.get(cityKey);
        if (progress == null) return;

        progress.roadSegments.add(segment);
        setDirty();
    }

    public CityProgress getProgress(int cityX, int cityZ) {
        return cities.get(cityKey(cityX, cityZ));
    }

    public boolean hasProgress(int cityX, int cityZ) {
        return cities.containsKey(cityKey(cityX, cityZ));
    }

    public synchronized void clear() {
        int count = cities.size();
        cities.clear();
        tasksSinceLastSave = 0;
        setDirty();
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Cleanup: [CITY-PROGRESS] Cleared progress for {} cities", count);
        }
    }

    private void scheduleSave() {
        tasksSinceLastSave++;
        if (tasksSinceLastSave >= SAVE_INTERVAL) {
            tasksSinceLastSave = 0;
            try {
                ServerLevel level = ServerLifecycleHooks.getCurrentServer().overworld();
                if (level != null) {
                    level.getDataStorage().save();
                }
            } catch (Exception e) {
                LOGGER.error("Error: [CITY-PROGRESS] Failed to save progress", e);
            }
        }
    }

    private static long cityKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static long taskKey(int anchorChunkX, int anchorChunkZ) {
        return ((long) anchorChunkX << 32) | (anchorChunkZ & 0xFFFFFFFFL);
    }
}