package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.RpgManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -&gt; server: replace one bar slot's whole bind list, in this exact order - the
 * Spellbook's expanded-slot view sends this both to drop one specific spell out of a stack
 * (the list is sent without it) and to reorder the stack (the list is sent in the new order).
 */
public class SetSlotBindOrderPacket {

    private final int slot;
    private final String[] names;

    public SetSlotBindOrderPacket(int slot, String[] names) {
        this.slot = slot;
        this.names = names;
    }

    public static void encode(SetSlotBindOrderPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
        buf.writeVarInt(p.names.length);
        for (String name : p.names) {
            buf.writeUtf(name, 48);
        }
    }

    public static SetSlotBindOrderPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        int n = Math.max(0, Math.min(buf.readVarInt(), 32));
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            names[i] = buf.readUtf(48);
        }
        return new SetSlotBindOrderPacket(slot, names);
    }

    public static void handle(SetSlotBindOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            List<String> names = new ArrayList<>();
            for (String n : packet.names) {
                names.add(n);
            }
            RpgManager.stats(player).setSlotBindOrder(packet.slot, names);
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
