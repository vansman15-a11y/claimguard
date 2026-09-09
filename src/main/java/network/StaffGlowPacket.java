package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; tracking clients: this player just started a cast, light their staff for a bit. */
public class StaffGlowPacket {

    public final int entityId;
    public final int ticks;

    public StaffGlowPacket(int entityId, int ticks) {
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public static void encode(StaffGlowPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.ticks);
    }

    public static StaffGlowPacket decode(FriendlyByteBuf buf) {
        return new StaffGlowPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StaffGlowPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientStaffGlow.set(packet.entityId, packet.ticks)
        ));
        context.setPacketHandled(true);
    }
}
