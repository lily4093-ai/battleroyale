package com.example.battleroyale.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal replacement for Bukkit's BukkitScheduler#runTaskLater / #runTaskTimer.
 * Ticked once per server tick from BattleRoyaleMod's ServerTickEvent handler.
 */
public final class TickScheduler {

    public interface TaskHandle {
        void cancel();
        boolean isCancelled();
    }

    private static final class ScheduledTask implements TaskHandle {
        final Runnable runnable;
        long ticksRemaining;
        final long period; // <= 0 means one-shot
        volatile boolean cancelled = false;

        ScheduledTask(Runnable runnable, long delayTicks, long period) {
            this.runnable = runnable;
            this.ticksRemaining = delayTicks;
            this.period = period;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final List<ScheduledTask> TASKS = new CopyOnWriteArrayList<>();

    private TickScheduler() {
    }

    public static TaskHandle runLater(Runnable runnable, long delayTicks) {
        ScheduledTask task = new ScheduledTask(runnable, Math.max(0, delayTicks), 0);
        TASKS.add(task);
        return task;
    }

    public static TaskHandle runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        ScheduledTask task = new ScheduledTask(runnable, Math.max(0, delayTicks), periodTicks);
        TASKS.add(task);
        return task;
    }

    public static void tick() {
        for (ScheduledTask task : TASKS) {
            if (task.cancelled) {
                TASKS.remove(task);
                continue;
            }
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                try {
                    task.runnable.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (task.period > 0 && !task.cancelled) {
                    task.ticksRemaining = task.period;
                } else {
                    TASKS.remove(task);
                }
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
