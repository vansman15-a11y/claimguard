package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.magic.SpellCasting;

import java.util.function.Supplier;

/**
 * Client -&gt; server: {@code release=false} starts charging the spell in this
 * slot; {@code release=true} lets go of the key (fire it if it finished charging,
 * cancel it if it hadn't).
 */
public class CastSpellPacket {

    private final int slot;
    private final boolean release;

    public CastSpellPacket(int slot, boolean release) {
        this.slot = slot;
        this.release = release;
    }

    public static void encode(CastSpellPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
        buf.writeBoolean(p.release);
    }

    public static CastSpellPacket decode(FriendlyByteBuf buf) {
        return new CastSpellPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(CastSpellPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.release) {
                SpellCasting.releaseCast(player, packet.slot);
            } else {
                SpellCasting.startCast(player, packet.slot);
            }
        });
        context.setPacketHandled(true);
    }
}
