package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.RpgManager;

import java.util.function.Supplier;

/** Client -> server: put this spell (or "" to clear) in this spell-bar slot. */
public class SetSpellSlotPacket {

    private final int slot;
    private final String spellName;

    public SetSpellSlotPacket(int slot, String spellName) {
        this.slot = slot;
        this.spellName = spellName == null ? "" : spellName;
    }

    public static void encode(SetSpellSlotPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
        buf.writeUtf(p.spellName, 48);
    }

    public static SetSpellSlotPacket decode(FriendlyByteBuf buf) {
        return new SetSpellSlotPacket(buf.readVarInt(), buf.readUtf(48));
    }

    public static void handle(SetSpellSlotPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            RpgManager.stats(player).setSpellSlot(packet.slot, packet.spellName);
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
