package dev.faboit.privacycoords.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling through the regionised API: the entity scheduler for anything that touches a player,
 * the async scheduler for disk work.
 *
 * <p>Used on Folia, on its forks such as CanvasMC, and on Paper - where the same API exists and
 * simply resolves to the one main thread.
 *
 * <p>This class must only be loaded once {@link PlatformScheduler#detect} has confirmed the API is
 * present, since it names those types directly.
 */
final class RegionisedScheduler implements PlatformScheduler {

    private final Plugin plugin;

    RegionisedScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        runForPlayerLater(player, task, 1L);
    }

    @Override
    public void runForPlayerLater(Player player, Runnable task, long delayTicks) {
        // A delay below one tick is treated as one tick, and the task is dropped outright if the
        // player has left in the meantime - which is exactly what we want for a kick or a reminder.
        player.getScheduler().execute(plugin, task, null, Math.max(1L, delayTicks));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public void cancelTasks() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public String describe() {
        return PlatformScheduler.isRegionised() ? "regionised (per-region threads)" : "regionised API on a single thread";
    }
}
