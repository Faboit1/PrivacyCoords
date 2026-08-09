package dev.faboit.privacycoords.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.world.WorldBlockPosition;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.List;
import java.util.Optional;

/**
 * The primitive operations both directions are built from. The caller passes an already signed
 * delta: clientbound listeners pass {@code +offset}, serverbound listeners pass {@code -offset}.
 *
 * <p>Only X and Z are ever touched. Y is left alone on purpose - the client is told the world's
 * height range once, and moving chunk sections outside of it breaks lighting and rendering.
 */
final class Shift {

    private Shift() {
    }

    static Vector3d position(Vector3d position, int deltaX, int deltaZ) {
        if (position == null) {
            return null;
        }
        return new Vector3d(position.getX() + deltaX, position.getY(), position.getZ() + deltaZ);
    }

    static Vector3i block(Vector3i position, int deltaX, int deltaZ) {
        if (position == null) {
            return null;
        }
        return new Vector3i(position.getX() + deltaX, position.getY(), position.getZ() + deltaZ);
    }

    static WorldBlockPosition worldBlock(WorldBlockPosition position, int deltaX, int deltaZ) {
        if (position == null) {
            return null;
        }
        return new WorldBlockPosition(position.getWorld(), block(position.getBlockPosition(), deltaX, deltaZ));
    }

    /**
     * Entity metadata can carry block positions - a sleeping player's bed, a mob's home or hive.
     * Those are absolute world positions and have to move with everything else.
     */
    @SuppressWarnings("unchecked")
    static void metadata(List<EntityData<?>> metadata, int deltaX, int deltaZ) {
        if (metadata == null) {
            return;
        }
        for (EntityData<?> entry : metadata) {
            EntityDataType<?> type = entry.getType();
            if (type == EntityDataTypes.BLOCK_POSITION) {
                EntityData<Vector3i> typed = (EntityData<Vector3i>) entry;
                typed.setValue(block(typed.getValue(), deltaX, deltaZ));
            } else if (type == EntityDataTypes.OPTIONAL_BLOCK_POSITION) {
                EntityData<Optional<Vector3i>> typed = (EntityData<Optional<Vector3i>>) entry;
                Optional<Vector3i> value = typed.getValue();
                if (value != null && value.isPresent()) {
                    typed.setValue(Optional.of(block(value.get(), deltaX, deltaZ)));
                }
            }
        }
    }
}
