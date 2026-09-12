package net.robmc.combat.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.combat.ShieldBindings;

import java.util.function.Supplier;

/** Client -&gt; server: a shield was dragged onto a sword (or a sword onto a shield) in the inventory - bind them. */
public class BindShieldPacket {

    public final String swordId;
    public final String shieldId;

    public BindShieldPacket(String swordId, String shieldId) {
        this.swordId = swordId;
        this.shieldId = shieldId;
    }

    public static void encode(BindShieldPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.swordId, 64);
        buf.writeUtf(p.shieldId, 64);
    }

    public static BindShieldPacket decode(FriendlyByteBuf buf) {
        return new BindShieldPacket(buf.readUtf(64), buf.readUtf(64));
    }

    public static void handle(BindShieldPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ShieldBindings.bind(player, packet.swordId, packet.shieldId);
            player.displayClientMessage(Component.literal("That shield will now follow that sword.")
                    .withStyle(ChatFormatting.GRAY), true);
        });
        context.setPacketHandled(true);
    }
}
