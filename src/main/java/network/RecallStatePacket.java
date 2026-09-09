package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: a Recall channel started ({@code ticksLeft} &gt; 0) or ended/cancelled (0). */
public class RecallStatePacket {

    public final int ticksLeft;

    public RecallStatePacket(int ticksLeft) {
        this.ticksLeft = ticksLeft;
    }

    public static void encode(RecallStatePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.ticksLeft);
    }

    public static RecallStatePacket decode(FriendlyByteBuf buf) {
        return new RecallStatePacket(buf.readVarInt());
    }

    public static void handle(RecallStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.magic.ClientRecall.onState(packet.ticksLeft)
        ));
        context.setPacketHandled(true);
    }
}
