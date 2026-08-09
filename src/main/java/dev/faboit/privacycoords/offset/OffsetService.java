package dev.faboit.privacycoords.offset;

import com.github.retrooper.packetevents.protocol.player.User;
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Owns every piece of per-player state: the stored preference, the stored offset, and the offset
 * that is currently live for a connected player.
 *
 * <p>Everything that the packet listeners touch is a {@link ConcurrentHashMap} lookup, because
 * those listeners run on Netty threads and not on the server thread.
 */
public final class OffsetService {

    private final Plugin plugin;
    private final OffsetStorage storage;
    private final Random random = new java.security.SecureRandom();

    /** Preference and last rolled offset, for every player we know about. */
    private final Map<UUID, OffsetStorage.Record> records = new ConcurrentHashMap<>();

    /**
     * The offset in force for each connected player. A player who is connected with the feature
     * off maps to {@link CoordinateOffset#NONE}; a missing entry means "we have not started a
     * session for them yet".
     */
    private final Map<UUID, CoordinateOffset> sessions = new ConcurrentHashMap<>();

    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private volatile PrivacyCoordsConfig config;

    public OffsetService(Plugin plugin, OffsetStorage storage, PrivacyCoordsConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;
        this.records.putAll(storage.load());
    }

    public void setConfig(PrivacyCoordsConfig config) {
        this.config = config;
    }

    public PrivacyCoordsConfig getConfig() {
        return config;
    }

    // ------------------------------------------------------------------ sessions

    /**
     * The offset to apply to packets for this connection. Starts the session on first use so that
     * no packet can slip through untranslated, even if it arrives before the login event.
     */
    public CoordinateOffset sessionOffset(User user) {
        if (user == null) {
            return CoordinateOffset.NONE;
        }
        UUID uuid = user.getUUID();
        if (uuid == null) {
            return CoordinateOffset.NONE;
        }
        CoordinateOffset active = sessions.get(uuid);
        return active != null ? active : beginSession(uuid);
    }

    /**
     * Decides the offset for a fresh connection and makes it live. Called once per login; calling
     * it again for a player who already has a live session returns the existing offset, so an
     * offset can never change underneath a client that has already been sent chunks.
     */
    public CoordinateOffset beginSession(UUID uuid) {
        return sessions.computeIfAbsent(uuid, id -> {
            PrivacyCoordsConfig current = config;
            if (!isEnabled(id)) {
                return CoordinateOffset.NONE;
            }

            OffsetStorage.Record stored = records.get(id);
            CoordinateOffset offset;
            if (stored != null && !stored.offset.isZero()
                    && current.isPersistenceEnabled() && !current.isRerollOnJoin()) {
                offset = stored.offset;
            } else {
                offset = roll();
                store(id, true, offset);
            }

            if (current.isDebug()) {
                plugin.getLogger().info("Session offset for " + id + ": x=" + offset.getX() + " z=" + offset.getZ());
            }
            return offset;
        });
    }

    public void endSession(UUID uuid) {
        if (uuid != null) {
            sessions.remove(uuid);
        }
    }

    public void endAllSessions() {
        sessions.clear();
    }

    /** Whether the given player currently has a shifted world on their client. */
    public boolean isActive(UUID uuid) {
        CoordinateOffset active = sessions.get(uuid);
        return active != null && !active.isZero();
    }

    /** The offset in force for a connected player, or {@link CoordinateOffset#NONE}. */
    public CoordinateOffset activeOffset(UUID uuid) {
        CoordinateOffset active = sessions.get(uuid);
        return active != null ? active : CoordinateOffset.NONE;
    }

    // ------------------------------------------------------------------ preferences

    public boolean isEnabled(UUID uuid) {
        OffsetStorage.Record record = records.get(uuid);
        return record != null ? record.enabled : config.isEnabledByDefault();
    }

