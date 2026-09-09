package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;
import net.robmc.claimguard.network.CastStatePacket;
import net.robmc.claimguard.network.SyncSpellBarPacket;
import net.robmc.rpgstats.magic.Spell;

/** Client-side mirror of the player's spell bar and current cast, for the HUD. */
public final class ClientSpells {

    private static final String[] SLOTS = new String[8];
    private static int weakMagicLevel = 1;

    private static Spell castingSpell;
    private static long castStartTick;
    private static int castDurationTicks;

    private ClientSpells() {
    }

    public static void onSyncSpellBar(SyncSpellBarPacket p) {
        for (int i = 0; i < 8; i++) {
            SLOTS[i] = i < p.slots.length ? p.slots[i] : "";
        }
        weakMagicLevel = p.weakMagicLevel;
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

    public static int weakMagicLevel() {
        return weakMagicLevel;
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
