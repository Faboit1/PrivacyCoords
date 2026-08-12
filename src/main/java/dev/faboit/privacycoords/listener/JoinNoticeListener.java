package dev.faboit.privacycoords.listener;

import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.offset.OffsetService;
import dev.faboit.privacycoords.platform.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Reminds a player on join that what F3 shows them is not where they are, so nobody goes live
 * believing the feature is off when it is on.
 *
 * <p>Controlled by {@code apply.notify-on-join} and {@code apply.notify-delay-ticks}.
 */
public final class JoinNoticeListener implements Listener {

    private final PlatformScheduler scheduler;
    private final OffsetService service;

    public JoinNoticeListener(PlatformScheduler scheduler, OffsetService service) {
        this.scheduler = scheduler;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PrivacyCoordsConfig config = service.getConfig();
        if (!config.isNotifyOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        // Delayed so the notice does not scroll past behind the join messages. On a regionised
        // server this runs on the thread that owns the player, and is dropped if they leave first.
        scheduler.runForPlayerLater(player, () -> {
            if (!player.isOnline() || !service.isActive(player.getUniqueId())) {
                return;
            }
            player.sendMessage(service.getConfig().message("join-notice"));
        }, config.getNotifyDelayTicks());
    }
}