    /**
     * Stores the preference. The change deliberately does not touch a live session: the client has
     * already cached a world at the old coordinates, and the server will not resend chunks it
     * believes the client already has, so the new setting is picked up on the next login.
     */
    public void setEnabled(UUID uuid, boolean enabled) {
        OffsetStorage.Record record = records.get(uuid);
        CoordinateOffset offset = record != null ? record.offset : CoordinateOffset.NONE;
        if (enabled && offset.isZero()) {
            offset = roll();
        }
        store(uuid, enabled, offset);
    }

    /** Rolls a new random offset for the player, keeping their enabled/disabled preference. */
    public CoordinateOffset reroll(UUID uuid) {
        CoordinateOffset offset = roll();
        store(uuid, isEnabled(uuid), offset);
        return offset;
    }

    /** The offset the player will get on their next login. */
    public CoordinateOffset pendingOffset(UUID uuid) {
        OffsetStorage.Record record = records.get(uuid);
        return record != null ? record.offset : CoordinateOffset.NONE;
    }

    private void store(UUID uuid, boolean enabled, CoordinateOffset offset) {
        records.put(uuid, new OffsetStorage.Record(enabled, offset));
        scheduleSave();
    }

    // ------------------------------------------------------------------ rolling

    /**
     * Picks a random chunk aligned offset inside the configured range, skipping any value whose
     * absolute size is below {@code offset.minimum-distance}.
     *
     * <p>The allowed values are counted arithmetically rather than sampled by rejection, so a
     * narrow range with a large minimum distance cannot spin.
     */
    private CoordinateOffset roll() {
        PrivacyCoordsConfig current = config;
        int chunkX = rollAxis(current);
        int chunkZ = rollAxis(current);
        return CoordinateOffset.ofChunks(chunkX, chunkZ);
    }

    private int rollAxis(PrivacyCoordsConfig current) {
        int size = CoordinateOffset.CHUNK_SIZE;
        // Round the bounds inwards so we never leave the configured block range.
        int lowest = Math.floorDiv(current.getOffsetMin() + size - 1, size);
        int highest = Math.floorDiv(current.getOffsetMax(), size);
        if (lowest > highest) {
            return 0;
        }

        int forbidden = Math.floorDiv(current.getMinimumDistance() + size - 1, size);
        if (forbidden <= 0) {
            return lowest + random.nextInt(highest - lowest + 1);
        }

        int negativeEnd = Math.min(highest, -forbidden);
        int positiveStart = Math.max(lowest, forbidden);
        int negativeCount = Math.max(0, negativeEnd - lowest + 1);
        int positiveCount = Math.max(0, highest - positiveStart + 1);
        int total = negativeCount + positiveCount;

        if (total <= 0) {
            // The minimum distance rules out the whole range; fall back to the raw range.
            return lowest + random.nextInt(highest - lowest + 1);
        }

        int pick = random.nextInt(total);
        return pick < negativeCount ? lowest + pick : positiveStart + (pick - negativeCount);
    }

    // ------------------------------------------------------------------ persistence

    private void scheduleSave() {
        if (!config.isPersistenceEnabled()) {
            return;
        }
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                saveScheduled.set(false);
                storage.save(snapshot());
            });
        } catch (IllegalStateException ex) {
            // The scheduler refuses new tasks while the server is shutting down; save inline.
            saveScheduled.set(false);
            storage.save(snapshot());
        }
    }

    /** Writes everything to disk immediately, on the calling thread. Used on plugin shutdown. */
    public void saveNow() {
        if (config.isPersistenceEnabled()) {
            storage.save(snapshot());
        }
    }

    private Map<UUID, OffsetStorage.Record> snapshot() {
        return new HashMap<>(records);
    }

    // ------------------------------------------------------------------ diagnostics

    private volatile long lastFailureLog;

    /**
     * Reports a packet that could not be translated. Rate limited to one message a minute so a
     * broken packet type cannot flood the console.
     */
    public void reportFailure(Object packetType, Throwable error) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLog < 60_000L && !config.isDebug()) {
            return;
        }
        lastFailureLog = now;
        plugin.getLogger().log(Level.WARNING,
                "Failed to translate coordinates for packet " + packetType + "; the client may briefly see "
                        + "untranslated positions. Please report this with your server version.", error);
    }
}
