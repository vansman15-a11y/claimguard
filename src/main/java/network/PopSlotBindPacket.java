package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.RpgManager;

import java.util.function.Supplier;

/** Client -&gt; server: right-click a bound bar slot - pop its most recently stacked spell, or clear it if there's just one. */
public class PopSlotBindPacket {

    private final int slot;

    public PopSlotBindPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(PopSlotBindPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
    }

    public static PopSlotBindPacket decode(FriendlyByteBuf buf) {
        return new PopSlotBindPacket(buf.readVarInt());
    }

    public static void handle(PopSlotBindPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            RpgManager.stats(player).popSlotBind(packet.slot);
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
