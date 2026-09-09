package net.robmc.rpgstats;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/**
 * One player's RPG progression: a level and an XP-toward-next-level for each of
 * the six stats, plus their current Stamina and Mana values (HP rides on the
 * vanilla health attribute so vanilla damage/regen/death keep working).
 *
 * Stored per player UUID in RpgData (a SavedData), so it survives death and
 * relog without any capability plumbing.
 */
public class PlayerStats {

    private final Map<Stat, Integer> levels = new EnumMap<>(Stat.class);
    private final Map<Stat, Double> xp = new EnumMap<>(Stat.class);

    private double stamina = StatFormulas.BASE_POOL;
    private double mana = StatFormulas.BASE_POOL;
    private boolean initialised = false;

    /** Weak Magic school progress (also nudges Intelligence). Level 1 = all Weak Magic spells known. */
    private int weakMagicLevel = 1;
    private double weakMagicXp = 0;

    /** The 8 vertical spell-bar slots, each holding a Spell enum name or "". */
    private final String[] spellBar = new String[8];

    public PlayerStats() {
        for (Stat stat : Stat.values()) {
            levels.put(stat, 0);
            xp.put(stat, 0.0);
        }
        java.util.Arrays.fill(spellBar, "");
    }

    public int getWeakMagicLevel() {
        return weakMagicLevel;
    }

    public void addWeakMagicXp(double amount) {
        weakMagicXp += amount;
        while (weakMagicXp >= StatFormulas.xpForNextLevel(weakMagicLevel)) {
            weakMagicXp -= StatFormulas.xpForNextLevel(weakMagicLevel);
            weakMagicLevel++;
        }
    }

    public String[] getSpellBar() {
        return spellBar;
    }

    public void setSpellSlot(int slot, String spellName) {
        if (slot >= 0 && slot < spellBar.length) {
            spellBar[slot] = spellName == null ? "" : spellName;
        }
    }

    public int getLevel(Stat stat) {
        return levels.get(stat);
    }

    public double getXp(Stat stat) {
        return xp.get(stat);
    }

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

    /** Adds XP to a stat and returns how many levels it gained (usually 0). */
    public int addXp(Stat stat, double amount) {
        if (amount <= 0) {
            return 0;
        }
        double have = xp.get(stat) + amount;
        int level = levels.get(stat);
        int gained = 0;
        while (have >= StatFormulas.xpForNextLevel(level)) {
            have -= StatFormulas.xpForNextLevel(level);
            level++;
            gained++;
        }
        levels.put(stat, level);
        xp.put(stat, have);
        return gained;
    }

    // --- NBT ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Stat stat : Stat.values()) {
            tag.putInt(stat.name() + "_lvl", levels.get(stat));
            tag.putDouble(stat.name() + "_xp", xp.get(stat));
        }
        tag.putDouble("Stamina", stamina);
        tag.putDouble("Mana", mana);
        tag.putBoolean("Initialised", initialised);
        tag.putInt("WeakMagicLevel", weakMagicLevel);
        tag.putDouble("WeakMagicXp", weakMagicXp);
        for (int i = 0; i < spellBar.length; i++) {
            tag.putString("Spell" + i, spellBar[i]);
        }
        return tag;
    }

    public static PlayerStats load(CompoundTag tag) {
        PlayerStats stats = new PlayerStats();
        for (Stat stat : Stat.values()) {
            stats.levels.put(stat, tag.getInt(stat.name() + "_lvl"));
            stats.xp.put(stat, tag.getDouble(stat.name() + "_xp"));
        }
        stats.stamina = tag.getDouble("Stamina");
        stats.mana = tag.getDouble("Mana");
        stats.initialised = tag.getBoolean("Initialised");
        stats.weakMagicLevel = Math.max(1, tag.getInt("WeakMagicLevel"));
        stats.weakMagicXp = tag.getDouble("WeakMagicXp");
        for (int i = 0; i < stats.spellBar.length; i++) {
            stats.spellBar[i] = tag.getString("Spell" + i);
        }
        return stats;
    }
}
