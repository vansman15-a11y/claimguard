package net.robmc.combat;

/** Every tuning number for Reforged Melee, in one place. */
public final class CombatConfig {

    private CombatConfig() {
    }

    // --- swing speed ---
    /** Multiplier taken off ATTACK_SPEED while a melee weapon is held (0.48 = swing 48% slower). */
    public static final double SWING_SLOWDOWN = 0.48;
    /** Hard floor between swings, regardless of attack-speed charge - stops click-spamming from bypassing it. */
    public static final int SWING_COOLDOWN_TICKS = 30; // 1.5s

    // --- parry ---
    /** Damage reduction while holding right-click with any melee weapon (not a staff - see isMeleeWeapon) alone. */
    public static final double PARRY_WEAPON_REDUCTION = 0.35;
    /** Damage reduction while holding right-click with a melee weapon AND a shield in the offhand. */
    public static final double PARRY_SHIELD_REDUCTION = 0.75;

    // --- cleave arc ---
    /** How far in front of you the cleave reaches (blocks). Vanilla melee is ~3. */
    public static final double ARC_RANGE = 4.6;
    /** Half-angle of the cleave cone, in degrees (so 65 = a 130-degree fan). */
    public static final double ARC_HALF_ANGLE_DEG = 65.0;
    /** Fraction of your swing damage that the extra cleaved targets take (the one you aimed at takes full). */
    public static final double ARC_SECONDARY_MULT = 0.8;
    /** Only cleave once your attack cooldown is at least this charged (1.0 = fully rested). */
    public static final float ARC_MIN_CHARGE = 0.85f;
    /** Most entities one swing can cleave (the primary target not counted). */
    public static final int ARC_MAX_TARGETS = 5;

    // --- combo crit ---
    /** Land this many melee hits in a row and the next one crits. */
    public static final int COMBO_HITS_FOR_CRIT = 5;
    /** The combo resets if you go this many ticks without landing a hit. */
    public static final int COMBO_TIMEOUT_TICKS = 80;
    /** Damage multiplier on the combo finisher. */
    public static final double COMBO_CRIT_MULT = 1.6;
}
