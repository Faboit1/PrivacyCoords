package dev.faboit.privacycoords;

import com.github.retrooper.packetevents.PacketEvents;
import dev.faboit.privacycoords.command.PrivacyCoordsCommand;
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
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

    @Override
    public void onLoad() {
        // PacketEvents is shaded into this jar, so it has to be built and loaded before any
        // player can connect.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PrivacyCoordsConfig config = PrivacyCoordsConfig.load(getConfig(), getLogger());

        OffsetStorage storage = new OffsetStorage(new File(getDataFolder(), "data.yml"), getLogger());
        service = new OffsetService(this, storage, config);

        PacketEvents.getAPI().getEventManager().registerListeners(
                new SessionListener(service),
                new ServerboundOffsetListener(service),
                new ClientboundOffsetListener(service));
        PacketEvents.getAPI().init();

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
                + (config.isEnabledByDefault() ? "for everyone by default." : "for players who opt in with /privacycoords enable."));
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
        service.setConfig(PrivacyCoordsConfig.load(getConfig(), getLogger()));
    }

    public OffsetService getService() {
        return service;
    }
}
