package dev.faboit.privacycoords.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The handful of scheduling the plugin needs, in a form that works on a single threaded server and
 * on a regionised one alike.
 *
 * <p>On Folia and its forks (CanvasMC among them) there is no main thread: each region of the world
 * ticks on its own thread, the Bukkit scheduler is gone, and anything touching a player has to run
 * on whichever thread currently owns that player. Everything else in this plugin already works that
 * way by nature - the packet listeners run on Netty threads and only ever touch concurrent
 * collections - so scheduling is the only place that needs to know which server it is on.
 */
public interface PlatformScheduler {

    /** Runs a task on the thread that owns the given player. */
    void runForPlayer(Player player, Runnable task);

    /** Runs a task on the thread that owns the given player, after a delay in ticks. */
    void runForPlayerLater(Player player, Runnable task, long delayTicks);

    /** Runs a task off the server's tick threads entirely. */
    void runAsync(Runnable task);

    /** Drops anything still pending. Called when the plugin is disabled. */
    void cancelTasks();

    /** A short name for the scheduling model in use, for the startup log line. */
    String describe();

    /**
     * Picks an implementation for the server we are running on.
     *
     * <p>The test is whether the regionised scheduler API exists at all, rather than whether this
     * is specifically Folia. Paper has shipped that API since 1.20 and implements it correctly on a
     * single threaded server, and every Folia fork inherits it - so "the API is there" is both the
     * safer question and the one that stays true for forks this code has never heard of.
     */
    static PlatformScheduler detect(Plugin plugin) {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            return new RegionisedScheduler(plugin);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return new BukkitScheduler(plugin);
        }
    }

    /** Whether this server ticks regions in parallel rather than on one main thread. */
    static boolean isRegionised() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** The server's own name, e.g. "Paper", "Folia" or "Canvas", for logging. */
    static String serverName() {
        try {
            return Bukkit.getServer().getName();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
