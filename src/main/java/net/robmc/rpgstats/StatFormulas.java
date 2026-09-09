package net.robmc.rpgstats;

/**
 * Every number the RPG system uses, in one place. Tune here after seeing it in
 * game - nothing else has magic constants.
 *
 * The core idea: stats and spells both run 0..{@link #LEVEL_CAP}. How much of a
 * level-100 effect you actually get is {@link #effectiveness(int)} - a curve that
 * is front-loaded on purpose: ~75% by level 50, ~95% by 75, 100% only at the cap.
 * Getting the last 25 levels is a slog and barely moves the needle.
 */
public final class StatFormulas {

    private StatFormulas() {
    }

    // --- levelling ---

    /** Hard cap on every stat level and every spell level. All stats at the cap = a fully maxed character. */
    public static final int LEVEL_CAP = 100;

    // --- casting bars ---
    public static final int BAR_SLOTS = 9;                       // slots per bar
    public static final int BAR_COUNT = 2;                       // number of bars
    public static final int TOTAL_BAR_SLOTS = BAR_SLOTS * BAR_COUNT;

    /**
     * Fraction of a stat/spell's level-{@link #LEVEL_CAP} power available at {@code level}.
     * Piecewise-linear: 0 -&gt; 0.75 over levels 0-50, -&gt; 0.95 over 50-75, -&gt; 1.00 over 75-100.
     */
    public static double effectiveness(int level) {
        int l = Math.max(0, Math.min(level, LEVEL_CAP));
        if (l <= 50) {
            return 0.75 * (l / 50.0);
        } else if (l <= 75) {
            return 0.75 + 0.20 * ((l - 50) / 25.0);
        }
        return 0.95 + 0.05 * ((l - 75) / 25.0);
    }

    /** Percent form of {@link #effectiveness(int)}, for UI. */
    public static int effectivenessPercent(int level) {
        return (int) Math.round(effectiveness(level) * 100.0);
    }

    /** XP to go from {@code currentLevel} to the next. Gentle to 50, a grind to 75, a slog to 100. */
    public static final double XP_CURVE_BASE = 21.0;   // transfers (16 xp/cast): ~150 casts to 50, ~390 to 75, ~820 to 100

    public static double xpForNextLevel(int currentLevel) {
        double t = currentLevel / 25.0;
        return XP_CURVE_BASE * (1.0 + t * t);
    }

    // --- resource pools ---

    public static final double BASE_POOL = 300.0;                 // every pool with zero stats
    public static final double MAX_POOL = 450.0;                  // every pool with its stats at the cap
    private static final double POOL_SPAN = MAX_POOL - BASE_POOL;

    // Each pool's contributing stats are weighted to sum to 1.0, so all-stats-at-cap lands exactly on MAX_POOL.
    private static final double HP_W_STR = 0.5, HP_W_VIT = 0.5;
    private static final double STAM_W_VIT = 0.4, STAM_W_DEX = 0.2, STAM_W_QUI = 0.4;
    private static final double MANA_W_INT = 0.65, MANA_W_WIS = 0.35;

    public static double maxHealth(PlayerStats s) {
        return clampPool(BASE_POOL + POOL_SPAN
                * (HP_W_STR * eff(s, Stat.STRENGTH) + HP_W_VIT * eff(s, Stat.VITALITY)));
    }

    public static double maxStamina(PlayerStats s) {
        return clampPool(BASE_POOL + POOL_SPAN
                * (STAM_W_VIT * eff(s, Stat.VITALITY)
                + STAM_W_DEX * eff(s, Stat.DEXTERITY)
                + STAM_W_QUI * eff(s, Stat.QUICKNESS)));
    }

    public static double maxMana(PlayerStats s) {
        return clampPool(BASE_POOL + POOL_SPAN
                * (MANA_W_INT * eff(s, Stat.INTELLIGENCE) + MANA_W_WIS * eff(s, Stat.WISDOM)));
    }

    private static double eff(PlayerStats s, Stat stat) {
        return effectiveness(s.getLevel(stat));
    }

    private static double clampPool(double v) {
        return Math.max(BASE_POOL, Math.min(v, MAX_POOL));
    }

    // --- combat: the full bonus lands only at LEVEL_CAP, scaled down the curve everywhere else ---

