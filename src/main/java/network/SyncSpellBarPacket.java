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
    public final String[] slotActive;     // per bar slot: which bound spell actually fires next, "" = empty
    public final String[][] slotBinds;    // per bar slot: its full ordered bind list (empty array = empty slot)
    public final int[] slotCycleMode;     // per bar slot: PlayerStats.CycleMode ordinal
    public final boolean[] slotAutoCast;  // per bar slot
    public final String[] slotWeaponId;   // per bar slot: forced-weapon registry id, "" = none
    public final boolean[] slotForceWeapon; // per bar slot
    public final int lastSlot;            // the global slot index last pressed on either bar, -1 = none yet

    public SyncSpellBarPacket(String[] slots, int[] spellLevels, int[] schoolLevels, int[] skillLevels, String[] weaponPrefs,
                               String[] slotActive, String[][] slotBinds, int[] slotCycleMode, boolean[] slotAutoCast,
                               String[] slotWeaponId, boolean[] slotForceWeapon, int lastSlot) {
        this.slots = slots;
        this.spellLevels = spellLevels;
        this.schoolLevels = schoolLevels;
        this.skillLevels = skillLevels;
        this.weaponPrefs = weaponPrefs;
        this.slotActive = slotActive;
        this.slotBinds = slotBinds;
        this.slotCycleMode = slotCycleMode;
        this.slotAutoCast = slotAutoCast;
        this.slotWeaponId = slotWeaponId;
        this.slotForceWeapon = slotForceWeapon;
        this.lastSlot = lastSlot;
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
            String[] binds = i < p.slotBinds.length && p.slotBinds[i] != null ? p.slotBinds[i] : new String[0];
            buf.writeVarInt(binds.length);
            for (String name : binds) {
                buf.writeUtf(name != null ? name : "", 48);
            }
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
        buf.writeVarInt(p.lastSlot);
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
        String[][] slotBinds = new String[SLOTS][];
        for (int i = 0; i < SLOTS; i++) {
            int bn = Math.max(0, Math.min(buf.readVarInt(), StatFormulas.MAX_SLOT_BINDS));
            String[] binds = new String[bn];
            for (int j = 0; j < bn; j++) {
                binds[j] = buf.readUtf(48);
            }
            slotBinds[i] = binds;
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
        int lastSlot = buf.readVarInt();
        return new SyncSpellBarPacket(slots, spellLevels, schoolLevels, skillLevels, weaponPrefs,
                slotActive, slotBinds, slotCycleMode, slotAutoCast, slotWeaponId, slotForceWeapon, lastSlot);
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
