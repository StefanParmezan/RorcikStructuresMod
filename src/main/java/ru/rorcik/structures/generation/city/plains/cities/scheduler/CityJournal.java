package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CityJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path JOURNALS_DIR = Paths.get("rorcik_journals");

    private final int centerX;
    private final int centerZ;
    private final String worldId;
    private final Path journalPath;
    private final Map<String, TaskProgress> tasks = new ConcurrentHashMap<>();
    private int totalTasks;

    public static class TaskProgress {
        public String status;
        public String lastPhase;

        public TaskProgress(String status, String lastPhase) {
            this.status = status;
            this.lastPhase = lastPhase;
        }
    }

    public static class JournalData {
        public int centerX;
        public int centerZ;
        public int totalTasks;
        public Map<String, TaskProgress> tasks;

        public JournalData(int centerX, int centerZ, int totalTasks, Map<String, TaskProgress> tasks) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.totalTasks = totalTasks;
            this.tasks = tasks;
        }
    }

    public CityJournal(int centerX, int centerZ, String worldId) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.worldId = worldId;
        this.journalPath = JOURNALS_DIR.resolve(String.format("city_%s_plains@%d_%d.json", worldId, centerX, centerZ));
        this.totalTasks = 0;
    }

    public static String getWorldId(ServerLevel level) {
        Path worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return Integer.toHexString(worldDir.hashCode());
    }

    public static CityJournal load(int centerX, int centerZ, ServerLevel level) {
        String worldId = getWorldId(level);
        CityJournal journal = new CityJournal(centerX, centerZ, worldId);
        Path path = journal.journalPath;

        if (!Files.exists(path)) {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] No journal found for city [{}, {}] in world {}, creating new", centerX, centerZ, worldId);
            }
            return journal;
        }

        try {
            String json = Files.readString(path);
            Type type = new TypeToken<JournalData>() {}.getType();
            JournalData data = GSON.fromJson(json, type);

            journal.totalTasks = data.totalTasks;
            journal.tasks.putAll(data.tasks);

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Loaded journal for city [{}, {}] in world {}: {} tasks, {} completed",
                        centerX, centerZ, worldId, data.totalTasks, data.tasks.size());
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
            if (!Files.exists(JOURNALS_DIR)) {
                Files.createDirectories(JOURNALS_DIR);
            }

            JournalData data = new JournalData(centerX, centerZ, totalTasks, tasks);
            String json = GSON.toJson(data);

            Files.writeString(journalPath, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL] Saved journal for city [{}, {}] in world {}: {} tasks", centerX, centerZ, worldId, tasks.size());
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