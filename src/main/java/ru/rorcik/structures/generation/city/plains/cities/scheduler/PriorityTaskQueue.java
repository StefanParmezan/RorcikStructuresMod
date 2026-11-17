package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityTaskQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ConcurrentSkipListSet<PrioritizedTask> tasks = new ConcurrentSkipListSet<>();
    private final AtomicInteger uniqueSequence = new AtomicInteger(0);
    private final int maxCapacity;

    public PriorityTaskQueue(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean offer(AnchorChunkTask task) {
        if (tasks.size() >= maxCapacity) {
            LOGGER.warn("Warn: [TASK-QUEUE] Queue exceeded capacity ({}/{}). Expanding dynamically to prevent task loss for: {}",
                    tasks.size(), maxCapacity, task);
        }

        PrioritizedTask prioritized = new PrioritizedTask(task, task.priority(), uniqueSequence.getAndIncrement());
        boolean added = tasks.add(prioritized);

        if (added && RorcikStructuresModConfig.enableLogging) {
            LOGGER.debug("Info: [TASK-QUEUE] Added task {} (priority={}, queue size={})", task, task.priority(), tasks.size());
        }
        return added;
    }

    public AnchorChunkTask poll() {
        PrioritizedTask prioritized = tasks.pollFirst();
        return prioritized != null ? prioritized.task : null;
    }

    public void updatePriorities(int playerBlockX, int playerBlockZ) {
        List<PrioritizedTask> oldTasks = new ArrayList<>(tasks);
        tasks.clear();
        for (PrioritizedTask pt : oldTasks) {
            pt.task.updatePriority(playerBlockX, playerBlockZ);
            PrioritizedTask updated = new PrioritizedTask(pt.task, pt.task.priority(), uniqueSequence.getAndIncrement());
            tasks.add(updated);
        }
        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.debug("Info: [TASK-QUEUE] Updated priorities for {} tasks based on player at [{}, {}]", oldTasks.size(), playerBlockX, playerBlockZ);
        }
    }

    public List<AnchorChunkTask> removeTasksForCity(int cityX, int cityZ) {
        List<AnchorChunkTask> removed = new ArrayList<>();
        Iterator<PrioritizedTask> it = tasks.iterator();
        while (it.hasNext()) {
            PrioritizedTask pt = it.next();
            if (pt.task.centerX() == cityX && pt.task.centerZ() == cityZ
                    && pt.task.state() == AnchorChunkTask.State.PENDING) {
                it.remove();
                removed.add(pt.task);
            }
        }
        if (RorcikStructuresModConfig.enableLogging && !removed.isEmpty()) {
            LOGGER.info("Info: [TASK-QUEUE] Removed {} pending tasks for city [{}, {}]", removed.size(), cityX, cityZ);
        }
        return removed;
    }

    public int size() { return tasks.size(); }
    public boolean isEmpty() { return tasks.isEmpty(); }
    public int getMaxCapacity() { return maxCapacity; }
    public void clear() { tasks.clear(); }

    private static class PrioritizedTask implements Comparable<PrioritizedTask> {
        final AnchorChunkTask task;
        final int priority;
        final int sequence;

        PrioritizedTask(AnchorChunkTask task, int priority, int sequence) {
            this.task = task;
            this.priority = priority;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(PrioritizedTask other) {
            int cmp = Integer.compare(this.priority, other.priority);
            if (cmp != 0) return cmp;
            return Integer.compare(this.sequence, other.sequence);
        }
    }
}