package dev.faboit.privacycoords.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.HeightmapType;
import com.github.retrooper.packetevents.protocol.world.waypoint.ChunkWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint;
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointInfo;
import com.github.retrooper.packetevents.util.Vector2i;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import dev.faboit.privacycoords.offset.CoordinateOffset;
import dev.faboit.privacycoords.offset.OffsetService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moves every position the server sends to a player by {@code +offset}, so the client - and the
 * F3 screen, and anything reading the client's memory for a stream overlay - only ever sees the
 * shifted world.
 *
 * <p>Runs at {@link PacketListenerPriority#HIGHEST} so that positions produced by other plugins
 * are translated too.
 */
public final class ClientboundOffsetListener extends PacketListenerAbstract {

    private final OffsetService service;

    public ClientboundOffsetListener(OffsetService service) {
        super(PacketListenerPriority.HIGHEST);
        this.service = service;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (!(type instanceof PacketType.Play.Server)) {
            return;
        }
        CoordinateOffset offset = service.sessionOffset(event.getUser());
        if (offset.isZero()) {
            return;
        }

        try {
            if (translate(event, (PacketType.Play.Server) type, offset.getX(), offset.getZ(),
                    offset.getChunkX(), offset.getChunkZ())) {
                event.markForReEncode(true);
            }
        } catch (Exception ex) {
            service.reportFailure(type, ex);
        }
    }

    /**
     * @return true when a wrapper was modified and the packet has to be re-encoded
     */
    private boolean translate(PacketSendEvent event, PacketType.Play.Server type,
                              int dx, int dz, int chunkDx, int chunkDz) {
        switch (type) {

            // ---------------------------------------------------------- chunks

            case CHUNK_DATA:
                return shiftChunkData(event, chunkDx, chunkDz);

            case UNLOAD_CHUNK: {
                WrapperPlayServerUnloadChunk packet = new WrapperPlayServerUnloadChunk(event);
                packet.setChunkX(packet.getChunkX() + chunkDx);
                packet.setChunkZ(packet.getChunkZ() + chunkDz);
                return true;
            }

            case UPDATE_VIEW_POSITION: {
                WrapperPlayServerUpdateViewPosition packet = new WrapperPlayServerUpdateViewPosition(event);
                packet.setChunkX(packet.getChunkX() + chunkDx);
                packet.setChunkZ(packet.getChunkZ() + chunkDz);
                return true;
            }

            case UPDATE_LIGHT: {
                WrapperPlayServerUpdateLight packet = new WrapperPlayServerUpdateLight(event);
                packet.setX(packet.getX() + chunkDx);
                packet.setZ(packet.getZ() + chunkDz);
                return true;
            }

            case CHUNK_BIOMES: {
                WrapperPlayServerChunkBiomes packet = new WrapperPlayServerChunkBiomes(event);
                Map<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> chunks = packet.getChunks();
                Map<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> moved = new LinkedHashMap<>(chunks.size());
                for (Map.Entry<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> entry : chunks.entrySet()) {
                    Vector2i key = entry.getKey();
                    moved.put(new Vector2i(key.getX() + chunkDx, key.getZ() + chunkDz), entry.getValue());
                }
                chunks.clear();
                chunks.putAll(moved);
                return true;
            }

            // ---------------------------------------------------------- blocks

            case BLOCK_CHANGE: {
                WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case MULTI_BLOCK_CHANGE: {
                WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
                // The Y component of this vector is the chunk section index, not a block height.
                Vector3i section = packet.getChunkPosition();
                packet.setChunkPosition(new Vector3i(section.getX() + chunkDx, section.getY(), section.getZ() + chunkDz));
                return true;
            }

            case BLOCK_ACTION: {
                WrapperPlayServerBlockAction packet = new WrapperPlayServerBlockAction(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case BLOCK_BREAK_ANIMATION: {
                WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case BLOCK_ENTITY_DATA: {
                WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case ACKNOWLEDGE_PLAYER_DIGGING: {
                WrapperPlayServerAcknowledgePlayerDigging packet = new WrapperPlayServerAcknowledgePlayerDigging(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case OPEN_SIGN_EDITOR: {
                WrapperPlayServerOpenSignEditor packet = new WrapperPlayServerOpenSignEditor(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case USE_BED: {
                WrapperPlayServerUseBed packet = new WrapperPlayServerUseBed(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- the player

            case PLAYER_POSITION_AND_LOOK: {
                WrapperPlayServerPlayerPositionAndLook packet = new WrapperPlayServerPlayerPositionAndLook(event);
                // A relative teleport carries a delta, which must not be shifted.
                if (!packet.isRelativeFlag(RelativeFlag.X)) {
                    packet.setX(packet.getX() + dx);
                }
                if (!packet.isRelativeFlag(RelativeFlag.Z)) {
                    packet.setZ(packet.getZ() + dz);
                }
                return true;
            }

            case VEHICLE_MOVE: {
                WrapperPlayServerVehicleMove packet = new WrapperPlayServerVehicleMove(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_POSITION: {
                // The world spawn, which is where the compass points.
                WrapperPlayServerSpawnPosition packet = new WrapperPlayServerSpawnPosition(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case JOIN_GAME: {
                WrapperPlayServerJoinGame packet = new WrapperPlayServerJoinGame(event);
                if (packet.getLastDeathPosition() == null) {
                    return false;
                }
                packet.setLastDeathPosition(Shift.worldBlock(packet.getLastDeathPosition(), dx, dz));
                return true;
            }

            case RESPAWN: {
                WrapperPlayServerRespawn packet = new WrapperPlayServerRespawn(event);
                if (packet.getLastDeathPosition() == null) {
                    return false;
                }
                packet.setLastDeathPosition(Shift.worldBlock(packet.getLastDeathPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- entities

            case SPAWN_ENTITY: {
                WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_LIVING_ENTITY: {
                WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                Shift.metadata(packet.getEntityMetadata(), dx, dz);
                return true;
            }

            case SPAWN_PLAYER: {
                WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                Shift.metadata(packet.getEntityMetadata(), dx, dz);
                return true;
            }

            case SPAWN_PAINTING: {
                WrapperPlayServerSpawnPainting packet = new WrapperPlayServerSpawnPainting(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_EXPERIENCE_ORB: {
                WrapperPlayServerSpawnExperienceOrb packet = new WrapperPlayServerSpawnExperienceOrb(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case SPAWN_WEATHER_ENTITY: {
                WrapperPlayServerSpawnWeatherEntity packet = new WrapperPlayServerSpawnWeatherEntity(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case ENTITY_TELEPORT: {
                WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
                RelativeFlag flags = packet.getRelativeFlags();
                Vector3d position = packet.getPosition();
                double x = position.getX();
                double z = position.getZ();
                if (flags == null || !RelativeFlag.X.has(flags.getFullMask())) {
                    x += dx;
                }
                if (flags == null || !RelativeFlag.Z.has(flags.getFullMask())) {
                    z += dz;
                }
                packet.setPosition(new Vector3d(x, position.getY(), z));
                return true;
            }

            case ENTITY_POSITION_SYNC: {
                WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
                packet.getValues().setPosition(Shift.position(packet.getValues().getPosition(), dx, dz));
                return true;
            }

            case MOVE_MINECART: {
                WrapperPlayServerMoveMinecart packet = new WrapperPlayServerMoveMinecart(event);
                for (WrapperPlayServerMoveMinecart.MinecartStep step : packet.getLerpSteps()) {
                    step.setPosition(Shift.position(step.getPosition(), dx, dz));
                }
                return true;
            }

            case ENTITY_METADATA: {
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                Shift.metadata(packet.getEntityMetadata(), dx, dz);
                return true;
            }

            case DAMAGE_EVENT: {
                WrapperPlayServerDamageEvent packet = new WrapperPlayServerDamageEvent(event);
                if (packet.getSourcePosition() == null) {
                    return false;
                }
                packet.setSourcePosition(Shift.position(packet.getSourcePosition(), dx, dz));
                return true;
            }

            case FACE_PLAYER: {
                WrapperPlayServerFacePlayer packet = new WrapperPlayServerFacePlayer(event);
                packet.setTargetPosition(Shift.position(packet.getTargetPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- effects

            case EFFECT: {
                WrapperPlayServerEffect packet = new WrapperPlayServerEffect(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case PARTICLE: {
                // getOffset() is the random spread around the position, not a world position.
                WrapperPlayServerParticle packet = new WrapperPlayServerParticle(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SOUND_EFFECT: {
                WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case EXPLOSION: {
                // The affected block records are offsets relative to the centre, so only the
                // centre itself moves.
                WrapperPlayServerExplosion packet = new WrapperPlayServerExplosion(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- world border

            case WORLD_BORDER_CENTER: {
                WrapperPlayServerWorldBorderCenter packet = new WrapperPlayServerWorldBorderCenter(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case INITIALIZE_WORLD_BORDER: {
                WrapperPlayServerInitializeWorldBorder packet = new WrapperPlayServerInitializeWorldBorder(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case WORLD_BORDER: {
                // The pre-1.17 combined packet: only two of its actions carry a centre.
                WrapperPlayServerWorldBorder packet = new WrapperPlayServerWorldBorder(event);
                WrapperPlayServerWorldBorder.WorldBorderAction action = packet.getAction();
                if (action != WrapperPlayServerWorldBorder.WorldBorderAction.SET_CENTER
                        && action != WrapperPlayServerWorldBorder.WorldBorderAction.INITIALIZE) {
                    return false;
                }
                packet.setCenterX(packet.getCenterX() + dx);
                packet.setCenterZ(packet.getCenterZ() + dz);
                return true;
            }

            // ---------------------------------------------------------- locator bar

            case WAYPOINT: {
                WrapperPlayServerWaypoint packet = new WrapperPlayServerWaypoint(event);
                TrackedWaypoint waypoint = packet.getWaypoint();
                WaypointInfo info = waypoint.getInfo();
                WaypointInfo moved;
                if (info instanceof Vec3iWaypointInfo) {
                    moved = new Vec3iWaypointInfo(Shift.block(((Vec3iWaypointInfo) info).getPosition(), dx, dz));
                } else if (info instanceof ChunkWaypointInfo) {
                    ChunkWaypointInfo chunk = (ChunkWaypointInfo) info;
                    moved = new ChunkWaypointInfo(chunk.getChunkX() + chunkDx, chunk.getChunkZ() + chunkDz);
                } else {
                    // An azimuth-only or empty waypoint carries no position.
                    return false;
                }
                packet.setWaypoint(new TrackedWaypoint(waypoint.getIdentifier(), waypoint.getIcon(), moved));
                return true;
            }

            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ chunk data

    /**
     * Chunk data is by far the most frequent packet carrying a position, and decoding a whole
     * column just to change two numbers is wasteful: every version of the packet since 1.7 starts
     * with the chunk X and Z as plain big endian ints, so they are patched in place.
     *
     * <p>If another plugin has already decoded this packet, its wrapper - not our bytes - is what
     * gets written back out, so in that case we fall back to editing the decoded column.
     */
    private boolean shiftChunkData(PacketSendEvent event, int chunkDx, int chunkDz) {
        if (event.getLastUsedWrapper() == null) {
            Object buffer = event.getByteBuf();
            int readerIndex = ByteBufHelper.readerIndex(buffer);
            if (ByteBufHelper.readableBytes(buffer) < Integer.BYTES * 2) {
                return false;
            }

            int chunkX = ByteBufHelper.readInt(buffer);
            int chunkZ = ByteBufHelper.readInt(buffer);
            ByteBufHelper.readerIndex(buffer, readerIndex);

            int writerIndex = ByteBufHelper.writerIndex(buffer);
            ByteBufHelper.writerIndex(buffer, readerIndex);
            ByteBufHelper.writeInt(buffer, chunkX + chunkDx);
            ByteBufHelper.writeInt(buffer, chunkZ + chunkDz);
            ByteBufHelper.writerIndex(buffer, writerIndex);
            return false;
        }

        WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
        packet.setColumn(moveColumn(packet.getColumn(), chunkDx, chunkDz));
        return true;
    }

    /** {@link Column} is immutable, so a shifted copy has to be built by hand. */
    private static Column moveColumn(Column column, int chunkDx, int chunkDz) {
        int x = column.getX() + chunkDx;
        int z = column.getZ() + chunkDz;
        boolean full = column.isFullChunk();

        if (column.hasBiomeData()) {
            int[] biomeInts = column.getBiomeDataInts();
            if (biomeInts != null) {
                return column.hasHeightMaps()
                        ? new Column(x, z, full, column.getChunks(), column.getTileEntities(),
                        column.getHeightMaps(), biomeInts)
                        : new Column(x, z, full, column.getChunks(), column.getTileEntities(), biomeInts);
            }
            byte[] biomeBytes = column.getBiomeDataBytes();
            return column.hasHeightMaps()
                    ? new Column(x, z, full, column.getChunks(), column.getTileEntities(),
                    column.getHeightMaps(), biomeBytes)
                    : new Column(x, z, full, column.getChunks(), column.getTileEntities(), biomeBytes);
        }

        if (!column.hasHeightMaps()) {
            return new Column(x, z, full, column.getChunks(), column.getTileEntities());
        }
        Map<HeightmapType, long[]> heightmaps = column.getHeightmaps();
        if (heightmaps.isEmpty()) {
            return new Column(x, z, full, column.getChunks(), column.getTileEntities(), new NBTCompound());
        }
        return new Column(x, z, full, column.getChunks(), column.getTileEntities(), heightmaps);
    }
}
