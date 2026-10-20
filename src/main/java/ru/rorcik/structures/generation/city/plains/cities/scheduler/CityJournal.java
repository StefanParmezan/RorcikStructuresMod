package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CityJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JOURNALS_SUBDIR = "rorcik_journals";

    private final int centerX;
    private final int centerZ;
    private final String worldId;
    private final Path journalPath;
    private final Map<String, TaskProgress> tasks = new ConcurrentHashMap<>();
    private final Set<String> roads = ConcurrentHashMap.newKeySet();
    private int totalTasks;

    public static class TaskProgress {
        public String status;
        public String lastPhase;

        public TaskProgress(String status, String lastPhase) {
            this.status = status;
            this.lastPhase = lastPhase;
        }
    }

    public static class RoadData {
        public int x;
        public int y;
        public int z;
        public String type;
        public int rotation;
        public int decor;

        public RoadData(int x, int y, int z, String type, int rotation, int decor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.rotation = rotation;
            this.decor = decor;
        }
    }

    public static class JournalData {
        public int centerX;
        public int centerZ;
        public int totalTasks;
        public Map<String, TaskProgress> tasks;
        public List<RoadData> roads;

        public JournalData(int centerX, int centerZ, int totalTasks, Map<String, TaskProgress> tasks, List<RoadData> roads) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.totalTasks = totalTasks;
            this.tasks = tasks;
            this.roads = roads;
        }
    }

    public CityJournal(int centerX, int centerZ, String worldId, Path journalPath) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.worldId = worldId;
        this.journalPath = journalPath;
        this.totalTasks = 0;
    }

    public static String getWorldId(ServerLevel level) {
        Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }

    public static Path getJournalsDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(JOURNALS_SUBDIR);
    }

    public static CityJournal load(int centerX, int centerZ, ServerLevel level) {
        String worldId = getWorldId(level);
        Path journalsDir = getJournalsDir(level);
        Path journalPath = journalsDir.resolve(String.format("city_%s_plains@%d_%d.json", worldId, centerX, centerZ));

        CityJournal journal = new CityJournal(centerX, centerZ, worldId, journalPath);

        if (!Files.exists(journalPath)) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] No journal found for city [{}, {}] in world {}, creating new", centerX, centerZ, worldId);
            }
            return journal;
        }

        try {
            String json = Files.readString(journalPath);
            Type type = new TypeToken<JournalData>() {}.getType();
            JournalData data = GSON.fromJson(json, type);

            journal.totalTasks = data.totalTasks;
            if (data.tasks != null) {
                journal.tasks.putAll(data.tasks);
            }
            if (data.roads != null) {
                for (RoadData road : data.roads) {
                    journal.roads.add(roadKey(road.x, road.y, road.z));
                }
            }

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Loaded journal for city [{}, {}] in world {}: {} tasks, {} roads",
                        centerX, centerZ, worldId, journal.tasks.size(), journal.roads.size());
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to load journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }

        return journal;
    }

    public void initialize(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    public void markTaskCompleted(int anchorChunkX, int anchorChunkZ, String lastPhase) {
        String taskKey = anchorChunkX + "," + anchorChunkZ;
        tasks.put(taskKey, new TaskProgress("COMPLETED", lastPhase));
        CityJournalWriter.scheduleWrite(this);
    }

    public void addRoad(int x, int y, int z, String type, int rotation, int decor) {
        roads.add(roadKey(x, y, z));
        CityJournalWriter.scheduleWrite(this);
    }

    public boolean hasRoad(int x, int y, int z) {
        return roads.contains(roadKey(x, y, z));
    }

    private static String roadKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public TaskProgress getTaskProgress(int anchorChunkX, int anchorChunkZ) {
        String taskKey = anchorChunkX + "," + anchorChunkZ;
        return tasks.get(taskKey);
    }

    public boolean isTaskCompleted(int anchorChunkX, int anchorChunkZ) {
        TaskProgress progress = getTaskProgress(anchorChunkX, anchorChunkZ);
        return progress != null && "COMPLETED".equals(progress.status);
    }

    public String getLastCompletedPhase(int anchorChunkX, int anchorChunkZ) {
        TaskProgress progress = getTaskProgress(anchorChunkX, anchorChunkZ);
        return progress != null ? progress.lastPhase : null;
    }

    public double completionRatio() {
        if (totalTasks == 0) return 0;
        return (double) tasks.size() / totalTasks;
    }

    public void save() {
        try {
            Path journalsDir = journalPath.getParent();
            if (!Files.exists(journalsDir)) {
                Files.createDirectories(journalsDir);
            }

            List<RoadData> roadList = new ArrayList<>();
            for (String roadKey : roads) {
                String[] parts = roadKey.split(",");
                if (parts.length == 3) {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    roadList.add(new RoadData(x, y, z, "road", 0, 0));
                }
            }

            JournalData data = new JournalData(centerX, centerZ, totalTasks, tasks, roadList);
            String json = GSON.toJson(data);

            Files.writeString(journalPath, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Saved journal for city [{}, {}] in world {}: {} tasks, {} roads",
                        centerX, centerZ, worldId, tasks.size(), roads.size());
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to save journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }
    }

    public void delete() {
        try {
            if (Files.exists(journalPath)) {
                Files.delete(journalPath);
                if (RorcikStructuresModConfig.enableLogging) {
                    LOGGER.info("Success: [JOURNAL] Deleted journal for city [{}, {}] in world {} (100% complete)", centerX, centerZ, worldId);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error: [JOURNAL] Failed to delete journal for city [{}, {}] in world {}", centerX, centerZ, worldId, e);
        }
    }

    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public String getWorldId() { return worldId; }
    public int getTotalTasks() { return totalTasks; }
}