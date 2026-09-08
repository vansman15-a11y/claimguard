package net.robmc.claimguard.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.claim.ClaimActions;

import java.util.function.Supplier;

/**
 * Client -> server: a button press in the claim menu. The server re-validates
 * ownership and cost before doing anything (see ClaimActions).
 */
public class ClaimActionPacket {

    public enum Action {
        SHOW_BORDER,
        UPGRADE,
        REMOVE,
        BIND,
        UNBIND
    }

    private final BlockPos corePos;
    private final Action action;

    public ClaimActionPacket(BlockPos corePos, Action action) {
        this.corePos = corePos;
        this.action = action;
    }

    public static void encode(ClaimActionPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.corePos);
        buf.writeEnum(packet.action);
    }

    public static ClaimActionPacket decode(FriendlyByteBuf buf) {
        return new ClaimActionPacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public static void handle(ClaimActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // Anti-cheat: these actions come from the beacon menu, so the player must
            // actually be next to the beacon - not firing packets from across the map.
            if (player.distanceToSqr(packet.corePos.getX() + 0.5, packet.corePos.getY() + 0.5, packet.corePos.getZ() + 0.5) > 64.0) {
                return;
            }
            switch (packet.action) {
                case SHOW_BORDER -> ClaimActions.showBorder(player, packet.corePos);
                case UPGRADE -> ClaimActions.tryUpgrade(player, packet.corePos);
                case REMOVE -> ClaimActions.remove(player, packet.corePos);
                case BIND -> ClaimActions.bind(player, packet.corePos);
                case UNBIND -> ClaimActions.unbind(player, packet.corePos);
            }
        });
        context.setPacketHandled(true);
    }
}
