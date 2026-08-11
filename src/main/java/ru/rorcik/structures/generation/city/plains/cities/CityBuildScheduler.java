package ru.rorcik.structures.generation.city.plains.cities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Deque<ChunkLoadTask> chunkQueue = new ArrayDeque<>();
    private static final Deque<BuildTask> buildQueue = new ArrayDeque<>();
    private static Runnable onChunksReady = null;
    private static boolean active = false;

    private static long buildStartTime = 0;
    private static int totalBuildActions = 0;
    private static int completedBuildActions = 0;

    private static long chunkLoadStartTime = 0;
    private static int totalChunks = 0;
    private static int loadedChunks = 0;

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

    public static void scheduleChunkLoading(Set<Long> chunkKeys, Runnable whenReady) {
        chunkQueue.clear();
        buildQueue.clear();

        for (long key : chunkKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chunkQueue.add(new ChunkLoadTask(cx, cz));
        }

        onChunksReady = whenReady;
        active = true;

        totalChunks = chunkKeys.size();
        loadedChunks = 0;
        chunkLoadStartTime = System.currentTimeMillis();

        LOGGER.info("[SCHEDULER] Queued {} chunks for loading ({} per tick)",
                totalChunks, GeneratorConfig.CHUNKS_PER_TICK);
    }

    public static void scheduleBuildActions(List<Runnable> actions) {
        buildQueue.clear();
        for (Runnable action : actions) {
            buildQueue.add(new BuildTask(action));
        }

        totalBuildActions = actions.size();
        completedBuildActions = 0;
        buildStartTime = System.currentTimeMillis();

        LOGGER.info("[SCHEDULER] Queued {} build actions ({} per tick)",
                totalBuildActions, GeneratorConfig.STRUCTURES_PER_TICK);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!chunkQueue.isEmpty()) {
            processChunkLoading(level);
        } else if (onChunksReady != null) {
            long elapsed = System.currentTimeMillis() - chunkLoadStartTime;
            LOGGER.info("[SCHEDULER] All {} chunks loaded in {} ms, starting build phase",
                    totalChunks, elapsed);
            Runnable callback = onChunksReady;
            onChunksReady = null;
            callback.run();
        } else if (!buildQueue.isEmpty()) {
            processBuildActions();
        } else {
            long elapsed = System.currentTimeMillis() - buildStartTime;
            LOGGER.info("[SCHEDULER] City generation complete: {} structures in {} ms ({} per second)",
                    completedBuildActions, elapsed,
                    elapsed > 0 ? (completedBuildActions * 1000L) / elapsed : 0);
            active = false;
        }
    }

    private static void processChunkLoading(ServerLevel level) {
        int processed = 0;
        var iterator = chunkQueue.iterator();

        while (iterator.hasNext() && processed < GeneratorConfig.CHUNKS_PER_TICK) {
            ChunkLoadTask task = iterator.next();
            level.getChunkSource().getChunk(task.chunkX(), task.chunkZ(), ChunkStatus.FULL, true);
            iterator.remove();
            processed++;
            loadedChunks++;
        }

        if (processed > 0 && totalChunks > 0) {
            int percent = (loadedChunks * 100) / totalChunks;
            LOGGER.info("[SCHEDULER] Chunks: {}/{} ({}%)", loadedChunks, totalChunks, percent);
        }
    }

    private static void processBuildActions() {
        int processed = 0;

        while (!buildQueue.isEmpty() && processed < GeneratorConfig.STRUCTURES_PER_TICK) {
            BuildTask task = buildQueue.poll();
            if (task != null) {
                try {
                    task.action().run();
                    completedBuildActions++;
                } catch (Exception e) {
                    LOGGER.error("[SCHEDULER] Build action failed", e);
                }
                processed++;
            }
        }

        if (completedBuildActions % 50 == 0 || buildQueue.isEmpty()) {
            LOGGER.info("[SCHEDULER] Build progress: {}/{} structures",
                    completedBuildActions, totalBuildActions);
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        chunkQueue.clear();
        buildQueue.clear();
        onChunksReady = null;
        active = false;
        totalBuildActions = 0;
        completedBuildActions = 0;
        totalChunks = 0;
        loadedChunks = 0;
    }
}