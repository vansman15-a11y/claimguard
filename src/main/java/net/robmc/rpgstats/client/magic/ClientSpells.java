package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;
import net.robmc.claimguard.network.CastStatePacket;
import net.robmc.claimguard.network.SpellCooldownPacket;
import net.robmc.claimguard.network.SyncSpellBarPacket;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.magic.School;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

/** Client-side mirror of the player's spell bar and current cast, for the HUD. */
public final class ClientSpells {

    /** How long the "spell is ready" flash lasts once a cooldown ends. */
    public static final int READY_FLASH_TICKS = 7;

    private static final int SLOT_COUNT = StatFormulas.TOTAL_BAR_SLOTS;
    private static final String[] SLOTS = new String[SLOT_COUNT];
    private static int[] spellLevels = new int[Spell.values().length];
    private static int[] schoolLevels = defaultSchoolLevels();
    private static int[] skillLevels = new int[Skill.values().length];
    private static String[] weaponPrefs = new String[Spell.values().length];
    private static String[] slotActive = new String[SLOT_COUNT];
    private static String[][] slotBinds = new String[SLOT_COUNT][0];
    private static int[] slotCycleMode = new int[SLOT_COUNT];
    private static boolean[] slotAutoCast = new boolean[SLOT_COUNT];
    private static String[] slotWeaponId = new String[SLOT_COUNT];
    private static boolean[] slotForceWeapon = new boolean[SLOT_COUNT];
    private static int lastSlot = -1;
    /** Client tick lastSlot last actually changed (a fresh cast press) - lets the Bar 1 readout tell a spell cast apart from a more recent melee swing. */
    private static long lastSpellActionTick = Long.MIN_VALUE;

    private static int[] defaultSchoolLevels() {
        int[] a = new int[School.values().length];
        java.util.Arrays.fill(a, 1);
        return a;
    }

    private static Spell castingSpell;
    private static long castStartTick;
    private static int castDurationTicks;
    private static boolean castCharged;   // finished charging, waiting on key release
    private static boolean castHeld;      // the client is still holding the cast key

    private static final long[] cdStart = new long[Spell.values().length];
    private static final int[] cdDuration = new int[Spell.values().length];

    private ClientSpells() {
    }

