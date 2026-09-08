package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/** Client -> server: invite a player (by name) from the roster's Invite screen. */
public class ClanInvitePacket {

    private final String targetName;

    public ClanInvitePacket(String targetName) {
        this.targetName = targetName;
    }

    public static void encode(ClanInvitePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.targetName, 32);
    }

    public static ClanInvitePacket decode(FriendlyByteBuf buf) {
        return new ClanInvitePacket(buf.readUtf(32));
    }

    public static void handle(ClanInvitePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer inviter = context.getSender();
            if (inviter == null) {
                return;
            }
            ServerPlayer target = inviter.server.getPlayerList().getPlayerByName(packet.targetName);
            if (target == null) {
                inviter.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "No online player called \"" + packet.targetName + "\"."), true);
                return;
            }
            ClanActions.invitePlayer(inviter, target);
        });
        context.setPacketHandled(true);
    }
}
