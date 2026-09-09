package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the 8 spell-bar slot assignments and the Weak Magic level. */
public class SyncSpellBarPacket {

    public final String[] slots;
    public final int weakMagicLevel;

    public SyncSpellBarPacket(String[] slots, int weakMagicLevel) {
        this.slots = slots;
        this.weakMagicLevel = weakMagicLevel;
    }

    public static void encode(SyncSpellBarPacket p, FriendlyByteBuf buf) {
        for (int i = 0; i < 8; i++) {
            buf.writeUtf(i < p.slots.length && p.slots[i] != null ? p.slots[i] : "", 48);
        }
        buf.writeVarInt(p.weakMagicLevel);
    }

    public static SyncSpellBarPacket decode(FriendlyByteBuf buf) {
        String[] slots = new String[8];
        for (int i = 0; i < 8; i++) {
            slots[i] = buf.readUtf(48);
        }
        return new SyncSpellBarPacket(slots, buf.readVarInt());
    }

    public static void handle(SyncSpellBarPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.magic.ClientSpells.onSyncSpellBar(packet)
        ));
        context.setPacketHandled(true);
    }
}
