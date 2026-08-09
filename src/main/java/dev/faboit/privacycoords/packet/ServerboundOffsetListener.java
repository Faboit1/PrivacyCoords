package dev.faboit.privacycoords.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import dev.faboit.privacycoords.offset.CoordinateOffset;
import dev.faboit.privacycoords.offset.OffsetService;

/**
 * Undoes the shift on everything the client sends back, so the server - and every other plugin,
 * anti-cheat included - keeps seeing the player's real position.
 *
 * <p>Runs at {@link PacketListenerPriority#LOWEST} so that it happens before anything else looks
 * at the packet.
 */
public final class ServerboundOffsetListener extends PacketListenerAbstract {

    private final OffsetService service;

    public ServerboundOffsetListener(OffsetService service) {
        super(PacketListenerPriority.LOWEST);
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
            if (translate(event, (PacketType.Play.Client) type, -offset.getX(), -offset.getZ())) {
                event.markForReEncode(true);
            }
        } catch (Exception ex) {
            service.reportFailure(type, ex);
        }
    }

    private boolean translate(PacketReceiveEvent event, PacketType.Play.Client type, int dx, int dz) {
        switch (type) {

            // ---------------------------------------------------------- movement

            case PLAYER_POSITION: {
                WrapperPlayClientPlayerPosition packet = new WrapperPlayClientPlayerPosition(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case PLAYER_POSITION_AND_ROTATION: {
                WrapperPlayClientPlayerPositionAndRotation packet = new WrapperPlayClientPlayerPositionAndRotation(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            case VEHICLE_MOVE: {
                WrapperPlayClientVehicleMove packet = new WrapperPlayClientVehicleMove(event);
                packet.setPosition(Shift.position(packet.getPosition(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- interacting with blocks

            case PLAYER_BLOCK_PLACEMENT: {
                // The cursor position is relative to the block face, so it stays as it is.
                WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case PLAYER_DIGGING: {
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case UPDATE_SIGN: {
                WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case QUERY_BLOCK_NBT: {
                WrapperPlayClientQueryBlockNBT packet = new WrapperPlayClientQueryBlockNBT(event);
                packet.setBlockPosition(Shift.block(packet.getBlockPosition(), dx, dz));
                return true;
            }

            case PICK_ITEM_FROM_BLOCK: {
                WrapperPlayClientPickItemFromBlock packet = new WrapperPlayClientPickItemFromBlock(event);
                packet.setBlockPos(Shift.block(packet.getBlockPos(), dx, dz));
                return true;
            }

            // ---------------------------------------------------------- editing special blocks

            case UPDATE_COMMAND_BLOCK: {
                WrapperPlayClientUpdateCommandBlock packet = new WrapperPlayClientUpdateCommandBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case UPDATE_JIGSAW_BLOCK: {
                WrapperPlayClientUpdateJigsawBlock packet = new WrapperPlayClientUpdateJigsawBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case UPDATE_STRUCTURE_BLOCK: {
                WrapperPlayClientSetStructureBlock packet = new WrapperPlayClientSetStructureBlock(event);
                packet.setPosition(Shift.block(packet.getPosition(), dx, dz));
                return true;
            }

            case GENERATE_STRUCTURE: {
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
