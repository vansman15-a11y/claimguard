package net.robmc.rpgstats;

import net.minecraft.nbt.CompoundTag;
import net.robmc.rpgstats.magic.School;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

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

    private final Map<School, Integer> schoolLevels = new EnumMap<>(School.class);
    private final Map<School, Double> schoolXp = new EnumMap<>(School.class);

    private final Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Double> skillXp = new EnumMap<>(Skill.class);

    private double stamina = StatFormulas.BASE_POOL;
    private double mana = StatFormulas.BASE_POOL;
    private boolean initialised = false;

    /** The two casting bars' slots (bar 1 = 0..8, bar 2 = 9..17), each holding a Spell/Skill enum name or "". */
    private final String[] spellBar = new String[StatFormulas.TOTAL_BAR_SLOTS];

    public PlayerStats() {
        for (Stat stat : Stat.values()) {
            levels.put(stat, 0);
            xp.put(stat, 0.0);
        }
        for (School school : School.values()) {
            schoolLevels.put(school, 0);
            schoolXp.put(school, 0.0);
        }
        for (Skill skill : Skill.values()) {
            skillLevels.put(skill, 0);
            skillXp.put(skill, 0.0);
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

    // --- magic schools ---

    public int getSchoolLevel(School school) {
        return schoolLevels.getOrDefault(school, 0);
    }

    /** Convenience: the caster's level in this spell's school. */
    public int getSpellLevel(Spell spell) {
        return getSchoolLevel(spell.school());
    }

    public double getSchoolXp(School school) {
        return schoolXp.getOrDefault(school, 0.0);
    }

    /** Adds XP to a school and returns how many levels it gained. Stops at LEVEL_CAP. */
    public int addSchoolXp(School school, double amount) {
        if (amount <= 0) {
            return 0;
        }
        int level = schoolLevels.getOrDefault(school, 0);
        if (level >= StatFormulas.LEVEL_CAP) {
            return 0;
        }
        double have = schoolXp.getOrDefault(school, 0.0) + amount;
        int gained = 0;
        while (level < StatFormulas.LEVEL_CAP && have >= StatFormulas.xpForNextLevel(level)) {
            have -= StatFormulas.xpForNextLevel(level);
            level++;
            gained++;
        }
        if (level >= StatFormulas.LEVEL_CAP) {
            have = 0;
        }
        schoolLevels.put(school, level);
        schoolXp.put(school, have);
        return gained;
    }

    /** School levels indexed by School.ordinal(), for the sync packet. */
    public int[] schoolLevelArray() {
        School[] all = School.values();
        int[] out = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = schoolLevels.getOrDefault(all[i], 0);
        }
        return out;
    }

    // --- skills ---

    public int getSkillLevel(Skill skill) {
        return skillLevels.getOrDefault(skill, 0);
    }

    public double getSkillXp(Skill skill) {
        return skillXp.getOrDefault(skill, 0.0);
    }

    /** Adds XP to one skill and returns how many levels it gained. Stops at LEVEL_CAP. */
    public int addSkillXp(Skill skill, double amount) {
        if (amount <= 0) {
            return 0;
        }
        int level = skillLevels.getOrDefault(skill, 0);
        if (level >= StatFormulas.LEVEL_CAP) {
            return 0;
        }
        double have = skillXp.getOrDefault(skill, 0.0) + amount;
        int gained = 0;
        while (level < StatFormulas.LEVEL_CAP && have >= StatFormulas.xpForNextLevel(level)) {
            have -= StatFormulas.xpForNextLevel(level);
            level++;
            gained++;
        }
        if (level >= StatFormulas.LEVEL_CAP) {
            have = 0;
        }
        skillLevels.put(skill, level);
        skillXp.put(skill, have);
        return gained;
    }

    /** Skill levels indexed by Skill.ordinal(), for the sync packet. */
    public int[] skillLevelArray() {
        Skill[] all = Skill.values();
        int[] out = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = skillLevels.getOrDefault(all[i], 0);
        }
        return out;
    }

    // --- spell bar ---

    public String[] getSpellBar() {
        return spellBar;
    }

    public void setSpellSlot(int slot, String name) {
        if (slot < 0 || slot >= spellBar.length) {
            return;
        }
        // only a real spell name or a clear - never arbitrary client text (skills aren't spells)
        if (name == null || name.isEmpty()) {
            spellBar[slot] = "";
        } else if (Spell.byName(name) != null) {
            spellBar[slot] = name;
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
        for (School school : School.values()) {
            tag.putInt("SchoolLvl_" + school.name(), schoolLevels.getOrDefault(school, 0));
            tag.putDouble("SchoolXp_" + school.name(), schoolXp.getOrDefault(school, 0.0));
        }
        for (Skill skill : Skill.values()) {
            tag.putInt("SkillLvl_" + skill.name(), skillLevels.getOrDefault(skill, 0));
            tag.putDouble("SkillXp_" + skill.name(), skillXp.getOrDefault(skill, 0.0));
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
        for (School school : School.values()) {
            stats.schoolLevels.put(school, clampLevel(tag.getInt("SchoolLvl_" + school.name())));
            stats.schoolXp.put(school, tag.getDouble("SchoolXp_" + school.name()));
        }
        for (Skill skill : Skill.values()) {
            stats.skillLevels.put(skill, clampLevel(tag.getInt("SkillLvl_" + skill.name())));
            stats.skillXp.put(skill, tag.getDouble("SkillXp_" + skill.name()));
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
