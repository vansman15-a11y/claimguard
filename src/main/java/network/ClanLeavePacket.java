package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/** Client -> server: leave your clan (confirmed on the client first). */
public class ClanLeavePacket {

    public ClanLeavePacket() {
    }

    public static void encode(ClanLeavePacket packet, FriendlyByteBuf buf) {
    }

    public static ClanLeavePacket decode(FriendlyByteBuf buf) {
        return new ClanLeavePacket();
    }

    public static void handle(ClanLeavePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.leaveClan(player);
            }
        });
        context.setPacketHandled(true);
    }
}