    private static final double MELEE_MAX_BONUS = 1.00;   // +100% melee damage at STR cap
    private static final double RANGED_MAX_BONUS = 1.00;  // +100% ranged damage at DEX cap
    private static final double SPELL_MAX_BONUS = 1.00;   // +100% spell damage at INT cap
    private static final double CAST_MAX_CUT = 0.45;      // -45% cast time at INT cap
    private static final double SPELL_RESIST_MAX = 0.25;  // -25% incoming spell damage at DEX cap
    private static final double MOVE_BONUS_MAX = 0.25;    // +25% move speed at QUI cap
    private static final double REGEN_BONUS_MAX = 1.00;   // +100% stamina/mana regen at QUI/WIS cap

    public static double meleeDamageMultiplier(PlayerStats s) {
        return 1.0 + MELEE_MAX_BONUS * eff(s, Stat.STRENGTH);
    }

    public static double rangedDamageMultiplier(PlayerStats s) {
        return 1.0 + RANGED_MAX_BONUS * eff(s, Stat.DEXTERITY);
    }

    public static double spellDamageMultiplier(PlayerStats s) {
        return 1.0 + SPELL_MAX_BONUS * eff(s, Stat.INTELLIGENCE);
    }

    /** Cast-time multiplier (&lt;1 = faster). Floors at 0.5x. */
    public static double castSpeedMultiplier(PlayerStats s) {
        return Math.max(0.5, 1.0 - CAST_MAX_CUT * eff(s, Stat.INTELLIGENCE));
    }

    /** Fraction of incoming spell damage removed by Dexterity. */
    public static double spellDamageResist(PlayerStats s) {
        return SPELL_RESIST_MAX * eff(s, Stat.DEXTERITY);
    }

    /** Bonus movement speed as a fraction of base. */
    public static double moveSpeedBonus(PlayerStats s) {
        return MOVE_BONUS_MAX * eff(s, Stat.QUICKNESS);
    }

    // --- natural regen, per regen tick (see REGEN_INTERVAL_TICKS) ---

    public static final int REGEN_INTERVAL_TICKS = 40;   // ~2 s
    public static final double HP_REGEN_PER_TICK = 1.5;
    public static final double STAMINA_REGEN_PER_TICK = 2.0;
    public static final double MANA_REGEN_PER_TICK = 1.5;

    public static double staminaRegen(PlayerStats s) {
        return STAMINA_REGEN_PER_TICK * (1.0 + REGEN_BONUS_MAX * eff(s, Stat.QUICKNESS));
    }

    public static double manaRegen(PlayerStats s) {
        return MANA_REGEN_PER_TICK * (1.0 + REGEN_BONUS_MAX * eff(s, Stat.WISDOM));
    }

    // --- stamina cost of physical actions (raw pool points) ---

    public static final double STAMINA_WALK_PER_TICK = 0.08;     // ~1.6 / second just walking around
    public static final double STAMINA_SPRINT_PER_TICK = 0.5;    // ~10 / second while sprinting
    public static final double STAMINA_JUMP = 3.0;               // per jump
    public static final double STAMINA_MELEE_SWING = 6.0;        // per landed hit with a weapon

    // --- exhausted state (stamina bottomed out) ---
    /** Exhausted at 0 stamina; stays exhausted until stamina climbs back to this fraction of max. */
    public static final double EXHAUSTION_RECOVER_FRACTION = 0.15;
    /** Slowness level applied while exhausted (0 = Slowness I; 3 = Slowness IV, roughly -60% move speed). */
    public static final int EXHAUSTION_SLOWNESS_AMPLIFIER = 3;

    // --- taking a hit also bleeds stamina/mana ---

    /** Fraction of damage taken (from a mob or player) that is also drained from stamina + mana. */
    public static final double DAMAGE_POOL_LEECH_FRACTION = 0.08;
    /** Of that leech, how much comes out of stamina (the rest out of mana), by damage kind. */
    public static final double LEECH_PHYSICAL_STAMINA = 0.80;
    public static final double LEECH_PROJECTILE_STAMINA = 0.55;
    public static final double LEECH_MAGIC_STAMINA = 0.20;
    /** Vanilla blocks sprinting below 6 food and self-heals at/above 18; hold food here so it does neither. */
    public static final int PINNED_FOOD_LEVEL = 17;

