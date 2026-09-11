package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.StatFormulas;

import java.util.function.Supplier;

/** Server -&gt; client: every casting-bar slot assignment and every spell/skill's current level. */
public class SyncSpellBarPacket {

    private static final int SLOTS = StatFormulas.TOTAL_BAR_SLOTS;

    public final String[] slots;
    public final int[] spellLevels;   // indexed by Spell.ordinal()
    public final int[] schoolLevels;  // indexed by School.ordinal()
    public final int[] skillLevels;   // indexed by Skill.ordinal()
    public final String[] weaponPrefs; // indexed by Spell.ordinal(), "" = none

    public SyncSpellBarPacket(String[] slots, int[] spellLevels, int[] schoolLevels, int[] skillLevels, String[] weaponPrefs) {
        this.slots = slots;
        this.spellLevels = spellLevels;
        this.schoolLevels = schoolLevels;
        this.skillLevels = skillLevels;
        this.weaponPrefs = weaponPrefs;
    }

    public static void encode(SyncSpellBarPacket p, FriendlyByteBuf buf) {
        for (int i = 0; i < SLOTS; i++) {
            buf.writeUtf(i < p.slots.length && p.slots[i] != null ? p.slots[i] : "", 48);
        }
        writeInts(buf, p.spellLevels);
        writeInts(buf, p.schoolLevels);
        writeInts(buf, p.skillLevels);
        buf.writeVarInt(p.weaponPrefs.length);
        for (String s : p.weaponPrefs) {
            buf.writeUtf(s != null ? s : "", 64);
        }
    }

    public static SyncSpellBarPacket decode(FriendlyByteBuf buf) {
        String[] slots = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = buf.readUtf(48);
        }
        int[] spellLevels = readInts(buf);
        int[] schoolLevels = readInts(buf);
        int[] skillLevels = readInts(buf);
        int wn = Math.max(0, Math.min(buf.readVarInt(), 256));
        String[] weaponPrefs = new String[wn];
        for (int i = 0; i < wn; i++) {
            weaponPrefs[i] = buf.readUtf(64);
        }
        return new SyncSpellBarPacket(slots, spellLevels, schoolLevels, skillLevels, weaponPrefs);
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