    public static void onSyncSpellBar(SyncSpellBarPacket p) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            SLOTS[i] = i < p.slots.length ? p.slots[i] : "";
        }
        if (p.spellLevels != null && p.spellLevels.length == spellLevels.length) {
            spellLevels = p.spellLevels;
        }
        if (p.schoolLevels != null && p.schoolLevels.length == schoolLevels.length) {
            schoolLevels = p.schoolLevels;
        }
        if (p.skillLevels != null && p.skillLevels.length == skillLevels.length) {
            skillLevels = p.skillLevels;
        }
        if (p.weaponPrefs != null && p.weaponPrefs.length == weaponPrefs.length) {
            weaponPrefs = p.weaponPrefs;
        }
        if (p.slotActive != null && p.slotActive.length == SLOT_COUNT) {
            slotActive = p.slotActive;
        }
        if (p.slotBinds != null && p.slotBinds.length == SLOT_COUNT) {
            slotBinds = p.slotBinds;
        }
        if (p.slotCycleMode != null && p.slotCycleMode.length == SLOT_COUNT) {
            slotCycleMode = p.slotCycleMode;
        }
        if (p.slotAutoCast != null && p.slotAutoCast.length == SLOT_COUNT) {
            slotAutoCast = p.slotAutoCast;
        }
        if (p.slotWeaponId != null && p.slotWeaponId.length == SLOT_COUNT) {
            slotWeaponId = p.slotWeaponId;
        }
        if (p.slotForceWeapon != null && p.slotForceWeapon.length == SLOT_COUNT) {
            slotForceWeapon = p.slotForceWeapon;
        }
        if (p.lastSlot != lastSlot) {
            lastSpellActionTick = clientTick();
        }
        lastSlot = p.lastSlot;
    }

    /** Client tick of the most recent spell-slot press - compare against ClientSwingCooldown.lastSwingTick() to see which action is newer. */
    public static long lastSpellActionTick() {
        return lastSpellActionTick;
    }

    /** The item registry id this spell auto-equips before casting, or null if none is set. */
    public static String weaponPref(Spell spell) {
        String v = weaponPrefs[spell.ordinal()];
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** The spell that would actually fire from this slot on a press right now (see SpellCasting.resolveSlotSpell). */
    public static Spell activeSlot(int i) {
        return (i >= 0 && i < SLOT_COUNT && slotActive[i] != null) ? Spell.byName(slotActive[i]) : null;
    }

    /** This slot's full, ordered bind list (see PlayerStats.getSlotBinds) - empty if the slot is empty. */
    public static java.util.List<String> slotBinds(int i) {
        return (i >= 0 && i < SLOT_COUNT && slotBinds[i] != null) ? java.util.Arrays.asList(slotBinds[i]) : java.util.List.of();
    }

    /** How many spells are stacked onto this slot (1 for a plain single-bind slot, 0 if empty). */
    public static int slotBindCount(int i) {
        return slotBinds(i).size();
    }

    /** 0 = Cycle, 1 = First Available - see PlayerStats.CycleMode. */
    public static int slotCycleMode(int i) {
        return (i >= 0 && i < SLOT_COUNT) ? slotCycleMode[i] : 0;
    }

    public static boolean slotAutoCast(int i) {
        return i >= 0 && i < SLOT_COUNT && slotAutoCast[i];
    }

    /** This slot's forced-weapon registry id, or null if none is set. */
    public static String slotWeaponId(int i) {
        String v = (i >= 0 && i < SLOT_COUNT) ? slotWeaponId[i] : null;
        return (v == null || v.isEmpty()) ? null : v;
    }

    public static boolean slotForceWeapon(int i) {
        return i >= 0 && i < SLOT_COUNT && slotForceWeapon[i];
    }

    /** The global slot index last pressed, on either bar, or -1 if none yet this session. */
    public static int lastSlot() {
        return lastSlot;
    }

    /** The spell shown in Bar 1's "currently selected" readout - whatever the last-pressed slot (on either bar) would fire. */
    public static Spell selectedSpell() {
        return lastSlot >= 0 ? activeSlot(lastSlot) : null;
    }

    /** The weapon shown under that readout: the last-pressed slot's forced weapon, else its spell's own weapon pref. */
    public static String selectedWeaponId() {
        if (lastSlot < 0) {
            return null;
        }
        if (slotForceWeapon(lastSlot)) {
            return slotWeaponId(lastSlot);
        }
        Spell sp = activeSlot(lastSlot);
        return sp != null ? weaponPref(sp) : null;
    }

    public static void onCastState(CastStatePacket p) {
        Spell spell = Spell.byName(p.spellName);
        if (spell != null && p.durationTicks > 0) {
            castingSpell = spell;
            castStartTick = clientTick();
            castDurationTicks = p.durationTicks;
            castCharged = false;
        } else if (spell != null && p.durationTicks == -1) {
            castCharged = true; // fully charged - keep the bar full until the key is released
        } else {
            castingSpell = null;
            castCharged = false;
        }
    }

    public static void setCastHeld(boolean held) {
        castHeld = held;
    }

    public static boolean isCharged() {
        return castingSpell != null && castCharged;
    }

    public static void onSpellCooldown(SpellCooldownPacket p) {
        Spell spell = Spell.byName(p.spellName);
        if (spell != null && p.durationTicks > 0) {
            cdStart[spell.ordinal()] = clientTick();
            cdDuration[spell.ordinal()] = p.durationTicks;
        }
    }

    /** 0..1 of the cooldown elapsed (1 = ready). 1 when the spell is not on cooldown. */
    public static float cooldownProgress(Spell spell) {
        if (spell == null) {
            return 1.0f;
        }
        int d = cdDuration[spell.ordinal()];
        if (d <= 0) {
            return 1.0f;
        }
        float p = (clientTick() - cdStart[spell.ordinal()] + Minecraft.getInstance().getFrameTime()) / d;
        return Math.max(0f, Math.min(1f, p));
    }

    /** 1..0 fading pulse for the first {@link #READY_FLASH_TICKS} ticks after a cooldown ends; 0 otherwise. */
    public static float readyFlash(Spell spell) {
        if (spell == null) {
            return 0f;
        }
        int d = cdDuration[spell.ordinal()];
        if (d <= 0) {
            return 0f;
        }
        long endTick = cdStart[spell.ordinal()] + d;
        float since = clientTick() + Minecraft.getInstance().getFrameTime() - endTick;
        if (since < 0f || since >= READY_FLASH_TICKS) {
            return 0f;
        }
        return 1.0f - since / READY_FLASH_TICKS;
    }

    /** Seconds left on this spell's cooldown (rounded up), or 0 once it's ready. */
    public static double cooldownSecondsLeft(Spell spell) {
        if (spell == null) {
            return 0;
        }
        int d = cdDuration[spell.ordinal()];
        if (d <= 0) {
            return 0;
        }
        long remainingTicks = d - (clientTick() - cdStart[spell.ordinal()]);
        return remainingTicks > 0 ? remainingTicks / 20.0 : 0;
    }

    public static void setSlotLocal(int i, String spellName) {
        if (i >= 0 && i < SLOT_COUNT) {
            SLOTS[i] = spellName == null ? "" : spellName;
        }
    }

    public static Spell slot(int i) {
        return (i >= 0 && i < SLOT_COUNT) ? Spell.byName(SLOTS[i]) : null;
    }

    public static String slotName(int i) {
        return (i >= 0 && i < SLOT_COUNT && SLOTS[i] != null) ? SLOTS[i] : "";
    }

    /** The viewer's level in this individual spell. */
    public static int spellLevel(Spell spell) {
        return spell == null ? 0 : spellLevels[spell.ordinal()];
    }

    public static int schoolLevel(School school) {
        return school == null ? 0 : schoolLevels[school.ordinal()];
    }

    /** Whether the viewer has unlocked this spell's tier. */
    public static boolean unlocked(Spell spell) {
        return spell != null && schoolLevel(spell.school()) >= spell.unlockLevel();
    }

    public static int skillLevel(Skill skill) {
        return skill == null ? 0 : skillLevels[skill.ordinal()];
    }

    public static boolean isCasting() {
        // shown while charging, and kept full while charged/held until the server clears it
        return castingSpell != null;
    }

    public static Spell castingSpell() {
        return castingSpell;
    }

    /** 0..1 progress of the current cast. */
    public static float castProgress() {
        if (castingSpell == null || castDurationTicks <= 0) {
            return 1.0f;
        }
        float p = (clientTick() - castStartTick + Minecraft.getInstance().getFrameTime()) / castDurationTicks;
        return Math.max(0f, Math.min(1f, p));
    }

    private static long clientTick() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
