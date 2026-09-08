package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.claimguard.clan.ClanRelation;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: set (or clear, ordinal -1) how our clan regards another. */
public class SetClanRelationPacket {

    private final UUID targetClanId;
    private final int relationOrdinal;

    public SetClanRelationPacket(UUID targetClanId, int relationOrdinal) {
        this.targetClanId = targetClanId;
        this.relationOrdinal = relationOrdinal;
    }

    public static void encode(SetClanRelationPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.targetClanId);
        buf.writeVarInt(packet.relationOrdinal + 1);
    }

    public static SetClanRelationPacket decode(FriendlyByteBuf buf) {
        return new SetClanRelationPacket(buf.readUUID(), buf.readVarInt() - 1);
    }

    public static void handle(SetClanRelationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.setRelation(player, packet.targetClanId, ClanRelation.byIndexOrNull(packet.relationOrdinal));
            }
        });
        context.setPacketHandled(true);
    }
}
