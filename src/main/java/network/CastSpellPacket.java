package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.magic.SpellCasting;

import java.util.function.Supplier;

/** Client -> server: begin casting the spell in this spell-bar slot. */
public class CastSpellPacket {

    private final int slot;

    public CastSpellPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(CastSpellPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
    }

    public static CastSpellPacket decode(FriendlyByteBuf buf) {
        return new CastSpellPacket(buf.readVarInt());
    }

    public static void handle(CastSpellPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SpellCasting.activateSlot(player, packet.slot);
            }
        });
        context.setPacketHandled(true);
    }
}
