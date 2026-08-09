package dev.faboit.privacycoords.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import dev.faboit.privacycoords.config.PrivacyCoordsConfig;
import dev.faboit.privacycoords.offset.CoordinateOffset;
import dev.faboit.privacycoords.offset.OffsetService;

/**
 * Undoes the shift on everything the client sends back, so the server - and every other plugin,
 * anti-cheat included - keeps seeing the player's real position.
 *
 * <p>Which categories are translated is controlled by the {@code translate} section of the config;
 * the listener's own priority comes from {@code advanced.serverbound-priority} and defaults to
 * {@link PacketListenerPriority#LOWEST}, so it happens before anything else looks at the packet.
 */
public final class ServerboundOffsetListener extends PacketListenerAbstract {

    private final OffsetService service;

    public ServerboundOffsetListener(OffsetService service, PacketListenerPriority priority) {
        super(priority);
        this.service = service;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (!(type instanceof PacketType.Play.Client)) {
            return;
        }
        CoordinateOffset offset = service.sessionOffset(event.getUser());
        if (offset.isZero()) {
            return;
        }

        try {
            if (translate(event, (PacketType.Play.Client) type, service.getConfig().translation(),
                    -offset.getX(), -offset.getZ())) {
                event.markForReEncode(true);
            }
        } catch (Exception ex) {
            service.reportFailure(type, ex);
        }
    }

    private boolean translate(PacketReceiveEvent event, PacketType.Play.Client type,
                              PrivacyCoordsConfig.Translation rules, int dx, int dz) {
        switch (type) {

            // ---------------------------------------------------------- movement

            case PLAYER_POSITION: {
                if (!rules.clientMovement) {
                    return false;
                }
                WrapperPlayClientPlayerPosition packet = new WrapperPlayClientPlayerPosition(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case PLAYER_POSITION_AND_ROTATION: {
                if (!rules.clientMovement) {
                    return false;
                }
                WrapperPlayClientPlayerPositionAndRotation packet = new WrapperPlayClientPlayerPositionAndRotation(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case VEHICLE_MOVE: {
                if (!rules.clientMovement) {
                    return false;
                }
                WrapperPlayClientVehicleMove packet = new WrapperPlayClientVehicleMove(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- interacting with blocks

            case PLAYER_BLOCK_PLACEMENT: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                // The cursor position is relative to the block face, so it stays as it is.
                WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case PLAYER_DIGGING: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case UPDATE_SIGN: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case QUERY_BLOCK_NBT: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientQueryBlockNBT packet = new WrapperPlayClientQueryBlockNBT(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case PICK_ITEM_FROM_BLOCK: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientPickItemFromBlock packet = new WrapperPlayClientPickItemFromBlock(event);
                packet.setBlockPos(Shift.block(packet.getBlockPos(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- editing special blocks

            case UPDATE_COMMAND_BLOCK: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientUpdateCommandBlock packet = new WrapperPlayClientUpdateCommandBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case UPDATE_JIGSAW_BLOCK: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientUpdateJigsawBlock packet = new WrapperPlayClientUpdateJigsawBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case UPDATE_STRUCTURE_BLOCK: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientSetStructureBlock packet = new WrapperPlayClientSetStructureBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case GENERATE_STRUCTURE: {
                if (!rules.clientBlockInteraction) {
                    return false;
                }
                WrapperPlayClientGenerateStructure packet = new WrapperPlayClientGenerateStructure(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            default:
                // Everything else the client sends is either relative (the hit vector in
                // "interact entity") or carries no position at all.
                return false;
        }
    }
}
