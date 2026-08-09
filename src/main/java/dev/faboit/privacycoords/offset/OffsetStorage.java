package dev.faboit.privacycoords.offset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes data.yml, which remembers each player's preference and the offset that was
 * rolled for them.
 *
 * <p>The file is small (one entry per player who has ever touched the command) so it is loaded
 * entirely into memory on startup; the packet listeners never touch the disk.
 */
public final class OffsetStorage {

    private static final String PLAYERS = "players";

    private final File file;
    private final Logger logger;
    private final Object saveLock = new Object();

    public OffsetStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** A single stored player entry. */
    public static final class Record {
        public final boolean enabled;
        public final CoordinateOffset offset;

        public Record(boolean enabled, CoordinateOffset offset) {
            this.enabled = enabled;
            this.offset = offset;
        }
    }

    public Map<UUID, Record> load() {
        Map<UUID, Record> records = new HashMap<>();
        if (!file.exists()) {
            return records;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection(PLAYERS);
        if (players == null) {
            return records;
        }

        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                logger.warning("Skipping malformed player id in data.yml: " + key);
                continue;
            }
            ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            boolean enabled = entry.getBoolean("enabled", false);
            CoordinateOffset offset = CoordinateOffset.ofBlocks(entry.getInt("x", 0), entry.getInt("z", 0));
            records.put(uuid, new Record(enabled, offset));
        }
        return records;
    }

    /**
     * Writes the given snapshot to disk. Safe to call from any thread; concurrent calls are
     * serialised so the file never ends up half written.
     */
    public void save(Map<UUID, Record> records) {
        synchronized (saveLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Record> entry : records.entrySet()) {
                String path = PLAYERS + "." + entry.getKey();
                Record record = entry.getValue();
                yaml.set(path + ".enabled", record.enabled);
                yaml.set(path + ".x", record.offset.getX());
                yaml.set(path + ".z", record.offset.getZ());
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    logger.warning("Could not create " + parent + ", offsets will not be remembered.");
                    return;
                }
                yaml.save(file);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not save data.yml", ex);
            }
        }
    }
}
