package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;
import net.robmc.claimguard.network.CastStatePacket;
import net.robmc.claimguard.network.SpellCooldownPacket;
import net.robmc.claimguard.network.SyncSpellBarPacket;
import net.robmc.rpgstats.magic.Spell;

/** Client-side mirror of the player's spell bar and current cast, for the HUD. */
public final class ClientSpells {

    /** How long the "spell is ready" flash lasts once a cooldown ends. */
    public static final int READY_FLASH_TICKS = 7;

    private static final String[] SLOTS = new String[8];
    private static int[] spellLevels = new int[Spell.values().length];

    private static Spell castingSpell;
    private static long castStartTick;
    private static int castDurationTicks;

    private static final long[] cdStart = new long[Spell.values().length];
    private static final int[] cdDuration = new int[Spell.values().length];

    private ClientSpells() {
    }

    public static void onSyncSpellBar(SyncSpellBarPacket p) {
        for (int i = 0; i < 8; i++) {
            SLOTS[i] = i < p.slots.length ? p.slots[i] : "";
        }
        if (p.spellLevels != null && p.spellLevels.length == spellLevels.length) {
            spellLevels = p.spellLevels;
        }
    }

    public static void onCastState(CastStatePacket p) {
        Spell spell = Spell.byName(p.spellName);
        if (spell != null && p.durationTicks > 0) {
            castingSpell = spell;
            castStartTick = clientTick();
            castDurationTicks = p.durationTicks;
        } else {
            castingSpell = null;
        }
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
        if (i >= 0 && i < 8) {
            SLOTS[i] = spellName == null ? "" : spellName;
        }
    }

    public static Spell slot(int i) {
        return (i >= 0 && i < 8) ? Spell.byName(SLOTS[i]) : null;
    }

    public static String slotName(int i) {
        return (i >= 0 && i < 8 && SLOTS[i] != null) ? SLOTS[i] : "";
    }

    public static int spellLevel(Spell spell) {
        return spell == null ? 0 : spellLevels[spell.ordinal()];
    }

    public static boolean isCasting() {
        return castingSpell != null && castProgress() < 1.0f;
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
