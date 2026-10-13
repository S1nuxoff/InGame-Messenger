package com.sinuxoff.ingamemessenger.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Where the chat's server runs its work. Paper and Folia have a global region scheduler (on Paper it's the main
 * thread); Spigot has only the Bukkit scheduler.
 */
final class Tasks {
    /** Folia: no main thread, each region ticks on its own. */
    static final boolean FOLIA = exists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean REGIONS = exists("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");

    private final Plugin plugin;

    Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    void run(Runnable task) {
        if (REGIONS) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs now if this is the thread the chat's server works on, otherwise on it. */
    void runHere(Runnable task) {
        if (!FOLIA && Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            run(task);
        }
    }

    void every(long ticks, Runnable task) {
        if (REGIONS) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), ticks, ticks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, ticks, ticks);
        }
    }

    /** Off the main thread, every so many ticks (20 a second). */
    void everyAsync(long ticks, Runnable task) {
        if (REGIONS) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), ticks * 50, ticks * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, ticks, ticks);
        }
    }

    void cancelAll() {
        if (REGIONS) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private static boolean exists(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
