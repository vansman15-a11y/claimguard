package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: smear the screen black with a red blood veil (Eye Decay). */
public class BloodBlindPacket {

    public final int ticks;

    public BloodBlindPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(BloodBlindPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.ticks);
    }

    public static BloodBlindPacket decode(FriendlyByteBuf buf) {
        return new BloodBlindPacket(buf.readVarInt());
    }

    public static void handle(BloodBlindPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientBloodBlind.trigger(packet.ticks)
        ));
        context.setPacketHandled(true);
    }
}
