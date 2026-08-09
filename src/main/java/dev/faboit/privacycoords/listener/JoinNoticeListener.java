package dev.faboit.privacycoords.listener;

import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.offset.OffsetService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Reminds a player on join that what F3 shows them is not where they are, so nobody goes live
 * believing the feature is off when it is on.
 *
 * <p>Controlled by {@code apply.notify-on-join} and {@code apply.notify-delay-ticks}.
 */
public final class JoinNoticeListener implements Listener {

    private final Plugin plugin;
    private final OffsetService service;

    public JoinNoticeListener(Plugin plugin, OffsetService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PrivacyCoordsConfig config = service.getConfig();
        if (!config.isNotifyOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        // Delayed so the notice does not scroll past behind the join messages.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !service.isActive(player.getUniqueId())) {
                return;
            }
            player.sendMessage(service.getConfig().message("join-notice"));
        }, config.getNotifyDelayTicks());
    }
}
