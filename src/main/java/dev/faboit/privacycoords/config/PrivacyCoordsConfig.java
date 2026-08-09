package dev.faboit.privacycoords.config;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * An immutable snapshot of config.yml. A new instance is built on every reload so the packet
 * listeners can read a single volatile reference without locking.
 */
public final class PrivacyCoordsConfig {

    /** The smallest shift that can be expressed in the packets carrying chunk coordinates. */
    private static final int CHUNK = 16;

    private final boolean enabledByDefault;
    private final AxisRange xRange;
    private final AxisRange zRange;
    private final int alignment;
    private final boolean persistenceEnabled;
    private final boolean rerollOnJoin;
    private final String persistenceFile;
    private final boolean kickOnChange;
    private final String kickMessage;
    private final boolean notifyOnJoin;
    private final long notifyDelayTicks;
    private final String usePermission;
    private final String othersPermission;
    private final String reloadPermission;
    private final Translation translation;
    private final boolean chunkDataFastPath;
    private final PacketListenerPriority clientboundPriority;
    private final PacketListenerPriority serverboundPriority;
    private final boolean packetEventsUpdateChecker;
    private final boolean debug;
    private final Map<String, String> messages;

    private PrivacyCoordsConfig(Builder builder) {
        this.enabledByDefault = builder.enabledByDefault;
        this.xRange = builder.xRange;
        this.zRange = builder.zRange;
        this.alignment = builder.alignment;
        this.persistenceEnabled = builder.persistenceEnabled;
        this.rerollOnJoin = builder.rerollOnJoin;
        this.persistenceFile = builder.persistenceFile;
        this.kickOnChange = builder.kickOnChange;
        this.kickMessage = builder.kickMessage;
        this.notifyOnJoin = builder.notifyOnJoin;
        this.notifyDelayTicks = builder.notifyDelayTicks;
        this.usePermission = builder.usePermission;
        this.othersPermission = builder.othersPermission;
        this.reloadPermission = builder.reloadPermission;
        this.translation = builder.translation;
        this.chunkDataFastPath = builder.chunkDataFastPath;
        this.clientboundPriority = builder.clientboundPriority;
        this.serverboundPriority = builder.serverboundPriority;
        this.packetEventsUpdateChecker = builder.packetEventsUpdateChecker;
        this.debug = builder.debug;
        this.messages = builder.messages;
    }

    /** The range one axis is rolled from, already validated. */
    public static final class AxisRange {
        private final int min;
        private final int max;
        private final int minimumDistance;

        AxisRange(int min, int max, int minimumDistance) {
            this.min = min;
            this.max = max;
            this.minimumDistance = minimumDistance;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public int getMinimumDistance() {
            return minimumDistance;
        }
    }

    /**
     * Which categories of packet get their positions moved. Public final fields because the packet
     * listeners read these for every packet they handle.
     */
    public static final class Translation {
        public final boolean chunks;
        public final boolean blocks;
        public final boolean player;
        public final boolean spawnPosition;
        public final boolean lastDeathPosition;
        public final boolean entities;
        public final boolean entityMetadata;
        public final boolean effects;
        public final boolean particles;
        public final boolean sounds;
        public final boolean explosions;
        public final boolean worldBorder;
        public final boolean locatorBar;
        public final boolean clientMovement;
        public final boolean clientBlockInteraction;

        Translation(ConfigurationSection section) {
            this.chunks = read(section, "chunks");
            this.blocks = read(section, "blocks");
            this.player = read(section, "player");
            this.spawnPosition = read(section, "spawn-position");
            this.lastDeathPosition = read(section, "last-death-position");
            this.entities = read(section, "entities");
            this.entityMetadata = read(section, "entity-metadata");
            this.effects = read(section, "effects");
            this.particles = read(section, "particles");
            this.sounds = read(section, "sounds");
            this.explosions = read(section, "explosions");
            this.worldBorder = read(section, "world-border");
            this.locatorBar = read(section, "locator-bar");
            this.clientMovement = read(section, "client-movement");
            this.clientBlockInteraction = read(section, "client-block-interaction");
        }

        private static boolean read(ConfigurationSection section, String key) {
            return section == null || section.getBoolean(key, true);
        }

