package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: wash the screen white for a bit (Bright Light). */
public class ScreenFlashPacket {

    public final int ticks;

    public ScreenFlashPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(ScreenFlashPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.ticks);
    }

    public static ScreenFlashPacket decode(FriendlyByteBuf buf) {
        return new ScreenFlashPacket(buf.readVarInt());
    }

    public static void handle(ScreenFlashPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientFlash.trigger(packet.ticks)
        ));
        context.setPacketHandled(true);
    }
}
