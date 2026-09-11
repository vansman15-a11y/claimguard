package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.RpgManager;

import java.util.function.Supplier;

/**
 * Client -&gt; server: put this spell (or "" to clear) in this spell-bar slot. A plain drop
 * replaces whatever was there; {@code append=true} (a Shift-drop) stacks it on top instead,
 * turning the slot into a multi-bind "ray bar" - see PlayerStats.addSlotBind.
 */
public class SetSpellSlotPacket {

    private final int slot;
    private final String spellName;
    private final boolean append;

    public SetSpellSlotPacket(int slot, String spellName) {
        this(slot, spellName, false);
    }

    public SetSpellSlotPacket(int slot, String spellName, boolean append) {
        this.slot = slot;
        this.spellName = spellName == null ? "" : spellName;
        this.append = append;
    }

    public static void encode(SetSpellSlotPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
        buf.writeUtf(p.spellName, 48);
        buf.writeBoolean(p.append);
    }

    public static SetSpellSlotPacket decode(FriendlyByteBuf buf) {
        return new SetSpellSlotPacket(buf.readVarInt(), buf.readUtf(48), buf.readBoolean());
    }

    public static void handle(SetSpellSlotPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.append) {
                RpgManager.stats(player).addSlotBind(packet.slot, packet.spellName);
            } else {
                RpgManager.stats(player).setSpellSlot(packet.slot, packet.spellName);
            }
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
