package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.JumpArcManager;

import java.util.function.Supplier;

/** Client -&gt; server: jump was pressed again while airborne - try a mid-arc double jump (see JumpArcManager). */
public class DoubleJumpPacket {

    public DoubleJumpPacket() {
    }

    public static void encode(DoubleJumpPacket p, FriendlyByteBuf buf) {
    }

    public static DoubleJumpPacket decode(FriendlyByteBuf buf) {
        return new DoubleJumpPacket();
    }

    public static void handle(DoubleJumpPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                JumpArcManager.tryDoubleJump(player);
            }
        });
        context.setPacketHandled(true);
    }
}
