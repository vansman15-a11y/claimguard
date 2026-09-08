package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: unban a player from the clan (leader only, re-checked). */
public class ClanUnbanPacket {

    private final UUID target;

    public ClanUnbanPacket(UUID target) {
        this.target = target;
    }

    public static void encode(ClanUnbanPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.target);
    }

    public static ClanUnbanPacket decode(FriendlyByteBuf buf) {
        return new ClanUnbanPacket(buf.readUUID());
    }

    public static void handle(ClanUnbanPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.unban(player, packet.target);
            }
        });
        context.setPacketHandled(true);
    }
}
