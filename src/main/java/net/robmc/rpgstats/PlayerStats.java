package net.robmc.rpgstats;

import net.minecraft.nbt.CompoundTag;
import net.robmc.rpgstats.magic.Spell;

import java.util.EnumMap;
import java.util.Map;

/**
 * One player's RPG progression: a level and an XP-toward-next-level for each of
 * the six stats and each spell, plus their current Stamina and Mana values (HP
 * rides on the vanilla health attribute so vanilla damage/regen/death keep
 * working).
 *
 * Stored per player UUID in RpgData (a SavedData), so it survives death and
 * relog without any capability plumbing.
 */
public class PlayerStats {

    private final Map<Stat, Integer> levels = new EnumMap<>(Stat.class);
    private final Map<Stat, Double> xp = new EnumMap<>(Stat.class);

    private final Map<Spell, Integer> spellLevels = new EnumMap<>(Spell.class);
    private final Map<Spell, Double> spellXp = new EnumMap<>(Spell.class);

    private double stamina = StatFormulas.BASE_POOL;
    private double mana = StatFormulas.BASE_POOL;
    private boolean initialised = false;

    /** The 8 vertical spell-bar slots, each holding a Spell enum name or "". */
    private final String[] spellBar = new String[8];

    public PlayerStats() {
        for (Stat stat : Stat.values()) {
            levels.put(stat, 0);
            xp.put(stat, 0.0);
        }
        for (Spell spell : Spell.values()) {
            spellLevels.put(spell, 0);
            spellXp.put(spell, 0.0);
        }
        java.util.Arrays.fill(spellBar, "");
    }

    // --- stats ---

    public int getLevel(Stat stat) {
        return levels.get(stat);
    }

    public double getXp(Stat stat) {
        return xp.get(stat);
    }

    /** Adds XP to a stat and returns how many levels it gained (usually 0). Stops at LEVEL_CAP. */
    public int addXp(Stat stat, double amount) {
        if (amount <= 0) {
            return 0;
        }
        int level = levels.get(stat);
        if (level >= StatFormulas.LEVEL_CAP) {
            return 0;
        }
        double have = xp.get(stat) + amount;
        int gained = 0;
        while (level < StatFormulas.LEVEL_CAP && have >= StatFormulas.xpForNextLevel(level)) {
            have -= StatFormulas.xpForNextLevel(level);
            level++;
            gained++;
        }
        if (level >= StatFormulas.LEVEL_CAP) {
            have = 0;
        }
        levels.put(stat, level);
        xp.put(stat, have);
        return gained;
    }

    // --- spells ---

    public int getSpellLevel(Spell spell) {
        return spellLevels.getOrDefault(spell, 0);
    }

    public double getSpellXp(Spell spell) {
        return spellXp.getOrDefault(spell, 0.0);
    }

    /** Adds XP to one spell and returns how many levels it gained. Stops at LEVEL_CAP. */
    public int addSpellXp(Spell spell, double amount) {
        if (amount <= 0) {
            return 0;
        }
        int level = spellLevels.getOrDefault(spell, 0);
        if (level >= StatFormulas.LEVEL_CAP) {
            return 0;
        }
        double have = spellXp.getOrDefault(spell, 0.0) + amount;
        int gained = 0;
        while (level < StatFormulas.LEVEL_CAP && have >= StatFormulas.xpForNextLevel(level)) {
            have -= StatFormulas.xpForNextLevel(level);
            level++;
            gained++;
        }
        if (level >= StatFormulas.LEVEL_CAP) {
            have = 0;
        }
        spellLevels.put(spell, level);
        spellXp.put(spell, have);
        return gained;
    }

    /** Spell levels indexed by Spell.ordinal(), for the sync packet. */
    public int[] spellLevelArray() {
        Spell[] all = Spell.values();
        int[] out = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = spellLevels.getOrDefault(all[i], 0);
        }
        return out;
    }

    // --- spell bar ---

    public String[] getSpellBar() {
        return spellBar;
    }

    public void setSpellSlot(int slot, String spellName) {
        if (slot >= 0 && slot < spellBar.length) {
            spellBar[slot] = spellName == null ? "" : spellName;
        }
    }

    // --- pools ---

    public double getStamina() {
        return stamina;
    }

    public double getMana() {
        return mana;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void markInitialised() {
        initialised = true;
    }

    public void setStamina(double value) {
        stamina = Math.max(0, Math.min(value, StatFormulas.maxStamina(this)));
    }

    public void setMana(double value) {
        mana = Math.max(0, Math.min(value, StatFormulas.maxMana(this)));
    }

    // --- NBT ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Stat stat : Stat.values()) {
            tag.putInt(stat.name() + "_lvl", levels.get(stat));
            tag.putDouble(stat.name() + "_xp", xp.get(stat));
        }
        for (Spell spell : Spell.values()) {
            tag.putInt("SpellLvl_" + spell.name(), spellLevels.getOrDefault(spell, 0));
            tag.putDouble("SpellXp_" + spell.name(), spellXp.getOrDefault(spell, 0.0));
        }
        tag.putDouble("Stamina", stamina);
        tag.putDouble("Mana", mana);
        tag.putBoolean("Initialised", initialised);
        for (int i = 0; i < spellBar.length; i++) {
            tag.putString("Spell" + i, spellBar[i]);
        }
        return tag;
    }

    public static PlayerStats load(CompoundTag tag) {
        PlayerStats stats = new PlayerStats();
        for (Stat stat : Stat.values()) {
            stats.levels.put(stat, clampLevel(tag.getInt(stat.name() + "_lvl")));
            stats.xp.put(stat, tag.getDouble(stat.name() + "_xp"));
        }
        for (Spell spell : Spell.values()) {
            stats.spellLevels.put(spell, clampLevel(tag.getInt("SpellLvl_" + spell.name())));
            stats.spellXp.put(spell, tag.getDouble("SpellXp_" + spell.name()));
        }
        stats.stamina = tag.getDouble("Stamina");
        stats.mana = tag.getDouble("Mana");
        stats.initialised = tag.getBoolean("Initialised");
        for (int i = 0; i < stats.spellBar.length; i++) {
            stats.spellBar[i] = tag.getString("Spell" + i);
        }
        return stats;
    }

    private static int clampLevel(int v) {
        return Math.max(0, Math.min(v, StatFormulas.LEVEL_CAP));
    }
}