    // --- transfer spells: the spell's own level scales both how much moves and the return rate ---

    public static final int TRANSFER_DURATION_TICKS = 40;         // the gain is paid out over ~2 seconds
    private static final double TRANSFER_AMOUNT_MIN = 12.0;       // spent per cast at spell level 0
    private static final double TRANSFER_AMOUNT_MAX = 26.0;       // ... at the level cap
    private static final double TRANSFER_RATIO_MIN = 0.75;        // level 0: you LOSE value in the exchange
    private static final double TRANSFER_RATIO_MAX = 1.25;        // level cap: a modest net gain, not a combat crutch

    public static double transferAmount(int spellLevel) {
        return TRANSFER_AMOUNT_MIN + (TRANSFER_AMOUNT_MAX - TRANSFER_AMOUNT_MIN) * effectiveness(spellLevel);
    }

    public static double transferRatio(int spellLevel) {
        return TRANSFER_RATIO_MIN + (TRANSFER_RATIO_MAX - TRANSFER_RATIO_MIN) * effectiveness(spellLevel);
    }

    // --- magic bolt ---

    public static final double MAGIC_BOLT_SPLASH_RADIUS = 3.0;    // blocks around the impact
    public static final double MAGIC_BOLT_SPLASH_FRACTION = 0.4;  // splash victims take 40% of a direct hit
    private static final double MAGIC_BOLT_BASE_DAMAGE = 3.0;     // raw (pre-DAMAGE_SCALE), at spell level 0
    private static final double MAGIC_BOLT_LEVEL_DAMAGE = 6.0;    // extra raw damage at the spell level cap
    private static final double MAGIC_BOLT_SPEED_MIN = 0.55;      // blocks/tick at spell level 0 (deliberately slow)
    private static final double MAGIC_BOLT_SPEED_MAX = 1.15;      // ... at the cap; the curve is front-loaded

    public static double magicBoltDamage(int spellLevel, PlayerStats caster) {
        return (MAGIC_BOLT_BASE_DAMAGE + MAGIC_BOLT_LEVEL_DAMAGE * effectiveness(spellLevel))
                * spellDamageMultiplier(caster);
    }

    public static double magicBoltSpeed(int spellLevel) {
        return MAGIC_BOLT_SPEED_MIN + (MAGIC_BOLT_SPEED_MAX - MAGIC_BOLT_SPEED_MIN) * effectiveness(spellLevel);
    }

    // --- Rest skill (a semi-AFK downtime action; deliberately mild) ---

    public static final int REST_REGEN_INTERVAL_TICKS = 20;      // rest tops your pools up every ~1s
    public static final double REST_BREAK_DISTANCE = 0.4;        // move this far from where you sat -> you stand up
    public static final double XP_REST_TICK = 4.0;               // Rest xp per regen tick while resting
    private static final double REST_MULT_MIN = 2.0;             // pools regen this many x faster at Rest level 0
    private static final double REST_MULT_MAX = 3.5;             // ... at the level cap - still modest, not a heal button

    /** Multiplier on natural regen while resting, scaled by Rest level. */
    public static double restRegenMultiplier(int restLevel) {
        return REST_MULT_MIN + (REST_MULT_MAX - REST_MULT_MIN) * effectiveness(restLevel);
    }

    // --- damage scaling ---

    /** All damage on the 20-HP scale is multiplied by this to fit the ~300-450 pool. */
    public static final double DAMAGE_SCALE = 15.0;

    // --- XP granted per action (tune freely) ---

    public static final double XP_MINE_BLOCK = 1.0;
    public static final double XP_CHOP_LOG = 2.5;
    public static final double XP_MELEE_HIT = 2.0;
    public static final double XP_RANGED_HIT = 3.0;
    public static final double XP_MOVE_PER_METRE = 0.05;
    public static final double XP_SWIM_PER_METRE = 0.12;
    public static final double XP_CAST_SPELL = 6.0;        // per cast, damage spells
    public static final double XP_CAST_TRANSFER = 16.0;    // per cast, Weak Magic transfers - they level noticeably faster
    public static final double XP_FISH_CATCH = 6.0;
    public static final double XP_HARVEST_CROP = 2.0;
    public static final double XP_ENCHANT = 15.0;
}
