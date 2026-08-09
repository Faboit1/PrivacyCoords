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
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.offset.CoordinateOffset;
import dev.faboit.privacycoords.offset.OffsetService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moves every position the server sends to a player by {@code +offset}, so the client - and the
 * F3 screen, and anything reading the client's memory for a stream overlay - only ever sees the
 * shifted world.
 *
 * <p>Which categories are translated is controlled by the {@code translate} section of the config;
 * the listener's own priority comes from {@code advanced.clientbound-priority} and defaults to
 * {@link PacketListenerPriority#HIGHEST}, so positions produced by other plugins are translated
 * too.
 */
public final class ClientboundOffsetListener extends PacketListenerAbstract {

    private final OffsetService service;

    public ClientboundOffsetListener(OffsetService service, PacketListenerPriority priority) {
        super(priority);
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
            PrivacyCoordsConfig config = service.getConfig();
            if (translate(event, (PacketType.Play.Server) type, config, config.translation(),
                    offset.getX(), offset.getZ(), offset.getChunkX(), offset.getChunkZ())) {
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
                              PrivacyCoordsConfig config, PrivacyCoordsConfig.Translation rules,
                              int dx, int dz, int chunkDx, int chunkDz) {
        switch (type) {

            // ---------------------------------------------------------- chunks

            case CHUNK_DATA:
                return rules.chunks && shiftChunkData(event, config, chunkDx, chunkDz);

            case UNLOAD_CHUNK: {
                if (!rules.chunks) {
                    return false;
                }
                WrapperPlayServerUnloadChunk packet = new WrapperPlayServerUnloadChunk(event);
                packet.setChunkX(packet.getChunkX() + chunkDx);
                packet.setChunkZ(packet.getChunkZ() + chunkDz);
                return true;
            }

            case UPDATE_VIEW_POSITION: {
                if (!rules.chunks) {
                    return false;
                }
                WrapperPlayServerUpdateViewPosition packet = new WrapperPlayServerUpdateViewPosition(event);
                packet.setChunkX(packet.getChunkX() + chunkDx);
                packet.setChunkZ(packet.getChunkZ() + chunkDz);
                return true;
            }

            case UPDATE_LIGHT: {
                if (!rules.chunks) {
                    return false;
                }
                WrapperPlayServerUpdateLight packet = new WrapperPlayServerUpdateLight(event);
                packet.setX(packet.getX() + chunkDx);
                packet.setZ(packet.getZ() + chunkDz);
                return true;
            }

            case CHUNK_BIOMES: {
                if (!rules.chunks) {
                    return false;
                }
                WrapperPlayServerChunkBiomes packet = new WrapperPlayServerChunkBiomes(event);
                Map<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> chunks = packet.getChunks();
                Map<Vector2i, WrapperPlayServerChunkBiomes.ChunkBiomeData> moved = new LinkedHashMap<>();
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
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case MULTI_BLOCK_CHANGE: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
                // The Y component of this vector is the chunk section index, not a block height.
                Vector3i section = packet.getChunkPosition();
                packet.setChunkPosition(new Vector3i(section.getX() + chunkDx, section.getY(), section.getZ() + chunkDz));
                return true;
            }

            case BLOCK_ACTION: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerBlockAction packet = new WrapperPlayServerBlockAction(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case BLOCK_BREAK_ANIMATION: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case BLOCK_ENTITY_DATA: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case ACKNOWLEDGE_PLAYER_DIGGING: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerAcknowledgePlayerDigging packet = new WrapperPlayServerAcknowledgePlayerDigging(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case OPEN_SIGN_EDITOR: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerOpenSignEditor packet = new WrapperPlayServerOpenSignEditor(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case USE_BED: {
                if (!rules.blocks) {
                    return false;
                }
                WrapperPlayServerUseBed packet = new WrapperPlayServerUseBed(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- the player

            case PLAYER_POSITION_AND_LOOK: {
                if (!rules.player) {
                    return false;
                }
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
                if (!rules.player) {
                    return false;
                }
                WrapperPlayServerVehicleMove packet = new WrapperPlayServerVehicleMove(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_POSITION: {
                // The world spawn, which is where the compass points.
                if (!rules.spawnPosition) {
                    return false;
                }
                WrapperPlayServerSpawnPosition packet = new WrapperPlayServerSpawnPosition(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case JOIN_GAME: {
                if (!rules.lastDeathPosition) {
                    return false;
                }
                WrapperPlayServerJoinGame packet = new WrapperPlayServerJoinGame(event);
                if (packet.getLastDeathPosition() == null) {
                    return false;
                }
                packet.setLastDeathPosition(Shift.worldBlock(packet.getLastDeathPosition(), dx, dz));
                return true;
            }

            case RESPAWN: {
                if (!rules.lastDeathPosition) {
                    return false;
                }
                WrapperPlayServerRespawn packet = new WrapperPlayServerRespawn(event);
                if (packet.getLastDeathPosition() == null) {
                    return false;
                }
                packet.setLastDeathPosition(Shift.worldBlock(packet.getLastDeathPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- entities

            case SPAWN_ENTITY: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_LIVING_ENTITY: {
                if (!rules.entities && !rules.entityMetadata) {
                    return false;
                }
                WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(event);
                if (rules.entities) {
                    packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                }
                if (rules.entityMetadata) {
                    Shift.metadata(packet.getEntityMetadata(), dx, dz);
                }
                return true;
            }

            case SPAWN_PLAYER: {
                if (!rules.entities && !rules.entityMetadata) {
                    return false;
                }
                WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
                if (rules.entities) {
                    packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                }
                if (rules.entityMetadata) {
                    Shift.metadata(packet.getEntityMetadata(), dx, dz);
                }
                return true;
            }

            case SPAWN_PAINTING: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerSpawnPainting packet = new WrapperPlayServerSpawnPainting(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case SPAWN_EXPERIENCE_ORB: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerSpawnExperienceOrb packet = new WrapperPlayServerSpawnExperienceOrb(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case SPAWN_WEATHER_ENTITY: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerSpawnWeatherEntity packet = new WrapperPlayServerSpawnWeatherEntity(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case ENTITY_TELEPORT: {
                if (!rules.entities) {
                    return false;
                }
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
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
                packet.getValues().setPosition(Shift.position(packet.getValues().getPosition(), dx, dz));
                return true;
            }

            case MOVE_MINECART: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerMoveMinecart packet = new WrapperPlayServerMoveMinecart(event);
                for (WrapperPlayServerMoveMinecart.MinecartStep step : packet.getLerpSteps()) {
                    step.setPosition(Shift.position(step.getPosition(), dx, dz));
                }
                return true;
            }

            case DAMAGE_EVENT: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerDamageEvent packet = new WrapperPlayServerDamageEvent(event);
                if (packet.getSourcePosition() == null) {
                    return false;
                }
                packet.setSourcePosition(Shift.position(packet.getSourcePosition(), dx, dz));
                return true;
            }

            case FACE_PLAYER: {
                if (!rules.entities) {
                    return false;
                }
                WrapperPlayServerFacePlayer packet = new WrapperPlayServerFacePlayer(event);
                packet.setTargetPosition(Shift.position(packet.getTargetPosition(), dx, dz));
                return true;
            }

            case ENTITY_METADATA: {
                if (!rules.entityMetadata) {
                    return false;
                }
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                Shift.metadata(packet.getEntityMetadata(), dx, dz);
                return true;
            }

            // ---------------------------------------------------------- effects

            case EFFECT: {
                if (!rules.effects) {
                    return false;
                }
                WrapperPlayServerEffect packet = new WrapperPlayServerEffect(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case PARTICLE: {
                // getOffset() is the random spread around the position, not a world position.
                if (!rules.particles) {
                    return false;
                }
                WrapperPlayServerParticle packet = new WrapperPlayServerParticle(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case SOUND_EFFECT: {
                if (!rules.sounds) {
                    return false;
                }
                WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case EXPLOSION: {
                // The affected block records are offsets relative to the centre, so only the
                // centre itself moves.
                if (!rules.explosions) {
                    return false;
                }
                WrapperPlayServerExplosion packet = new WrapperPlayServerExplosion(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- world border

            case WORLD_BORDER_CENTER: {
                if (!rules.worldBorder) {
                    return false;
                }
                WrapperPlayServerWorldBorderCenter packet = new WrapperPlayServerWorldBorderCenter(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case INITIALIZE_WORLD_BORDER: {
                if (!rules.worldBorder) {
                    return false;
                }
                WrapperPlayServerInitializeWorldBorder packet = new WrapperPlayServerInitializeWorldBorder(event);
                packet.setX(packet.getX() + dx);
                packet.setZ(packet.getZ() + dz);
                return true;
            }

            case WORLD_BORDER: {
                // The pre-1.17 combined packet: only two of its actions carry a centre.
                if (!rules.worldBorder) {
                    return false;
                }
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
                if (!rules.locatorBar) {
                    return false;
                }
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
     * with the chunk X and Z as plain big endian ints, so they are patched in place. That fast
     * path can be switched off with {@code advanced.chunk-data-fast-path}.
     *
     * <p>If another plugin has already decoded this packet, its wrapper - not our bytes - is what
     * gets written back out, so in that case we edit the decoded column instead.
     */
    private boolean shiftChunkData(PacketSendEvent event, PrivacyCoordsConfig config, int chunkDx, int chunkDz) {
        if (config.isChunkDataFastPath() && event.getLastUsedWrapper() == null) {
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
