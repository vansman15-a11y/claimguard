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
    private static int[] schoolLevels = defaultSchoolLevels();
    private static int[] skillLevels = new int[Skill.values().length];

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
        if (p.schoolLevels != null && p.schoolLevels.length == schoolLevels.length) {
            schoolLevels = p.schoolLevels;
        }
        if (p.skillLevels != null && p.skillLevels.length == skillLevels.length) {
            skillLevels = p.skillLevels;
        }
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

    /** The viewer's level in this spell's school. */
    public static int spellLevel(Spell spell) {
        return spell == null ? 0 : schoolLevel(spell.school());
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
