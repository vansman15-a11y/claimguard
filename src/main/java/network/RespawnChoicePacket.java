package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.bind.BindManager;

import java.util.function.Supplier;

/**
 * Client -> server: the player's respawn choice, sent just before the respawn
 * packet. {@code atBind} true = revive at the bindstone, false = the bed.
 */
public class RespawnChoicePacket {

    private final boolean atBind;

    public RespawnChoicePacket(boolean atBind) {
        this.atBind = atBind;
    }

    public static void encode(RespawnChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.atBind);
    }

    public static RespawnChoicePacket decode(FriendlyByteBuf buf) {
        return new RespawnChoicePacket(buf.readBoolean());
    }

    public static void handle(RespawnChoicePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BindManager.get(player.server).setRespawnChoice(player.getUUID(), packet.atBind);
            }
        });
        context.setPacketHandled(true);
    }
}
