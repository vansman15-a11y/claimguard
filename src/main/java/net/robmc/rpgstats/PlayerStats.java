package net.robmc.rpgstats;

import net.minecraft.nbt.CompoundTag;
import net.robmc.rpgstats.magic.School;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    /** Per-spell "auto-equip this weapon before casting" preference - the item's registry id, e.g. "rpgstats:cobra_staff". */
    private final Map<Spell, String> weaponPrefs = new EnumMap<>(Spell.class);

    private final Map<School, Integer> schoolLevels = new EnumMap<>(School.class);
    private final Map<School, Double> schoolXp = new EnumMap<>(School.class);

    private final Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Double> skillXp = new EnumMap<>(Skill.class);

    private double stamina = StatFormulas.BASE_POOL;
    private double mana = StatFormulas.BASE_POOL;
    private boolean initialised = false;

    /** Battle Hymn's temporary +N to Strength / Vitality / Quickness (0 = not buffed). Not persisted. */
    private int hymnBonus = 0;

    /** Summed encumbrance of the 4 worn armour pieces. Derived, refreshed by RpgManager.stats() - not persisted. */
    private double armorEncumbrance = 0.0;

    /** The two casting bars' slots (bar 1 = 0..8, bar 2 = 9..17), each holding a Spell/Skill enum name or "". */
    private final String[] spellBar = new String[StatFormulas.TOTAL_BAR_SLOTS];

    /** How a multi-bind slot picks which of its stacked spells fires next. */
    public enum CycleMode { CYCLE, FIRST_AVAILABLE }

    /**
     * A slot can hold more than one spell (a "ray bar") - spellBar[slot] is always the
     * first/primary one, this holds any additional ones stacked on top of it, in the
     * order they were added. Empty for every plain single-bind slot.
     */
    private final List<List<String>> slotExtra = new ArrayList<>();
    private final CycleMode[] slotCycleMode = new CycleMode[StatFormulas.TOTAL_BAR_SLOTS];
    private final boolean[] slotAutoCast = new boolean[StatFormulas.TOTAL_BAR_SLOTS];
    /** Per-slot forced weapon (overrides any per-spell weaponPref while this slot is what's firing) - registry id, "" = none. */
    private final String[] slotWeaponId = new String[StatFormulas.TOTAL_BAR_SLOTS];
    private final boolean[] slotForceWeapon = new boolean[StatFormulas.TOTAL_BAR_SLOTS];
    /** Which bound spell a CYCLE-mode slot fires next. Not persisted - just resets to the top on relog. */
    private final int[] slotCursor = new int[StatFormulas.TOTAL_BAR_SLOTS];

    public PlayerStats() {
        for (Stat stat : Stat.values()) {
            levels.put(stat, 0);
            xp.put(stat, 0.0);
        }
        for (Spell spell : Spell.values()) {
            spellLevels.put(spell, 0);
            spellXp.put(spell, 0.0);
        }
        for (School school : School.values()) {
            schoolLevels.put(school, 1); // every school starts at 1 - tier-1 spells are usable immediately
            schoolXp.put(school, 0.0);
        }
        for (Skill skill : Skill.values()) {
            skillLevels.put(skill, 0);
            skillXp.put(skill, 0.0);
        }
        java.util.Arrays.fill(spellBar, "");
        java.util.Arrays.fill(slotWeaponId, "");
        java.util.Arrays.fill(slotCycleMode, CycleMode.CYCLE);
        for (int i = 0; i < StatFormulas.TOTAL_BAR_SLOTS; i++) {
            slotExtra.add(new ArrayList<>());
        }
    }

    // --- stats ---

    public int getLevel(Stat stat) {
        int base = levels.get(stat);
        if (hymnBonus > 0 && (stat == Stat.STRENGTH || stat == Stat.VITALITY || stat == Stat.QUICKNESS)) {
            base += hymnBonus;
        }
        return Math.min(base, StatFormulas.LEVEL_CAP);
    }

    /** The stat level as trained, ignoring any Battle Hymn buff (for XP / level-up / save). */
    public int getBaseLevel(Stat stat) {
        return levels.get(stat);
    }

    public void setHymnBonus(int bonus) {
        this.hymnBonus = Math.max(0, bonus);
    }

    public boolean hasHymn() {
        return hymnBonus > 0;
    }

    public double getArmorEncumbrance() {
        return armorEncumbrance;
    }

    public void setArmorEncumbrance(double value) {
        this.armorEncumbrance = Math.max(0.0, value);
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

    // --- individual spells ---

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

    public int[] spellLevelArray() {
        Spell[] all = Spell.values();
        int[] out = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = spellLevels.getOrDefault(all[i], 0);
        }
        return out;
    }

    // --- magic schools ---

    public int getSchoolLevel(School school) {
        return schoolLevels.getOrDefault(school, 0);
    }

    public double getSchoolXp(School school) {
        return schoolXp.getOrDefault(school, 0.0);
    }

    // --- admin/testing setters (xp reset) ---

    public void setSpellLevel(Spell spell, int level) {
        spellLevels.put(spell, Math.max(0, Math.min(level, StatFormulas.LEVEL_CAP)));
        spellXp.put(spell, 0.0);
    }

    public void setSchoolLevel(School school, int level) {
        schoolLevels.put(school, Math.max(1, Math.min(level, StatFormulas.LEVEL_CAP)));
        schoolXp.put(school, 0.0);
    }

    public void setStatLevel(Stat stat, int level) {
        levels.put(stat, Math.max(0, Math.min(level, StatFormulas.LEVEL_CAP)));
        xp.put(stat, 0.0);
    }

    public void setSkillLevel(Skill skill, int level) {
        skillLevels.put(skill, Math.max(0, Math.min(level, StatFormulas.LEVEL_CAP)));
        skillXp.put(skill, 0.0);
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
        } else {
            return;
        }
        // replacing the slot wholesale drops any stacked "ray bar" extras and resets the rotation
        slotExtra.get(slot).clear();
        slotCursor[slot] = 0;
    }

    // --- multi-bind ("ray bar") slots ---

    /** Every spell bound to this slot, primary first, in add order. Empty if the slot is empty. */
    public List<String> getSlotBinds(int slot) {
        if (slot < 0 || slot >= spellBar.length) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (!spellBar[slot].isEmpty()) {
            out.add(spellBar[slot]);
        }
        for (String extra : slotExtra.get(slot)) {
            if (!extra.isEmpty() && !out.contains(extra)) {
                out.add(extra);
            }
        }
        return out;
    }

    /** Stack another spell onto this slot instead of replacing it (a Shift-drop in the Spellbook). */
    public void addSlotBind(int slot, String name) {
        if (slot < 0 || slot >= spellBar.length || name == null || name.isEmpty() || Spell.byName(name) == null) {
            return;
        }
        if (spellBar[slot].isEmpty()) {
            spellBar[slot] = name;
            return;
        }
        if (name.equals(spellBar[slot]) || getSlotBinds(slot).size() >= StatFormulas.MAX_SLOT_BINDS) {
            return;
        }
        List<String> extra = slotExtra.get(slot);
        if (!extra.contains(name)) {
            extra.add(name);
            // stacking a 2nd+ spell onto a slot turns it into a ray bar right away - no separate
            // opt-in needed. Auto Cast stays available in the J editor to pause the rotation later.
            setSlotAutoCast(slot, true);
        }
    }

    /**
     * Replace this slot's whole bind list with {@code names}, in that exact order - the
     * Spellbook's expanded-slot view uses this both to drop one specific spell out of a stack
     * and to reorder the stack (which one a Cycle-mode press reaches first). Invalid/duplicate
     * names are dropped rather than rejecting the whole call.
     */
    public void setSlotBindOrder(int slot, List<String> names) {
        if (slot < 0 || slot >= spellBar.length || names == null) {
            return;
        }
        List<String> valid = new ArrayList<>();
        for (String name : names) {
            if (name != null && !name.isEmpty() && Spell.byName(name) != null
                    && !valid.contains(name) && valid.size() < StatFormulas.MAX_SLOT_BINDS) {
                valid.add(name);
            }
        }
        spellBar[slot] = valid.isEmpty() ? "" : valid.get(0);
        slotExtra.get(slot).clear();
        if (valid.size() > 1) {
            slotExtra.get(slot).addAll(valid.subList(1, valid.size()));
            setSlotAutoCast(slot, true);
        }
        slotCursor[slot] = 0;
    }

    /** Pop the most recently added extra spell off this slot, or clear it entirely if it's down to just the primary. */
    public void popSlotBind(int slot) {
        if (slot < 0 || slot >= spellBar.length) {
            return;
        }
        List<String> extra = slotExtra.get(slot);
        if (!extra.isEmpty()) {
            extra.remove(extra.size() - 1);
        } else {
            setSpellSlot(slot, "");
        }
    }

    public CycleMode getSlotCycleMode(int slot) {
        return slot >= 0 && slot < slotCycleMode.length ? slotCycleMode[slot] : CycleMode.CYCLE;
    }

    public void setSlotCycleMode(int slot, CycleMode mode) {
        if (slot >= 0 && slot < slotCycleMode.length) {
            slotCycleMode[slot] = mode;
        }
    }

    public boolean isSlotAutoCast(int slot) {
        return slot >= 0 && slot < slotAutoCast.length && slotAutoCast[slot];
    }

    public void setSlotAutoCast(int slot, boolean value) {
        if (slot >= 0 && slot < slotAutoCast.length) {
            slotAutoCast[slot] = value;
        }
    }

    /** The item registry id this slot force-equips before casting whatever fires from it, or "" if none. */
    public String getSlotWeaponId(int slot) {
        return slot >= 0 && slot < slotWeaponId.length ? slotWeaponId[slot] : "";
    }

    public void setSlotWeaponId(int slot, String itemId) {
        if (slot >= 0 && slot < slotWeaponId.length) {
            slotWeaponId[slot] = itemId == null ? "" : itemId;
        }
    }

    public boolean isSlotForceWeapon(int slot) {
        return slot >= 0 && slot < slotForceWeapon.length && slotForceWeapon[slot];
    }

    public void setSlotForceWeapon(int slot, boolean value) {
        if (slot >= 0 && slot < slotForceWeapon.length) {
            slotForceWeapon[slot] = value;
        }
    }

    /** Which of this slot's bound spells a CYCLE-mode press fires next. */
    public int getSlotCursor(int slot) {
        return slot >= 0 && slot < slotCursor.length ? slotCursor[slot] : 0;
    }

    public void advanceSlotCursor(int slot, int bindCount) {
        if (slot >= 0 && slot < slotCursor.length && bindCount > 0) {
            slotCursor[slot] = (slotCursor[slot] + 1) % bindCount;
        }
    }

    // --- per-spell weapon auto-equip preference ---

    /** The item registry id (e.g. "rpgstats:cobra_staff") to auto-equip before casting this spell, or null. */
    public String getWeaponPref(Spell spell) {
        return weaponPrefs.get(spell);
    }

    public void setWeaponPref(Spell spell, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            weaponPrefs.remove(spell);
        } else {
            weaponPrefs.put(spell, itemId);
        }
    }

    /** Indexed by Spell.ordinal(), "" where no preference is set - for SyncSpellBarPacket. */
    public String[] weaponPrefArray() {
        String[] arr = new String[Spell.values().length];
        for (Spell sp : Spell.values()) {
            arr[sp.ordinal()] = weaponPrefs.getOrDefault(sp, "");
        }
        return arr;
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
        CompoundTag weapons = new CompoundTag();
        weaponPrefs.forEach((spell, id) -> weapons.putString(spell.name(), id));
        tag.put("WeaponPrefs", weapons);

        CompoundTag slots = new CompoundTag();
        for (int i = 0; i < StatFormulas.TOTAL_BAR_SLOTS; i++) {
            if (!slotExtra.get(i).isEmpty()) {
                slots.putString("Extra" + i, String.join(";", slotExtra.get(i)));
            }
            if (slotCycleMode[i] != CycleMode.CYCLE) {
                slots.putString("Mode" + i, slotCycleMode[i].name());
            }
            if (slotAutoCast[i]) {
                slots.putBoolean("Auto" + i, true);
            }
            if (!slotWeaponId[i].isEmpty()) {
                slots.putString("Weapon" + i, slotWeaponId[i]);
            }
            if (slotForceWeapon[i]) {
                slots.putBoolean("ForceWeapon" + i, true);
            }
        }
        tag.put("SlotOptions", slots);
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
        for (School school : School.values()) {
            stats.schoolLevels.put(school, Math.max(1, clampLevel(tag.getInt("SchoolLvl_" + school.name()))));
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
        if (tag.contains("WeaponPrefs")) {
            CompoundTag weapons = tag.getCompound("WeaponPrefs");
            for (String key : weapons.getAllKeys()) {
                Spell spell = Spell.byName(key);
                if (spell != null) {
                    stats.weaponPrefs.put(spell, weapons.getString(key));
                }
            }
        }
        if (tag.contains("SlotOptions")) {
            CompoundTag slots = tag.getCompound("SlotOptions");
            for (int i = 0; i < StatFormulas.TOTAL_BAR_SLOTS; i++) {
                if (slots.contains("Extra" + i)) {
                    String joined = slots.getString("Extra" + i);
                    if (!joined.isEmpty()) {
                        for (String name : joined.split(";")) {
                            if (Spell.byName(name) != null) {
                                stats.slotExtra.get(i).add(name);
                            }
                        }
                    }
                }
                if (slots.contains("Mode" + i)) {
                    try {
                        stats.slotCycleMode[i] = CycleMode.valueOf(slots.getString("Mode" + i));
                    } catch (IllegalArgumentException ignored) {
                        // keep the default (CYCLE)
                    }
                }
                stats.slotAutoCast[i] = slots.getBoolean("Auto" + i);
                if (slots.contains("Weapon" + i)) {
                    stats.slotWeaponId[i] = slots.getString("Weapon" + i);
                }
                stats.slotForceWeapon[i] = slots.getBoolean("ForceWeapon" + i);
            }
        }
        return stats;
    }

    private static int clampLevel(int v) {
        return Math.max(0, Math.min(v, StatFormulas.LEVEL_CAP));
    }
}
