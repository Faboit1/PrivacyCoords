package dev.faboit.privacycoords.offset;

/**
 * An immutable horizontal shift between the world the server works with and the world a single
 * player's client is shown.
 *
 * <p>The convention used everywhere in this plugin is:
 * <pre>client = server + offset</pre>
 * so clientbound packets add the offset and serverbound packets subtract it again.
 *
 * <p>Both components are always a multiple of {@link #CHUNK_SIZE}. A shift that is not chunk
 * aligned cannot be expressed in the packets that carry chunk coordinates (chunk data, light,
 * multi block change, ...), which would tear the client's world apart at every chunk border.
 */
public final class CoordinateOffset {

    public static final int CHUNK_SIZE = 16;

    /** No shift at all - used for players who have the feature turned off. */
    public static final CoordinateOffset NONE = new CoordinateOffset(0, 0);

    private final int x;
    private final int z;

    private CoordinateOffset(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /**
     * Creates an offset, rounding both components towards zero to a whole number of chunks.
     */
    public static CoordinateOffset ofBlocks(int x, int z) {
        int alignedX = alignToChunk(x);
        int alignedZ = alignToChunk(z);
        if (alignedX == 0 && alignedZ == 0) {
            return NONE;
        }
        return new CoordinateOffset(alignedX, alignedZ);
    }

    /**
     * Creates an offset from chunk coordinates.
     */
    public static CoordinateOffset ofChunks(int chunkX, int chunkZ) {
        return ofBlocks(chunkX * CHUNK_SIZE, chunkZ * CHUNK_SIZE);
    }

    private static int alignToChunk(int blocks) {
        return (blocks / CHUNK_SIZE) * CHUNK_SIZE;
    }

    /** The shift applied to the X axis, in blocks. */
    public int getX() {
        return x;
    }

    /** The shift applied to the Z axis, in blocks. */
    public int getZ() {
        return z;
    }

    /** The shift applied to the X axis, in chunks. */
    public int getChunkX() {
        return x / CHUNK_SIZE;
    }

    /** The shift applied to the Z axis, in chunks. */
    public int getChunkZ() {
        return z / CHUNK_SIZE;
    }

    public boolean isZero() {
        return x == 0 && z == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CoordinateOffset)) {
            return false;
        }
        CoordinateOffset that = (CoordinateOffset) other;
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return 31 * x + z;
    }

    @Override
    public String toString() {
        return "CoordinateOffset{x=" + x + ", z=" + z + "}";
    }
}
