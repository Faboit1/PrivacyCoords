package dev.faboit.privacycoords.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling through the classic single threaded Bukkit scheduler, for Spigot and for Paper builds
 * old enough to predate the regionised API.
 */
final class BukkitScheduler implements PlatformScheduler {

    private final Plugin plugin;

    BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runForPlayerLater(Player player, Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void cancelTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    @Override
    public String describe() {
        return "single main thread";
    }
}
