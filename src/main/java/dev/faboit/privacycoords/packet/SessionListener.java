package dev.faboit.privacycoords.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import dev.faboit.privacycoords.offset.OffsetService;

/**
 * Ties the lifetime of a player's offset to the lifetime of their connection: it is decided once
 * at login and thrown away when they disconnect.
 */
public final class SessionListener extends PacketListenerAbstract {

    private final OffsetService service;

    public SessionListener(OffsetService service) {
        super(PacketListenerPriority.LOWEST);
        this.service = service;
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            service.beginSession(event.getUser().getUUID());
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (event.getUser() != null) {
            service.endSession(event.getUser().getUUID());
        }
    }
}
