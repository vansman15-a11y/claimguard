package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; tracking clients: this player is now in the given druid form (0 none / 1 wolf / 2 dolphin). */
public class WildShapeSyncPacket {

    public final int entityId;
    public final int form;

    public WildShapeSyncPacket(int entityId, int form) {
        this.entityId = entityId;
        this.form = form;
    }

    public static void encode(WildShapeSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.form);
    }

    public static WildShapeSyncPacket decode(FriendlyByteBuf buf) {
        return new WildShapeSyncPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(WildShapeSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientWildShape.set(packet.entityId, packet.form)
        ));
        context.setPacketHandled(true);
    }
}
