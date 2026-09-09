package net.robmc.rpgstats;

/**
 * Every number the RPG system uses, in one place. Tune here after seeing it in
 * game - nothing else has magic constants.
 */
public final class StatFormulas {

    private StatFormulas() {
    }

    // --- resource pools ---
    public static final double BASE_POOL = 300.0;  // pool size with zero stats (roughly where new players start)
    public static final double MAX_POOL = 450.0;   // hard cap on every pool

    // per-stat-point contribution to each pool
    private static final double HP_PER_STR = 1.2;
    private static final double HP_PER_VIT = 1.2;
    private static final double STAM_PER_VIT = 1.0;
    private static final double STAM_PER_DEX = 0.35;
    private static final double STAM_PER_QUI = 0.55;
    private static final double MANA_PER_INT = 1.3;
    private static final double MANA_PER_WIS = 0.5;

    public static double maxHealth(PlayerStats s) {
        return clampPool(BASE_POOL
                + HP_PER_STR * s.getLevel(Stat.STRENGTH)
                + HP_PER_VIT * s.getLevel(Stat.VITALITY));
    }

    public static double maxStamina(PlayerStats s) {
        return clampPool(BASE_POOL
                + STAM_PER_VIT * s.getLevel(Stat.VITALITY)
                + STAM_PER_DEX * s.getLevel(Stat.DEXTERITY)
                + STAM_PER_QUI * s.getLevel(Stat.QUICKNESS));
    }

    public static double maxMana(PlayerStats s) {
        return clampPool(BASE_POOL
                + MANA_PER_INT * s.getLevel(Stat.INTELLIGENCE)
                + MANA_PER_WIS * s.getLevel(Stat.WISDOM));
    }

    private static double clampPool(double v) {
        return Math.max(BASE_POOL, Math.min(v, MAX_POOL));
    }

    // --- damage multipliers (applied in RpgEvents / RpgCombat) ---

    /** All damage on the 20-HP scale is multiplied by this to fit the ~300-450 pool. */
    public static final double DAMAGE_SCALE = 15.0;

    public static double meleeDamageMultiplier(PlayerStats s) {
        return 1.0 + 0.010 * s.getLevel(Stat.STRENGTH);   // +1% melee per Strength point
    }

    public static double rangedDamageMultiplier(PlayerStats s) {
        return 1.0 + 0.010 * s.getLevel(Stat.DEXTERITY);
    }

    public static double spellDamageMultiplier(PlayerStats s) {
        return 1.0 + 0.012 * s.getLevel(Stat.INTELLIGENCE);
    }

    /** Cast-time multiplier (<1 = faster). Floors at 0.5x. */
    public static double castSpeedMultiplier(PlayerStats s) {
        return Math.max(0.5, 1.0 - 0.006 * s.getLevel(Stat.INTELLIGENCE));
    }

    /** Fraction of incoming spell damage removed by Dexterity (small). Caps at 20%. */
    public static double spellDamageResist(PlayerStats s) {
        return Math.min(0.20, 0.003 * s.getLevel(Stat.DEXTERITY));
    }

    // --- natural regen, per regen tick (see RpgEvents.REGEN_INTERVAL_TICKS) ---
    public static final int REGEN_INTERVAL_TICKS = 40;   // ~2 s
    public static final double HP_REGEN_PER_TICK = 1.5;
    public static final double STAMINA_REGEN_PER_TICK = 2.0;
    public static final double MANA_REGEN_PER_TICK = 1.5;
    /** Quickness speeds stamina regen. */
    public static double staminaRegen(PlayerStats s) {
        return STAMINA_REGEN_PER_TICK * (1.0 + 0.02 * s.getLevel(Stat.QUICKNESS));
    }
    /** Wisdom speeds mana regen. */
    public static double manaRegen(PlayerStats s) {
        return MANA_REGEN_PER_TICK * (1.0 + 0.02 * s.getLevel(Stat.WISDOM));
    }

    // --- Quickness movement bonus (attribute modifier) ---
    /** Bonus movement speed as a fraction of base, capped at +30%. */
    public static double moveSpeedBonus(PlayerStats s) {
        return Math.min(0.30, 0.002 * s.getLevel(Stat.QUICKNESS));
    }

    // --- XP ---

    /** XP needed to go from the given level to the next. Gently rising cost. */
    public static double xpForNextLevel(int currentLevel) {
        return 80.0 + 12.0 * currentLevel;
    }

    // --- spells (see magic/) ---
    public static final double TRANSFER_AMOUNT = 40.0;   // per cast: spend up to this, gain 2x
    public static final double MAGIC_BOLT_BASE_DAMAGE = 3.0;  // raw (pre-DAMAGE_SCALE) - deliberately weak
    public static final double MAGIC_BOLT_RANGE = 24.0;

    // XP granted per action (tune freely)
    public static final double XP_MINE_BLOCK = 1.0;
    public static final double XP_CHOP_LOG = 2.5;
    public static final double XP_MELEE_HIT = 2.0;
    public static final double XP_RANGED_HIT = 3.0;
    public static final double XP_MOVE_PER_METRE = 0.05;
    public static final double XP_SWIM_PER_METRE = 0.12;
    public static final double XP_CAST_SPELL = 4.0;
    public static final double XP_FISH_CATCH = 6.0;
    public static final double XP_HARVEST_CROP = 2.0;
    public static final double XP_ENCHANT = 15.0;
}
