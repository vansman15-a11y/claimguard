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
    private static final int BARS = StatFormulas.BAR_COUNT;

    public final String[] slots;
    public final int[] spellLevels;   // indexed by Spell.ordinal()
    public final int[] schoolLevels;  // indexed by School.ordinal()
    public final int[] skillLevels;   // indexed by Skill.ordinal()
    public final String[] weaponPrefs; // indexed by Spell.ordinal(), "" = none
    public final String[] slotActive;     // per bar slot: which bound spell actually fires next, "" = empty
    public final int[] slotCount;         // per bar slot: how many spells are stacked on it
    public final int[] slotCycleMode;     // per bar slot: PlayerStats.CycleMode ordinal
    public final boolean[] slotAutoCast;  // per bar slot
    public final String[] slotWeaponId;   // per bar slot: forced-weapon registry id, "" = none
    public final boolean[] slotForceWeapon; // per bar slot
    public final int[] barLastSlot;       // per bar (BAR_COUNT): global slot index last pressed on it, -1 = none yet

    public SyncSpellBarPacket(String[] slots, int[] spellLevels, int[] schoolLevels, int[] skillLevels, String[] weaponPrefs,
                               String[] slotActive, int[] slotCount, int[] slotCycleMode, boolean[] slotAutoCast,
                               String[] slotWeaponId, boolean[] slotForceWeapon, int[] barLastSlot) {
        this.slots = slots;
        this.spellLevels = spellLevels;
        this.schoolLevels = schoolLevels;
        this.skillLevels = skillLevels;
        this.weaponPrefs = weaponPrefs;
        this.slotActive = slotActive;
        this.slotCount = slotCount;
        this.slotCycleMode = slotCycleMode;
        this.slotAutoCast = slotAutoCast;
        this.slotWeaponId = slotWeaponId;
        this.slotForceWeapon = slotForceWeapon;
        this.barLastSlot = barLastSlot;
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
        for (int i = 0; i < SLOTS; i++) {
            buf.writeUtf(i < p.slotActive.length && p.slotActive[i] != null ? p.slotActive[i] : "", 48);
        }
        for (int i = 0; i < SLOTS; i++) {
            buf.writeVarInt(i < p.slotCount.length ? p.slotCount[i] : 0);
        }
        for (int i = 0; i < SLOTS; i++) {
            buf.writeVarInt(i < p.slotCycleMode.length ? p.slotCycleMode[i] : 0);
        }
        for (int i = 0; i < SLOTS; i++) {
            buf.writeBoolean(i < p.slotAutoCast.length && p.slotAutoCast[i]);
        }
        for (int i = 0; i < SLOTS; i++) {
            buf.writeUtf(i < p.slotWeaponId.length && p.slotWeaponId[i] != null ? p.slotWeaponId[i] : "", 64);
        }
        for (int i = 0; i < SLOTS; i++) {
            buf.writeBoolean(i < p.slotForceWeapon.length && p.slotForceWeapon[i]);
        }
        for (int i = 0; i < BARS; i++) {
            buf.writeVarInt(i < p.barLastSlot.length ? p.barLastSlot[i] : -1);
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
        String[] slotActive = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotActive[i] = buf.readUtf(48);
        }
        int[] slotCount = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotCount[i] = buf.readVarInt();
        }
        int[] slotCycleMode = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotCycleMode[i] = buf.readVarInt();
        }
        boolean[] slotAutoCast = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotAutoCast[i] = buf.readBoolean();
        }
        String[] slotWeaponId = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotWeaponId[i] = buf.readUtf(64);
        }
        boolean[] slotForceWeapon = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slotForceWeapon[i] = buf.readBoolean();
        }
        int[] barLastSlot = new int[BARS];
        for (int i = 0; i < BARS; i++) {
            barLastSlot[i] = buf.readVarInt();
        }
        return new SyncSpellBarPacket(slots, spellLevels, schoolLevels, skillLevels, weaponPrefs,
                slotActive, slotCount, slotCycleMode, slotAutoCast, slotWeaponId, slotForceWeapon, barLastSlot);
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
