package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.concurrent.*;

public class CityJournalWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService WRITER_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CityJournalWriter");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentLinkedQueue<CityJournal> WRITE_QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean running = true;
    private static long lastWriteTime = 0;
    private static final long WRITE_INTERVAL_MS = 5000;

    static {
        WRITER_POOL.submit(() -> {
            if (RorcikStructuresModConfig.enableLogging) {
                LOGGER.info("Info: [JOURNAL-WRITER] Started");
            }
            while (running) {
                try {
                    CityJournal journal = WRITE_QUEUE.poll();
                    if (journal != null) {
                        journal.save();
                        lastWriteTime = System.currentTimeMillis();
                    } else {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error: [JOURNAL-WRITER] Failed to write journal", e);
                }
            }
        });
    }

    public static void scheduleWrite(CityJournal journal) {
        long now = System.currentTimeMillis();
        if (now - lastWriteTime < WRITE_INTERVAL_MS) {
            return;
        }
        WRITE_QUEUE.offer(journal);
    }

    public static void shutdown() {
        running = false;
        WRITER_POOL.shutdown();
        try {
            if (!WRITER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                WRITER_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            WRITER_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}