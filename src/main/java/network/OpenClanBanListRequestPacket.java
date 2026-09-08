package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/** Client -> server: "send me my clan's ban list" (the roster's Ban list button). */
public class OpenClanBanListRequestPacket {

    public OpenClanBanListRequestPacket() {
    }

    public static void encode(OpenClanBanListRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenClanBanListRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenClanBanListRequestPacket();
    }

    public static void handle(OpenClanBanListRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.openBanList(player);
            }
        });
        context.setPacketHandled(true);
    }
}