        /** The categories that the player will notice immediately if they are switched off. */
        boolean isStructurallyComplete() {
            return chunks && blocks && player && entities && clientMovement && clientBlockInteraction;
        }
    }

    private static final class Builder {
        boolean enabledByDefault;
        AxisRange xRange;
        AxisRange zRange;
        int alignment;
        boolean persistenceEnabled;
        boolean rerollOnJoin;
        String persistenceFile;
        boolean kickOnChange;
        String kickMessage;
        boolean notifyOnJoin;
        long notifyDelayTicks;
        String usePermission;
        String othersPermission;
        String reloadPermission;
        Translation translation;
        boolean chunkDataFastPath;
        PacketListenerPriority clientboundPriority;
        PacketListenerPriority serverboundPriority;
        boolean packetEventsUpdateChecker;
        boolean debug;
        Map<String, String> messages;
    }

    public static PrivacyCoordsConfig load(FileConfiguration configuration, Logger logger) {
        Builder builder = new Builder();

        builder.enabledByDefault = configuration.getBoolean("enabled-by-default", false);

        int sharedMin = configuration.getInt("offset.min", -10000);
        int sharedMax = configuration.getInt("offset.max", 10000);
        int sharedDistance = configuration.getInt("offset.minimum-distance", 1000);
        builder.alignment = readAlignment(configuration, logger);
        builder.xRange = readAxis(configuration, logger, "x", sharedMin, sharedMax, sharedDistance);
        builder.zRange = readAxis(configuration, logger, "z", sharedMin, sharedMax, sharedDistance);

        builder.persistenceEnabled = configuration.getBoolean("persistence.enabled", true);
        builder.rerollOnJoin = configuration.getBoolean("persistence.reroll-on-join", false);
        builder.persistenceFile = configuration.getString("persistence.file", "data.yml");
        if (builder.persistenceFile == null || builder.persistenceFile.trim().isEmpty()) {
            builder.persistenceFile = "data.yml";
        }

        builder.kickOnChange = configuration.getBoolean("apply.kick-on-change", false);
        builder.kickMessage = configuration.getString("apply.kick-message",
                "&bPrivacyCoords&7: reconnect to apply your new coordinates.");
        builder.notifyOnJoin = configuration.getBoolean("apply.notify-on-join", true);
        builder.notifyDelayTicks = Math.max(0, configuration.getInt("apply.notify-delay-ticks", 40));

        builder.usePermission = configuration.getString("permissions.use", "privacycoords.use");
        builder.othersPermission = configuration.getString("permissions.others", "privacycoords.others");
        builder.reloadPermission = configuration.getString("permissions.reload", "privacycoords.reload");

        builder.translation = new Translation(configuration.getConfigurationSection("translate"));
        if (!builder.translation.isStructurallyComplete()) {
            logger.warning("One of the structural translate.* options is switched off. The player's world "
                    + "will only be partly shifted, which they will see as blocks, entities or their own "
                    + "position being in the wrong place.");
        }

        builder.chunkDataFastPath = configuration.getBoolean("advanced.chunk-data-fast-path", true);
        builder.clientboundPriority = readPriority(configuration, logger,
                "advanced.clientbound-priority", PacketListenerPriority.HIGHEST);
        builder.serverboundPriority = readPriority(configuration, logger,
                "advanced.serverbound-priority", PacketListenerPriority.LOWEST);
        builder.packetEventsUpdateChecker = configuration.getBoolean("advanced.packetevents-update-checker", false);

        builder.debug = configuration.getBoolean("debug", false);

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key.toLowerCase(Locale.ROOT), section.getString(key, ""));
            }
        }
        builder.messages = messages;

        return new PrivacyCoordsConfig(builder);
    }

    private static int readAlignment(FileConfiguration configuration, Logger logger) {
        int alignment = configuration.getInt("offset.alignment", CHUNK);
        if (alignment < CHUNK) {
            if (alignment != CHUNK) {
                logger.warning("offset.alignment (" + alignment + ") is below one chunk; using " + CHUNK + ".");
            }
            return CHUNK;
        }
        if (alignment % CHUNK != 0) {
            int rounded = ((alignment / CHUNK) + 1) * CHUNK;
            logger.warning("offset.alignment (" + alignment + ") is not a multiple of " + CHUNK
                    + "; rounding up to " + rounded + ".");
            return rounded;
        }
        return alignment;
    }

    private static AxisRange readAxis(FileConfiguration configuration, Logger logger, String axis,
                                      int sharedMin, int sharedMax, int sharedDistance) {
        int min = configuration.getInt("offset." + axis + ".min", sharedMin);
        int max = configuration.getInt("offset." + axis + ".max", sharedMax);
        if (min > max) {
            logger.warning("offset." + axis + ".min (" + min + ") is larger than offset." + axis + ".max ("
                    + max + "), swapping them.");
            int swap = min;
            min = max;
            max = swap;
        }

        int distance = Math.abs(configuration.getInt("offset." + axis + ".minimum-distance", sharedDistance));
        int reach = Math.max(Math.abs(min), Math.abs(max));
        if (distance > reach) {
            logger.warning("offset." + axis + ".minimum-distance (" + distance + ") is larger than anything "
                    + "the configured range can produce; ignoring it.");
            distance = 0;
        }
        return new AxisRange(min, max, distance);
    }

    private static PacketListenerPriority readPriority(FileConfiguration configuration, Logger logger,
                                                       String path, PacketListenerPriority fallback) {
        String raw = configuration.getString(path, fallback.name());
        try {
            return PacketListenerPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            logger.warning(path + " is set to '" + raw + "', which is not a listener priority; using "
                    + fallback.name() + ".");
            return fallback;
        }
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public AxisRange getXRange() {
        return xRange;
    }

    public AxisRange getZRange() {
        return zRange;
    }

    public int getAlignment() {
        return alignment;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public boolean isRerollOnJoin() {
        return rerollOnJoin;
    }

    public String getPersistenceFile() {
        return persistenceFile;
    }

    public boolean isKickOnChange() {
        return kickOnChange;
    }

    public String getKickMessage() {
        return colorize(kickMessage);
    }

    public boolean isNotifyOnJoin() {
        return notifyOnJoin;
    }

    public long getNotifyDelayTicks() {
        return notifyDelayTicks;
    }

    public Translation translation() {
        return translation;
    }

    public boolean isChunkDataFastPath() {
        return chunkDataFastPath;
    }

    public PacketListenerPriority getClientboundPriority() {
        return clientboundPriority;
    }

    public PacketListenerPriority getServerboundPriority() {
        return serverboundPriority;
    }

    public boolean isPacketEventsUpdateChecker() {
        return packetEventsUpdateChecker;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean canUse(CommandSender sender) {
        return allowed(sender, usePermission);
    }

    public boolean canTargetOthers(CommandSender sender) {
        return allowed(sender, othersPermission);
    }

    public boolean canReload(CommandSender sender) {
        return allowed(sender, reloadPermission);
    }

    private static boolean allowed(CommandSender sender, String node) {
        // An empty node in the config means "everybody may do this".
        return node == null || node.trim().isEmpty() || sender.hasPermission(node);
    }

    /**
     * Looks up a message, prefixes it and applies colour codes.
     *
     * @param placeholders alternating placeholder name and replacement, e.g. {@code "player", "Steve"}
     */
    public String message(String key, String... placeholders) {
        String raw = messages.get(key.toLowerCase(Locale.ROOT));
        if (raw == null || raw.isEmpty()) {
            return colorize(prefix() + "&7(missing message: " + key + ")");
        }
        return colorize(prefix() + replace(raw, placeholders));
    }

    /**
     * Same as {@link #message} but with neither prefix nor colours applied, for fragments that get
     * embedded into another message before it is coloured.
     */
    public String rawMessage(String key, String... placeholders) {
        return replace(messages.getOrDefault(key.toLowerCase(Locale.ROOT), ""), placeholders);
    }

    /**
     * Turns an already assembled raw fragment into a finished, prefixed, coloured line.
     */
    public String prefixed(String rawText) {
        return colorize(prefix() + rawText);
    }

    private static String replace(String raw, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return raw;
    }

    private String prefix() {
        return messages.getOrDefault("prefix", "");
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
