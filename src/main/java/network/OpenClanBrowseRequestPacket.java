package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/** Client -> server: "send me the clan directory" (the roster's Browse button). */
public class OpenClanBrowseRequestPacket {

    public OpenClanBrowseRequestPacket() {
    }

    public static void encode(OpenClanBrowseRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenClanBrowseRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenClanBrowseRequestPacket();
    }

    public static void handle(OpenClanBrowseRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.openBrowse(player);
            }
        });
        context.setPacketHandled(true);
    }
}
