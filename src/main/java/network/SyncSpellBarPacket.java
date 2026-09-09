package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: the 8 spell-bar slot assignments and every spell/skill's current level. */
public class SyncSpellBarPacket {

    public final String[] slots;
    public final int[] spellLevels;   // indexed by Spell.ordinal()
    public final int[] skillLevels;   // indexed by Skill.ordinal()

    public SyncSpellBarPacket(String[] slots, int[] spellLevels, int[] skillLevels) {
        this.slots = slots;
        this.spellLevels = spellLevels;
        this.skillLevels = skillLevels;
    }

    public static void encode(SyncSpellBarPacket p, FriendlyByteBuf buf) {
        for (int i = 0; i < 8; i++) {
            buf.writeUtf(i < p.slots.length && p.slots[i] != null ? p.slots[i] : "", 48);
        }
        writeInts(buf, p.spellLevels);
        writeInts(buf, p.skillLevels);
    }

    public static SyncSpellBarPacket decode(FriendlyByteBuf buf) {
        String[] slots = new String[8];
        for (int i = 0; i < 8; i++) {
            slots[i] = buf.readUtf(48);
        }
        int[] spellLevels = readInts(buf);
        int[] skillLevels = readInts(buf);
        return new SyncSpellBarPacket(slots, spellLevels, skillLevels);
    }

    private static void writeInts(FriendlyByteBuf buf, int[] arr) {
        buf.writeVarInt(arr.length);
        for (int v : arr) {
            buf.writeVarInt(v);
        }
    }

    private static int[] readInts(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(buf.readVarInt(), 64));
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.readVarInt();
        }
        return out;
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
