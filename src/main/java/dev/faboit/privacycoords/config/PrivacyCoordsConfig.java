package dev.faboit.privacycoords.config;

import org.bukkit.ChatColor;
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

    private final boolean enabledByDefault;
    private final int offsetMin;
    private final int offsetMax;
    private final int minimumDistance;
    private final boolean persistenceEnabled;
    private final boolean rerollOnJoin;
    private final boolean kickOnChange;
    private final String kickMessage;
    private final boolean debug;
    private final Map<String, String> messages;

    private PrivacyCoordsConfig(boolean enabledByDefault, int offsetMin, int offsetMax, int minimumDistance,
                                boolean persistenceEnabled, boolean rerollOnJoin, boolean kickOnChange,
                                String kickMessage, boolean debug, Map<String, String> messages) {
        this.enabledByDefault = enabledByDefault;
        this.offsetMin = offsetMin;
        this.offsetMax = offsetMax;
        this.minimumDistance = minimumDistance;
        this.persistenceEnabled = persistenceEnabled;
        this.rerollOnJoin = rerollOnJoin;
        this.kickOnChange = kickOnChange;
        this.kickMessage = kickMessage;
        this.debug = debug;
        this.messages = messages;
    }

    public static PrivacyCoordsConfig load(FileConfiguration configuration, Logger logger) {
        int min = configuration.getInt("offset.min", -10000);
        int max = configuration.getInt("offset.max", 10000);
        if (min > max) {
            logger.warning("offset.min (" + min + ") is larger than offset.max (" + max + "), swapping them.");
            int swap = min;
            min = max;
            max = swap;
        }

        int minimumDistance = Math.abs(configuration.getInt("offset.minimum-distance", 1000));
        int reach = Math.max(Math.abs(min), Math.abs(max));
        if (minimumDistance > reach) {
            logger.warning("offset.minimum-distance (" + minimumDistance + ") is larger than anything "
                    + "offset.min/offset.max can produce; ignoring it.");
            minimumDistance = 0;
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key.toLowerCase(Locale.ROOT), section.getString(key, ""));
            }
        }

        return new PrivacyCoordsConfig(
                configuration.getBoolean("enabled-by-default", false),
                min,
                max,
                minimumDistance,
                configuration.getBoolean("persistence.enabled", true),
                configuration.getBoolean("persistence.reroll-on-join", false),
                configuration.getBoolean("apply.kick-on-change", false),
                configuration.getString("apply.kick-message", "&bPrivacyCoords&7: reconnect to apply your new coordinates."),
                configuration.getBoolean("debug", false),
                messages);
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public int getOffsetMin() {
        return offsetMin;
    }

    public int getOffsetMax() {
        return offsetMax;
    }

    public int getMinimumDistance() {
        return minimumDistance;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public boolean isRerollOnJoin() {
        return rerollOnJoin;
    }

    public boolean isKickOnChange() {
        return kickOnChange;
    }

    public String getKickMessage() {
        return colorize(kickMessage);
    }

    public boolean isDebug() {
        return debug;
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
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return colorize(prefix() + raw);
    }

    /**
     * Same as {@link #message} but with neither prefix nor colours applied, for fragments that get
     * embedded into another message before it is coloured.
     */
    public String rawMessage(String key, String... placeholders) {
        String raw = messages.getOrDefault(key.toLowerCase(Locale.ROOT), "");
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return raw;
    }

    /**
     * Turns an already assembled raw fragment into a finished, prefixed, coloured line.
     */
    public String prefixed(String rawText) {
        return colorize(prefix() + rawText);
    }

    private String prefix() {
        return messages.getOrDefault("prefix", "");
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
