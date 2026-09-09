package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: the 8 spell-bar slot assignments and every spell's current level. */
public class SyncSpellBarPacket {

    public final String[] slots;
    public final int[] spellLevels;   // indexed by Spell.ordinal()

    public SyncSpellBarPacket(String[] slots, int[] spellLevels) {
        this.slots = slots;
        this.spellLevels = spellLevels;
    }

    public static void encode(SyncSpellBarPacket p, FriendlyByteBuf buf) {
        for (int i = 0; i < 8; i++) {
            buf.writeUtf(i < p.slots.length && p.slots[i] != null ? p.slots[i] : "", 48);
        }
        buf.writeVarInt(p.spellLevels.length);
        for (int lvl : p.spellLevels) {
            buf.writeVarInt(lvl);
        }
    }

    public static SyncSpellBarPacket decode(FriendlyByteBuf buf) {
        String[] slots = new String[8];
        for (int i = 0; i < 8; i++) {
            slots[i] = buf.readUtf(48);
        }
        int n = buf.readVarInt();
        int[] levels = new int[Math.max(0, Math.min(n, 64))];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = buf.readVarInt();
        }
        return new SyncSpellBarPacket(slots, levels);
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
