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
    public final int[] schoolLevels;  // indexed by School.ordinal()
    public final int[] skillLevels;   // indexed by Skill.ordinal()

    public SyncSpellBarPacket(String[] slots, int[] schoolLevels, int[] skillLevels) {
        this.slots = slots;
        this.schoolLevels = schoolLevels;
        this.skillLevels = skillLevels;
    }

    public static void encode(SyncSpellBarPacket p, FriendlyByteBuf buf) {
        for (int i = 0; i < SLOTS; i++) {
            buf.writeUtf(i < p.slots.length && p.slots[i] != null ? p.slots[i] : "", 48);
        }
        writeInts(buf, p.schoolLevels);
        writeInts(buf, p.skillLevels);
    }

    public static SyncSpellBarPacket decode(FriendlyByteBuf buf) {
        String[] slots = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = buf.readUtf(48);
        }
        int[] schoolLevels = readInts(buf);
        int[] skillLevels = readInts(buf);
        return new SyncSpellBarPacket(slots, schoolLevels, skillLevels);
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
