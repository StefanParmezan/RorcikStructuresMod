package ru.rorcik.structures.generation.city.plains.cities;

import net.minecraft.core.BlockPos;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CityBuildScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Deque<ChunkLoadTask> chunkQueue = new ArrayDeque<>();
    private static final Deque<BuildTask> buildQueue = new ArrayDeque<>();
    private static Runnable onChunksReady = null;
    private static boolean active = false;

    public record ChunkLoadTask(int chunkX, int chunkZ) {}
    public record BuildTask(Runnable action) {}

    /**
     * Запланировать загрузку чанков, потом выполнить callback.
     */
    public static void scheduleChunkLoading(
            Set<Long> chunkKeys,
            Runnable whenReady
    ) {
        chunkQueue.clear();
        for (long key : chunkKeys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chunkQueue.add(new ChunkLoadTask(cx, cz));
        }
        onChunksReady = whenReady;
        active = true;
        LOGGER.info("[SCHEDULER] Queued {} chunks for loading", chunkKeys.size());
    }

    /**
     * Запланировать серию действий по постройке.
     */
    public static void scheduleBuildActions(java.util.List<Runnable> actions) {
        buildQueue.clear();
        for (Runnable action : actions) {
            buildQueue.add(new BuildTask(action));
        }
        active = true;
        LOGGER.info("[SCHEDULER] Queued {} build actions", actions.size());
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;

        ServerLevel level = event.getServer().overworld();

        // Фаза 1: загрузка чанков
        if (!chunkQueue.isEmpty()) {
            int loaded = 0;
            while (!chunkQueue.isEmpty() && loaded < GeneratorConfig.CHUNKS_PER_TICK) {
                ChunkLoadTask task = chunkQueue.poll();
                level.getChunkSource().getChunk(task.chunkX, task.chunkZ, ChunkStatus.FULL, true);
                loaded++;
            }

            if (chunkQueue.isEmpty() && onChunksReady != null) {
                LOGGER.info("[SCHEDULER] All chunks loaded, starting build phase");
                Runnable callback = onChunksReady;
                onChunksReady = null;
                callback.run();
            }
            return;
        }

        // Фаза 2: постройка структур
        if (!buildQueue.isEmpty()) {
            int built = 0;
            while (!buildQueue.isEmpty() && built < GeneratorConfig.STRUCTURES_PER_TICK) {
                BuildTask task = buildQueue.poll();
                try {
                    task.action.run();
                } catch (Exception e) {
                    LOGGER.error("[SCHEDULER] Build action failed", e);
                }
                built++;
            }

            if (buildQueue.isEmpty()) {
                LOGGER.info("[SCHEDULER] City generation complete");
                active = false;
            }
            return;
        }

        active = false;
    }

    public static void reset() {
        chunkQueue.clear();
        buildQueue.clear();
        onChunksReady = null;
        active = false;
    }
}