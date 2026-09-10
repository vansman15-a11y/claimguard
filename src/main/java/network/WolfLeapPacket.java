package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -&gt; server: the wolf-form player pressed use, asking to leap toward their crosshair. */
public class WolfLeapPacket {

    public WolfLeapPacket() {
    }

    public static void encode(WolfLeapPacket p, FriendlyByteBuf buf) {
    }

    public static WolfLeapPacket decode(FriendlyByteBuf buf) {
        return new WolfLeapPacket();
    }

    public static void handle(WolfLeapPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                net.robmc.rpgstats.magic.WildShapeManager.leap(player);
            }
        });
        context.setPacketHandled(true);
    }
}
