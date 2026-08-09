package dev.faboit.privacycoords;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import dev.faboit.privacycoords.command.PrivacyCoordsCommand;
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.listener.JoinNoticeListener;
import dev.faboit.privacycoords.offset.OffsetService;
import dev.faboit.privacycoords.offset.OffsetStorage;
import dev.faboit.privacycoords.packet.ClientboundOffsetListener;
import dev.faboit.privacycoords.packet.ServerboundOffsetListener;
import dev.faboit.privacycoords.packet.SessionListener;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * PrivacyCoords - gives every player their own random view of where they are in the world.
 *
 * <p>The whole plugin is two packet listeners: one adds a per player offset to every position the
 * server sends, the other takes it back off everything the client sends. Nothing on the server
 * side ever sees the shifted numbers, and the client never sees the real ones.
 */
public final class PrivacyCoordsPlugin extends JavaPlugin {

    private OffsetService service;
    private PacketListenerCommon sessionListener;
    private PacketListenerCommon clientboundListener;
    private PacketListenerCommon serverboundListener;

    @Override
    public void onLoad() {
        // PacketEvents is shaded into this jar, so it has to be built and loaded before any
        // player can connect.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        saveDefaultConfig();
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(getConfig().getBoolean("advanced.packetevents-update-checker", false));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PrivacyCoordsConfig config = PrivacyCoordsConfig.load(getConfig(), getLogger());

        OffsetStorage storage = new OffsetStorage(
                new File(getDataFolder(), config.getPersistenceFile()), getLogger());
        service = new OffsetService(this, storage, config);

        registerPacketListeners(config);
        PacketEvents.getAPI().init();

        getServer().getPluginManager().registerEvents(new JoinNoticeListener(this, service), this);

        PluginCommand command = getCommand("privacycoords");
        if (command != null) {
            PrivacyCoordsCommand executor = new PrivacyCoordsCommand(this, service);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("The /privacycoords command is missing from plugin.yml; the plugin is running "
                    + "but cannot be controlled.");
        }

        getLogger().info("Ready. Coordinates are randomised "
                + (config.isEnabledByDefault()
                ? "for everyone by default."
                : "for players who opt in with /privacycoords enable."));
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.saveNow();
            service.endAllSessions();
        }
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
    }

    /**
     * Re-reads config.yml. Live sessions keep the offset they were given, because their clients
     * have already been sent a world at those coordinates.
     */
    public void reload() {
        reloadConfig();
        PrivacyCoordsConfig config = PrivacyCoordsConfig.load(getConfig(), getLogger());
        service.setConfig(config);

        // The listener priorities are baked in when a listener is registered, so re-register if
        // they changed.
        if (clientboundListener == null
                || clientboundListener.getPriority() != config.getClientboundPriority()
                || serverboundListener.getPriority() != config.getServerboundPriority()) {
            unregisterPacketListeners();
            registerPacketListeners(config);
        }
    }

    private void registerPacketListeners(PrivacyCoordsConfig config) {
        sessionListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new SessionListener(service));
        serverboundListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new ServerboundOffsetListener(service, config.getServerboundPriority()));
        clientboundListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new ClientboundOffsetListener(service, config.getClientboundPriority()));
    }

    private void unregisterPacketListeners() {
        if (sessionListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(sessionListener);
            sessionListener = null;
        }
        if (serverboundListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(serverboundListener);
            serverboundListener = null;
        }
        if (clientboundListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(clientboundListener);
            clientboundListener = null;
        }
    }

    public OffsetService getService() {
        return service;
    }
}
