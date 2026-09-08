package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.siege.SiegeActions;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: start a siege on this (enemy) clan from the browse screen. */
public class StartSiegePacket {

    private final UUID targetClanId;

    public StartSiegePacket(UUID targetClanId) {
        this.targetClanId = targetClanId;
    }

    public static void encode(StartSiegePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.targetClanId);
    }

    public static StartSiegePacket decode(FriendlyByteBuf buf) {
        return new StartSiegePacket(buf.readUUID());
    }

    public static void handle(StartSiegePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SiegeActions.startSiege(player, packet.targetClanId);
            }
        });
        context.setPacketHandled(true);
    }
}
