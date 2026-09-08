package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/** Client -> server: set the clan MOTD (leader/officer only, re-checked). */
public class SetClanMotdPacket {

    private final String motd;

    public SetClanMotdPacket(String motd) {
        this.motd = motd;
    }

    public static void encode(SetClanMotdPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.motd, 512);
    }

    public static SetClanMotdPacket decode(FriendlyByteBuf buf) {
        return new SetClanMotdPacket(buf.readUtf(512));
    }

    public static void handle(SetClanMotdPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.setMotd(player, packet.motd);
            }
        });
        context.setPacketHandled(true);
    }
}
