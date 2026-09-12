package net.robmc.combat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.combat.ParryManager;

import java.util.function.Supplier;

/** Client -&gt; server: right-click was pressed or released while wielding a melee weapon - start or stop parrying. */
public class ParryStatePacket {

    public final boolean active;

    public ParryStatePacket(boolean active) {
        this.active = active;
    }

    public static void encode(ParryStatePacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
    }

    public static ParryStatePacket decode(FriendlyByteBuf buf) {
        return new ParryStatePacket(buf.readBoolean());
    }

    public static void handle(ParryStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ParryManager.setParrying(player, packet.active);
            }
        });
        context.setPacketHandled(true);
    }
}
