package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: the set of player ids who are in an allied clan, so their
 * name tags render green. Sent on login and whenever relations change.
 */
public class SyncAlliesPacket {

    private final List<UUID> alliedPlayers;

    public SyncAlliesPacket(List<UUID> alliedPlayers) {
        this.alliedPlayers = alliedPlayers;
    }

    public static void encode(SyncAlliesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.alliedPlayers.size());
        for (UUID id : packet.alliedPlayers) {
            buf.writeUUID(id);
        }
    }

    public static SyncAlliesPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUUID());
        }
        return new SyncAlliesPacket(ids);
    }

    public static void handle(SyncAlliesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.AllyClient.setAllies(packet.alliedPlayers)
        ));
        context.setPacketHandled(true);
    }
}
