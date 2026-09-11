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

    public static final double STAMINA_WALK_PER_TICK = 0.04;     // ~0.8 / second - roughly cancels natural regen
    public static final double STAMINA_SPRINT_PER_TICK = 0.4;    // ~8 / second while sprinting
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
    /** A fully-charged spell fires on its own after this long if you never let go of the key. */
    public static final int CHARGED_MAX_HOLD_TICKS = 80;
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

    // --- Adept Magic ---

    // Sunder: a slow bolt that makes the target bleed
    public static final double SUNDER_SPEED = 0.55;               // magic-bolt slow
    public static final double SUNDER_IMPACT_DAMAGE = 1.25;      // raw, small

    /** Offensive spell impacts within this of the caster also catch the caster. */
    public static final double SPELL_SELF_HIT_RADIUS = 2.5;
    public static final int SUNDER_BLEED_TICKS = 80;              // 4 s
    public static final int SUNDER_BLEED_INTERVAL = 20;           // damage every 1 s (4 hits total)
    private static final double SUNDER_BLEED_MIN = 0.5;          // raw per tick at school level 1 - low-end spell
    private static final double SUNDER_BLEED_MAX = 1.4;          // ... at the cap

    public static double sunderBleedPerTick(int schoolLevel) {
        return SUNDER_BLEED_MIN + (SUNDER_BLEED_MAX - SUNDER_BLEED_MIN) * effectiveness(schoolLevel);
    }

    // Heal Other: near-hitscan, heals what it hits, costs the caster mana
    public static final double HEAL_OTHER_SPEED = 2.6;
    private static final double HEAL_OTHER_MIN = 18.0;           // healed at school level 1
    private static final double HEAL_OTHER_MAX = 45.0;          // ... at the cap

    public static double healOtherAmount(int schoolLevel) {
        return HEAL_OTHER_MIN + (HEAL_OTHER_MAX - HEAL_OTHER_MIN) * effectiveness(schoolLevel);
    }

    // Away: knock the target back; cast at your feet for a short speed/hop buff
    public static final double AWAY_SPEED = 0.75;                 // a touch quicker than magic bolt
    public static final double AWAY_IMPACT_DAMAGE = 1.0;
    public static final double AWAY_KNOCKBACK = 1.4;
    public static final int AWAY_SELF_BUFF_TICKS = 100;          // 5 s
    public static final double AWAY_SELF_RANGE = 3.5;            // impact within this of the caster = self-buff

    // Ward: instant self-cast damage shield (vanilla Absorption)
    public static final int WARD_DURATION_TICKS = 160;           // the shield lingers up to 8 s
    private static final int WARD_ABS_AMP_MIN = 3;               // Absorption IV  ~= 16 HP at spell level 1
    private static final int WARD_ABS_AMP_MAX = 11;              // Absorption XII ~= 48 HP at the cap

    public static int wardAbsorptionAmplifier(int spellLevel) {
        return (int) Math.round(WARD_ABS_AMP_MIN + (WARD_ABS_AMP_MAX - WARD_ABS_AMP_MIN) * effectiveness(spellLevel));
    }

    // Bright Light: detonates and blinds anyone facing the blast
    public static final double BRIGHT_LIGHT_SPEED = 0.9;
    public static final double BRIGHT_LIGHT_RADIUS = 9.0;
    public static final int BRIGHT_LIGHT_BLIND_TICKS = 40;       // 2 s
    public static final int BRIGHT_LIGHT_FLASH_TICKS = 40;
    public static final double BRIGHT_LIGHT_FACING_DOT = 0.15;   // how "toward" the blast a look vector must be

    // --- Fire Magic (first advanced school; end-game mage kit - costs & damage run higher than Adept) ---

    /** Fire projectile detonations catch every living thing within this of the impact - caster and allies included. */
    public static final double FIRE_IMPACT_RADIUS = 2.5;

    // Shared stacking burn (Ember Dart applies it; Sunburst & Cinder Maelstrom keep it stacked)
    public static final int FIRE_BURN_MAX_STACKS = 3;
    public static final int FIRE_BURN_DURATION_TICKS = 100;      // 5 s - runs off fast, you have to keep re-applying
    public static final int FIRE_BURN_INTERVAL = 20;             // a burn tick every 1 s (5 ticks over the window)
    private static final double FIRE_BURN_PER_STACK_MIN = 0.35;  // raw, per stack, per burn tick - at spell level 1
    private static final double FIRE_BURN_PER_STACK_MAX = 0.75;  // ... at the cap; x3 stacks = a real burn, still not OP

    public static double fireBurnPerStack(int spellLevel) {
        return FIRE_BURN_PER_STACK_MIN
                + (FIRE_BURN_PER_STACK_MAX - FIRE_BURN_PER_STACK_MIN) * effectiveness(spellLevel);
    }

    // Ember Dart: fast fire bolt, adds one burn stack per hit
    public static final double EMBER_DART_SPEED = 1.3;
    private static final double EMBER_DART_DMG_MIN = 1.0;        // raw impact at spell level 1
    private static final double EMBER_DART_DMG_MAX = 2.2;        // ... at the cap

    public static double emberDartDamage(int spellLevel) {
        return EMBER_DART_DMG_MIN + (EMBER_DART_DMG_MAX - EMBER_DART_DMG_MIN) * effectiveness(spellLevel);
    }

    // Serpent's Plume: instant hitscan ray, dims the vision of a player it hits
    public static final double SERPENTS_PLUME_RANGE = 42.0;
    public static final int SERPENTS_PLUME_DARKNESS_TICKS = 60;  // 3 s of Darkness (a soft blind, players only)
    private static final double SERPENTS_PLUME_DMG_MIN = 2.2;    // raw at spell level 1
    private static final double SERPENTS_PLUME_DMG_MAX = 4.5;    // ... at the cap - it always connects, so no huge number

    public static double serpentsPlumeDamage(int spellLevel) {
        return SERPENTS_PLUME_DMG_MIN + (SERPENTS_PLUME_DMG_MAX - SERPENTS_PLUME_DMG_MIN) * effectiveness(spellLevel);
    }

    // Sunburst: fast medium bolt, knocks the target up (also a burn stacker)
    public static final double SUNBURST_SPEED = 1.6;
    public static final double SUNBURST_LAUNCH = 0.62;           // up a couple feet
    private static final double SUNBURST_DMG_MIN = 3.0;          // raw at spell level 1
    private static final double SUNBURST_DMG_MAX = 6.0;          // ... at the cap

    public static double sunburstDamage(int spellLevel) {
        return SUNBURST_DMG_MIN + (SUNBURST_DMG_MAX - SUNBURST_DMG_MIN) * effectiveness(spellLevel);
    }

    // Pyroclasm: slow-cast arcing bolt that falls like an arrow; ~Sunburst damage, ~2x the knock-up (fall damage on landing)
    public static final double PYROCLASM_SPEED = 2.1;            // fast - was 1.4
    public static final double PYROCLASM_ARC_LIFT = 0.10;        // only a slight lob now, so it lands near the crosshair up close
    public static final double PYROCLASM_LAUNCH = 1.15;          // ~2x Sunburst - high enough that the drop hurts
    private static final double PYROCLASM_DMG_MIN = 3.0;
    private static final double PYROCLASM_DMG_MAX = 6.0;

    public static double pyroclasmDamage(int spellLevel) {
        return PYROCLASM_DMG_MIN + (PYROCLASM_DMG_MAX - PYROCLASM_DMG_MIN) * effectiveness(spellLevel);
    }

    // Cinder Maelstrom: ultimate - a lingering AOE fire field that stacks burn AND deals its own damage (friendly fire included)
    public static final double CINDER_FIELD_RADIUS = 4.5;
    public static final int CINDER_FIELD_DURATION_TICKS = 100;   // ~5 s on the ground
    public static final int CINDER_FIELD_DAMAGE_INTERVAL = 20;   // its own damage + a burn refresh every 1 s
    public static final double CINDER_FIELD_PLACE_RANGE = 22.0;  // how far ahead you can drop it
    private static final double CINDER_FIELD_DMG_MIN = 0.8;      // raw per field tick at spell level 1 (on top of the burn)
    private static final double CINDER_FIELD_DMG_MAX = 1.8;      // ... at the cap

    public static double cinderFieldTickDamage(int spellLevel) {
        return CINDER_FIELD_DMG_MIN + (CINDER_FIELD_DMG_MAX - CINDER_FIELD_DMG_MIN) * effectiveness(spellLevel);
    }

    // --- Chaos Magic (a debuff school with one very strong heal) ---

    public static final double CHAOS_BOLT_SPEED = 1.0;            // "medium speed" for Wither / Slump / Hexdrain bolts

    // Heartwell: heal yourself, and splash a slice of that heal to allies standing in the purple aura
    public static final double HEARTWELL_AURA_RADIUS = 4.5;
    public static final double HEARTWELL_ALLY_FRACTION = 0.25;    // allies get 25% of what you healed yourself for
    private static final double HEARTWELL_HEAL_MIN = 55.0;        // HP healed at spell level 1
    private static final double HEARTWELL_HEAL_MAX = 120.0;       // ... at the cap - a real heal

    public static double heartwellSelfHeal(int spellLevel) {
        return HEARTWELL_HEAL_MIN + (HEARTWELL_HEAL_MAX - HEARTWELL_HEAL_MIN) * effectiveness(spellLevel);
    }

    // Wither: while it's on you, your casts are slower and your spells hit softer
    public static final int WITHER_DURATION_TICKS = 160;          // 8 s
    public static final double WITHER_CAST_TIME_MULT = 1.15;      // +15% cast time
    public static final double WITHER_SPELL_DAMAGE_MULT = 0.90;   // -10% outgoing spell damage
    public static final double WITHER_IMPACT_DAMAGE = 1.0;        // raw - it's a debuff, not a nuke

    // Slump: cuts the target's Vitality & Quickness - i.e. their max HP and their stamina
    public static final int SLUMP_DURATION_TICKS = 140;           // 7 s
    public static final double SLUMP_MAX_HP_FRACTION = 0.14;      // -14% max health (vanilla clamps current HP down)
    public static final double SLUMP_STAMINA_DRAIN_FRACTION = 0.15; // and lose 15% of current stamina on the hit
    public static final double SLUMP_IMPACT_DAMAGE = 1.0;

    // Pestilence: instant hitscan disease - damage over 5 s
    public static final double PESTILENCE_RANGE = 40.0;
    public static final int PESTILENCE_DOT_TICKS = 100;           // 5 s
    public static final int PESTILENCE_DOT_INTERVAL = 20;         // a tick every 1 s (5 hits)
    private static final double PESTILENCE_DOT_MIN = 1.2;         // raw per tick at spell level 1
    private static final double PESTILENCE_DOT_MAX = 2.6;         // ... at the cap

    public static double pestilenceDotPerTick(int spellLevel) {
        return PESTILENCE_DOT_MIN + (PESTILENCE_DOT_MAX - PESTILENCE_DOT_MIN) * effectiveness(spellLevel);
    }

    // Hexdrain: steal mana from the target (players only); deliberately not a spam tool
    public static final double HEXDRAIN_RETURN_FRACTION = 0.6;    // caster keeps 60% of what was drained
    public static final double HEXDRAIN_MOB_DAMAGE = 2.0;         // raw, if it somehow hits something with no mana
    private static final double HEXDRAIN_MANA_MIN = 90.0;
    private static final double HEXDRAIN_MANA_MAX = 160.0;

    public static double hexdrainMana(int spellLevel) {
        return HEXDRAIN_MANA_MIN + (HEXDRAIN_MANA_MAX - HEXDRAIN_MANA_MIN) * effectiveness(spellLevel);
    }

    // --- Druid ---

    // Thorns: a reflect buff, cast on yourself or an ally
    public static final double THORNS_RANGE = 20.0;
    public static final int THORNS_TICKS = 1200;                  // 60 s
    private static final double THORNS_REFLECT_MIN = 0.08;        // fraction of the hit bounced back at school level 1
    private static final double THORNS_REFLECT_MAX = 0.18;        // ... at the cap

    public static double thornsReflect(int schoolLevel) {
        return THORNS_REFLECT_MIN + (THORNS_REFLECT_MAX - THORNS_REFLECT_MIN) * effectiveness(schoolLevel);
    }

    // Wolf Form: a toggled shapeshift - faster on land, with Bite and Leap
    public static final double WOLF_FORM_SPEED_BONUS = 0.25;      // +25% movement speed
    public static final double WOLF_BITE_REACH = 3.2;
    public static final int WOLF_BITE_COOLDOWN_TICKS = 12;
    private static final double WOLF_BITE_DMG_MIN = 3.0;
    private static final double WOLF_BITE_DMG_MAX = 6.5;

    public static double wolfBiteDamage(int spellLevel) {
        return WOLF_BITE_DMG_MIN + (WOLF_BITE_DMG_MAX - WOLF_BITE_DMG_MIN) * effectiveness(spellLevel);
    }

    public static final float WOLF_BITE_BLEED_PER_TICK = 1.0f;
    public static final int WOLF_BITE_BLEED_TICKS = 60;           // 3 s
    public static final double WOLF_LEAP_DISTANCE = 8.0;
    public static final double WOLF_LEAP_POWER = 1.05;
    public static final int WOLF_LEAP_COOLDOWN_TICKS = 40;        // 2 s between leaps

    // Dolphin Form: a toggled water-only shapeshift
    public static final double DOLPHIN_FORM_SWIM_BONUS = 0.35;    // +35% swim speed
    public static final int DOLPHIN_FORM_OUT_OF_WATER_GRACE = 60; // out of water this long and the form drops

    // Bloom of Renewal: an ally heal-over-time that also cleanses
    public static final double BLOOM_RANGE = 22.0;
    public static final int BLOOM_TICKS = 120;                    // 6 s of regrowth
    public static final int BLOOM_HEAL_INTERVAL = 20;
    public static final int BLOOM_PATCH_RADIUS = 2;
    private static final double BLOOM_HEAL_PER_TICK_MIN = 2.0;
    private static final double BLOOM_HEAL_PER_TICK_MAX = 5.0;

    public static double bloomHealPerTick(int schoolLevel) {
        return BLOOM_HEAL_PER_TICK_MIN + (BLOOM_HEAL_PER_TICK_MAX - BLOOM_HEAL_PER_TICK_MIN) * effectiveness(schoolLevel);
    }

    // Swarm of the Wild: turn nearby peaceful animals on a target
    public static final double SWARM_CAST_RANGE = 26.0;
    public static final int SWARM_TICKS = 200;                    // 10 s
    public static final double SWARM_RECRUIT_RADIUS = 16.0;
    public static final int SWARM_MAX_ANIMALS = 8;
    public static final int SWARM_ATTACK_INTERVAL = 16;
    private static final double SWARM_ANIMAL_DMG_MIN = 1.0;
    private static final double SWARM_ANIMAL_DMG_MAX = 2.5;

    public static double swarmAnimalDamage(int spellLevel) {
        return SWARM_ANIMAL_DMG_MIN + (SWARM_ANIMAL_DMG_MAX - SWARM_ANIMAL_DMG_MIN) * effectiveness(spellLevel);
    }

    // --- Cleric ---

    // Divine Smite: arms your next melee swing with radiant damage and refunds mana
    public static final int DIVINE_SMITE_TICKS = 100;            // the arm lasts 5 s
    public static final double DIVINE_SMITE_MANA_REFUND = 15.0;
    private static final double DIVINE_SMITE_DMG_MIN = 4.0;      // raw radiant bonus at school level 1
    private static final double DIVINE_SMITE_DMG_MAX = 9.0;      // ... at the cap

    public static double divineSmiteBonus(int spellLevel) {
        return DIVINE_SMITE_DMG_MIN + (DIVINE_SMITE_DMG_MAX - DIVINE_SMITE_DMG_MIN) * effectiveness(spellLevel);
    }

    // Blessing of Protection: an absorption shield that bursts into an AoE heal
    public static final double BLESSING_RANGE = 22.0;
    public static final int BLESSING_ABSORB_AMPLIFIER = 3;       // ~16 HP shield
    public static final int BLESSING_MAX_TICKS = 1200;           // 60 s if never broken
    public static final double BLESSING_BURST_RADIUS = 5.0;
    private static final double BLESSING_BURST_HEAL_MIN = 15.0;
    private static final double BLESSING_BURST_HEAL_MAX = 30.0;

    public static double blessingBurstHeal(int schoolLevel) {
        return BLESSING_BURST_HEAL_MIN + (BLESSING_BURST_HEAL_MAX - BLESSING_BURST_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // Purifying Wave: a frontal holy cone - hurts foes, heals + cleanses allies
    public static final double PURIFYING_WAVE_RANGE = 8.5;
    public static final double PURIFYING_WAVE_HALF_ANGLE_COS = 0.55; // ~57 deg half-angle
    private static final double PURIFYING_WAVE_DMG_MIN = 3.0;
    private static final double PURIFYING_WAVE_DMG_MAX = 6.5;
    private static final double PURIFYING_WAVE_HEAL_MIN = 12.0;
    private static final double PURIFYING_WAVE_HEAL_MAX = 26.0;

    public static double purifyingWaveDamage(int spellLevel) {
        return PURIFYING_WAVE_DMG_MIN + (PURIFYING_WAVE_DMG_MAX - PURIFYING_WAVE_DMG_MIN) * effectiveness(spellLevel);
    }

    public static double purifyingWaveHeal(int schoolLevel) {
        return PURIFYING_WAVE_HEAL_MIN + (PURIFYING_WAVE_HEAL_MAX - PURIFYING_WAVE_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // Sacrificial Heal: spend your own HP, the target gets more back
    public static final double SACRIFICIAL_HEAL_RANGE = 24.0;
    public static final double SACRIFICIAL_HEAL_RATIO = 1.6;    // heal = sacrifice x 1.6
    private static final double SACRIFICIAL_HEAL_COST_MIN = 18.0;
    private static final double SACRIFICIAL_HEAL_COST_MAX = 40.0;

    public static double sacrificialHealCost(int schoolLevel) {
        return SACRIFICIAL_HEAL_COST_MIN + (SACRIFICIAL_HEAL_COST_MAX - SACRIFICIAL_HEAL_COST_MIN) * effectiveness(schoolLevel);
    }

    // Mass Mend: a channelled radius heal, weighted toward the most hurt ally
    public static final double MASS_MEND_RADIUS = 8.0;
    public static final double MASS_MEND_MANA_PER_TICK = 1.1;
    public static final double MASS_MEND_MOVE_TOLERANCE = 0.6;  // move farther than this from the cast spot and it drops
    private static final double MASS_MEND_HEAL_MIN = 1.4;       // per tick, per ally, at school level 1
    private static final double MASS_MEND_HEAL_MAX = 3.2;
    public static final double MASS_MEND_INJURED_BONUS = 2.0;   // most-injured ally gets up to this much extra per tick

    public static double massMendHealPerTick(int schoolLevel) {
        return MASS_MEND_HEAL_MIN + (MASS_MEND_HEAL_MAX - MASS_MEND_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // --- Shaman ---

    // Totems (shared): a 1x2 destructible pillar that pulses an aura
    public static final int TOTEM_LIFETIME = 300;               // 15 s
    public static final double TOTEM_AURA_RADIUS = 7.0;

    // Lightning Totem
    public static final int LIGHTNING_TOTEM_PULSE = 30;
    public static final int LIGHTNING_TOTEM_BUFF_TICKS = 60;
    private static final double LIGHTNING_TOTEM_DMG_MIN = 1.5;
    private static final double LIGHTNING_TOTEM_DMG_MAX = 3.5;

    public static double lightningTotemDamage(int spellLevel) {
        return LIGHTNING_TOTEM_DMG_MIN + (LIGHTNING_TOTEM_DMG_MAX - LIGHTNING_TOTEM_DMG_MIN) * effectiveness(spellLevel);
    }

    // Healing Totem
    public static final int HEALING_TOTEM_LIFETIME = 600;       // 30 s so it gets several ticks
    public static final int HEALING_TOTEM_PULSE = 100;          // heal every 5 s
    private static final double HEALING_TOTEM_HEAL_MIN = 8.0;
    private static final double HEALING_TOTEM_HEAL_MAX = 18.0;

    public static double healingTotemHeal(int schoolLevel) {
        return HEALING_TOTEM_HEAL_MIN + (HEALING_TOTEM_HEAL_MAX - HEALING_TOTEM_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // Hex of Frailty: a curse that weakens and slows, and pays out on the kill
    public static final double HEX_RANGE = 26.0;
    public static final int HEX_TICKS = 200;                    // 10 s
    private static final double HEX_KILL_HEAL_MIN = 40.0;
    private static final double HEX_KILL_HEAL_MAX = 85.0;

    public static double hexKillHeal(int schoolLevel) {
        return HEX_KILL_HEAL_MIN + (HEX_KILL_HEAL_MAX - HEX_KILL_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // Fire Shock: a burning DoT that heals the caster on its last tick
    public static final double FIRE_SHOCK_RANGE = 22.0;
    public static final int FIRE_SHOCK_TICKS = 100;             // 5 s
    public static final int FIRE_SHOCK_INTERVAL = 20;
    private static final double FIRE_SHOCK_DMG_MIN = 1.6;       // raw, per tick
    private static final double FIRE_SHOCK_DMG_MAX = 3.4;
    private static final double FIRE_SHOCK_HEAL_MIN = 10.0;
    private static final double FIRE_SHOCK_HEAL_MAX = 22.0;

    public static double fireShockDamage(int spellLevel) {
        return FIRE_SHOCK_DMG_MIN + (FIRE_SHOCK_DMG_MAX - FIRE_SHOCK_DMG_MIN) * effectiveness(spellLevel);
    }

    public static double fireShockHeal(int schoolLevel) {
        return FIRE_SHOCK_HEAL_MIN + (FIRE_SHOCK_HEAL_MAX - FIRE_SHOCK_HEAL_MIN) * effectiveness(schoolLevel);
    }

    // Plague: a green beam that leaves three poison frogs on the target
    public static final double PLAGUE_RANGE = 24.0;
    public static final int PLAGUE_FROGS = 3;
    public static final int PLAGUE_FROG_TICKS = 100;            // 5 s
    public static final int PLAGUE_FROG_ATTACK_INTERVAL = 18;
    public static final int PLAGUE_POISON_TICKS = 60;
    private static final double PLAGUE_FROG_DMG_MIN = 1.0;
    private static final double PLAGUE_FROG_DMG_MAX = 2.5;

    public static double plagueFrogDamage(int spellLevel) {
        return PLAGUE_FROG_DMG_MIN + (PLAGUE_FROG_DMG_MAX - PLAGUE_FROG_DMG_MIN) * effectiveness(spellLevel);
    }

    // --- Arcana ---

    // Arcane Bolt: a piercing skill-shot that leaves a sigil for a follow-up
    public static final double ARCANE_BOLT_SPEED = 2.6;
    public static final int ARCANE_BOLT_MAX_PIERCE = 2;
    public static final int ARCANE_BOLT_MARK_TICKS = 100;       // the sigil lasts 5 s
    public static final double ARCANA_MARK_BONUS = 1.3;         // your next Arcana hit on a marked target does +30%
    private static final double ARCANE_BOLT_DMG_MIN = 3.5;
    private static final double ARCANE_BOLT_DMG_MAX = 7.0;

    public static double arcaneBoltDamage(int spellLevel) {
        return ARCANE_BOLT_DMG_MIN + (ARCANE_BOLT_DMG_MAX - ARCANE_BOLT_DMG_MIN) * effectiveness(spellLevel);
    }

    // Mana Rift: a small zone that tears the ground open and destabilizes magic in it
    public static final double MANA_RIFT_RANGE = 10.0;
    public static final double MANA_RIFT_RADIUS = 3.2;
    public static final int MANA_RIFT_TICKS = 80;                // lasts 4 s
    public static final int MANA_RIFT_DAMAGE_INTERVAL = 20;      // a damage tick every 1 s
    private static final double MANA_RIFT_TICK_DMG_MIN = 2.0;
    private static final double MANA_RIFT_TICK_DMG_MAX = 4.5;

    public static double manaRiftTickDamage(int spellLevel) {
        return MANA_RIFT_TICK_DMG_MIN + (MANA_RIFT_TICK_DMG_MAX - MANA_RIFT_TICK_DMG_MIN) * effectiveness(spellLevel);
    }

    // Spellbind: locks a caster out of every school for a few seconds
    public static final double SPELLBIND_RANGE = 24.0;
    public static final int SPELLBIND_LOCK_TICKS = 60;           // 3 s

    // Arcane Shift: a short forward blink that can't pass through terrain
    public static final double ARCANE_SHIFT_DISTANCE = 7.0;
    public static final double ARCANE_SHIFT_MIN_TRAVEL = 1.5;    // shorter than this and it fizzles
    public static final double ARCANE_SHIFT_DECOY_RADIUS = 10.0; // hostiles this close to the old spot lose your trail

    // Astral Annihilation: a channelled beam that ramps up the longer you hold it,
    // rooting you in place and eventually chewing through the terrain it crosses.
    public static final int ASTRAL_ANNIHILATION_MAX_CHARGE_TICKS = 80;    // 4 s to reach full power
    public static final double ASTRAL_ANNIHILATION_MANA_PER_TICK_MIN = 1.0;
    public static final double ASTRAL_ANNIHILATION_MANA_PER_TICK_MAX = 3.2;
    public static final double ASTRAL_ANNIHILATION_RANGE = 14.0;
    public static final double ASTRAL_ANNIHILATION_MIN_RADIUS = 0.6;
    public static final double ASTRAL_ANNIHILATION_MAX_RADIUS = 2.4;
    public static final int ASTRAL_ANNIHILATION_DAMAGE_INTERVAL = 5;      // every 0.25 s
    private static final double ASTRAL_ANNIHILATION_TICK_DMG_MIN = 0.5;
    private static final double ASTRAL_ANNIHILATION_TICK_DMG_MAX = 2.2;
    public static final int ASTRAL_ANNIHILATION_DIG_START_TICKS = 30;     // digging starts 1.5 s into the channel
    public static final int ASTRAL_ANNIHILATION_DIG_INTERVAL = 4;
    public static final double ASTRAL_ANNIHILATION_DIG_HARDNESS_CAP = 30.0; // won't eat obsidian/bedrock-tier blocks

    /** 0 at the start of the channel, 1 at full charge (and capped there). */
    public static double astralAnnihilationCharge(long channelTicks) {
        return Math.min(1.0, channelTicks / (double) ASTRAL_ANNIHILATION_MAX_CHARGE_TICKS);
    }

    public static double astralAnnihilationTickDamage(double charge, int spellLevel) {
        double base = ASTRAL_ANNIHILATION_TICK_DMG_MIN + (ASTRAL_ANNIHILATION_TICK_DMG_MAX - ASTRAL_ANNIHILATION_TICK_DMG_MIN) * charge;
        return base * (0.6 + 0.4 * effectiveness(spellLevel));
    }

    // --- Gravemancy ---

    // Bone Spear: a plain damage bolt
    public static final double BONE_SPEAR_SPEED = 1.2;
    private static final double BONE_SPEAR_DMG_MIN = 2.5;
    private static final double BONE_SPEAR_DMG_MAX = 5.5;

    public static double boneSpearDamage(int spellLevel) {
        return BONE_SPEAR_DMG_MIN + (BONE_SPEAR_DMG_MAX - BONE_SPEAR_DMG_MIN) * effectiveness(spellLevel);
    }

    // Raise Minion: a skeleton that fights for you for a while
    public static final double RAISE_MINION_RANGE = 14.0;
    public static final int RAISE_MINION_LIFETIME = 600;      // 30 s
    public static final int RAISE_MINION_CHARGE_TICKS = 1200; // one charge back every 60 s
    public static int raiseMinionCap(int spellLevel) {
        return spellLevel >= 75 ? 2 : 1;
    }

    // Soul Drain: a fast bolt that steals health
    public static final double SOUL_DRAIN_SPEED = 1.9;
    public static final double SOUL_DRAIN_HEAL = 25.0;        // health moved from target to caster
    public static final double SOUL_DRAIN_MOB_DAMAGE = 3.0;   // raw, if the thing hit has no "soul" to give

    // Eye Decay: a blinding hex
    public static final double EYE_DECAY_SPEED = 1.0;
    public static final int EYE_DECAY_BLIND_TICKS = 30;       // 1.5 s
    private static final double EYE_DECAY_DMG_MIN = 0.6;
    private static final double EYE_DECAY_DMG_MAX = 1.4;

    public static double eyeDecayDamage(int spellLevel) {
        return EYE_DECAY_DMG_MIN + (EYE_DECAY_DMG_MAX - EYE_DECAY_DMG_MIN) * effectiveness(spellLevel);
    }

    // Wraith Step: a short blink with a blast where you land
    public static final double WRAITH_STEP_DISTANCE = 5.0;
    public static final double WRAITH_STEP_BLAST_RADIUS = 2.6;
    public static final double WRAITH_STEP_KNOCKBACK = 0.6;
    private static final double WRAITH_STEP_DMG_MIN = 2.5;
    private static final double WRAITH_STEP_DMG_MAX = 4.5;

    public static double wraithStepDamage(int spellLevel) {
        return WRAITH_STEP_DMG_MIN + (WRAITH_STEP_DMG_MAX - WRAITH_STEP_DMG_MIN) * effectiveness(spellLevel);
    }

    // --- Earth Magic ---

    // Earthen Path: a mossy hazard patch - slows and chips at anyone on it; a fire spell sets it off
    public static final double EARTHEN_PATH_RANGE = 20.0;
    public static final double EARTHEN_PATH_RADIUS = 2.6;
    public static final int EARTHEN_PATH_TICKS = 360;          // 18 s if never ignited
    public static final int EARTHEN_PATH_DAMAGE_INTERVAL = 20; // a chip every 1 s
    public static final double EARTHEN_PATH_SLOW = 0.15;       // -15% move speed while standing on it
    private static final double EARTHEN_PATH_DPT_MIN = 0.30;   // raw per chip at spell level 1
    private static final double EARTHEN_PATH_DPT_MAX = 0.70;   // ... at the cap
    public static final int EARTHEN_PATH_COMBUST_TICKS = 60;   // 3 s of burning once lit
    public static final double EARTHEN_PATH_COMBUST_BONUS = 2.5; // combust hits do this much extra raw

    public static double earthenPathChipDamage(int spellLevel) {
        return EARTHEN_PATH_DPT_MIN + (EARTHEN_PATH_DPT_MAX - EARTHEN_PATH_DPT_MIN) * effectiveness(spellLevel);
    }

    // Seismic Pillar: erupt the ground and fling the target the way they were moving
    public static final double SEISMIC_PILLAR_RANGE = 22.0;
    public static final double SEISMIC_PILLAR_UP = 1.05;
    public static final double SEISMIC_PILLAR_FORWARD = 1.25;
    public static final int SEISMIC_PILLAR_STONE_TICKS = 80;   // the conjured stone lingers ~4 s

    // Quake Stomp: lift, hover, then dive to where you're aiming and crash down
    public static final int QUAKE_STOMP_HOLD_TICKS = 60;       // 3 s hover before the dive
    public static final double QUAKE_STOMP_LIFT = 0.85;        // upward kick to get airborne
    public static final double QUAKE_STOMP_MIN_RANGE = 20.0;
    public static final double QUAKE_STOMP_MAX_RANGE = 40.0;
    public static final double QUAKE_STOMP_DIVE_SPEED = 2.2;
    public static final double QUAKE_STOMP_CRASH_RADIUS = 3.6;
    public static final double QUAKE_STOMP_KNOCKBACK = 0.7;
    private static final double QUAKE_STOMP_DMG_MIN = 4.0;
    private static final double QUAKE_STOMP_DMG_MAX = 7.5;

    public static double quakeStompDamage(int spellLevel) {
        return QUAKE_STOMP_DMG_MIN + (QUAKE_STOMP_DMG_MAX - QUAKE_STOMP_DMG_MIN) * effectiveness(spellLevel);
    }

    // Bark Skin: -X% incoming melee and archery damage for a spell
    public static final double BARK_SKIN_RANGE = 24.0;
    public static final int BARK_SKIN_TICKS = 900;             // 45 s
    public static final double BARK_SKIN_REDUCTION = 0.25;     // -25% melee / projectile damage

    // Fissure: rip a trench along your look direction
    public static final int FISSURE_LENGTH = 6;
    public static final int FISSURE_DEPTH = 3;

    // --- Water Magic ---

    // Water Breathing: aimed self/ally buff
    public static final double WATER_BREATHING_RANGE = 24.0;
    public static final int WATER_BREATHING_TICKS = 2400;         // 2 minutes
    public static final double WATER_BREATHING_SWIM_BONUS = 0.15; // +15% swim speed

    // Ice Wall: a 3x3 slab of ice that rises where you aim, then melts away
    public static final double ICE_WALL_RANGE = 12.0;
    public static final int ICE_WALL_TICKS = 300;                 // 15 s

    // Water Orb: slow-cast bolt that shatters into ice patches and hurts the target
    public static final double WATER_ORB_SPEED = 0.9;
    public static final double WATER_ORB_ICE_RADIUS = 2.2;        // frosted-ice patch around the impact
    private static final double WATER_ORB_DMG_MIN = 3.0;
    private static final double WATER_ORB_DMG_MAX = 6.0;

    public static double waterOrbDamage(int spellLevel) {
        return WATER_ORB_DMG_MIN + (WATER_ORB_DMG_MAX - WATER_ORB_DMG_MIN) * effectiveness(spellLevel);
    }

    // Water Spout: a toggled beam that only pays off while you keep it on target
    public static final double WATER_SPOUT_RANGE = 26.0;
    public static final double WATER_SPOUT_MANA_PER_TICK = 1.0;   // ~20 / s while it's on
    private static final double WATER_SPOUT_DPT_MIN = 0.20;       // raw damage per tick on target, spell level 1
    private static final double WATER_SPOUT_DPT_MAX = 0.38;       // ... at the cap

    public static double waterSpoutDamagePerTick(int spellLevel) {
        return WATER_SPOUT_DPT_MIN + (WATER_SPOUT_DPT_MAX - WATER_SPOUT_DPT_MIN) * effectiveness(spellLevel);
    }

    // Riptide: a travelling wave that scoops up whoever it rolls over, then bursts
    public static final double RIPTIDE_SPEED = 1.15;              // blocks / tick the wave front advances
    public static final int RIPTIDE_TICKS = 16;                   // ~18 blocks of travel
    public static final double RIPTIDE_CATCH_RADIUS = 2.3;
    public static final double RIPTIDE_LIFT = 0.9;                // upward scoop
    public static final double RIPTIDE_BURST_RADIUS = 3.5;
    private static final double RIPTIDE_WAVE_DMG_MIN = 2.5;       // raw, caught by the rolling wave
    private static final double RIPTIDE_WAVE_DMG_MAX = 4.5;
    private static final double RIPTIDE_BURST_DMG_MIN = 3.5;      // raw, the end explosion
    private static final double RIPTIDE_BURST_DMG_MAX = 6.5;

    public static double riptideWaveDamage(int spellLevel) {
        return RIPTIDE_WAVE_DMG_MIN + (RIPTIDE_WAVE_DMG_MAX - RIPTIDE_WAVE_DMG_MIN) * effectiveness(spellLevel);
    }

    public static double riptideBurstDamage(int spellLevel) {
        return RIPTIDE_BURST_DMG_MIN + (RIPTIDE_BURST_DMG_MAX - RIPTIDE_BURST_DMG_MIN) * effectiveness(spellLevel);
    }

    // --- Air Magic (lightning & wind) ---

    // Lightning / Chain Shock / Howling Impact all conduct through water
    public static final double WATER_CONDUCT_RADIUS = 2.5;    // ~5x5 of water around the strike
    public static final double WATER_CONDUCT_MULT = 1.4;      // +40% to a target that's standing in water
    private static final double WATER_CONDUCT_SPLASH_MIN = 1.5; // raw, to others in the water
    private static final double WATER_CONDUCT_SPLASH_MAX = 3.0;

    public static double waterConductSplashDamage(int spellLevel) {
        return WATER_CONDUCT_SPLASH_MIN + (WATER_CONDUCT_SPLASH_MAX - WATER_CONDUCT_SPLASH_MIN) * effectiveness(spellLevel);
    }

    // Lightning Strike: aimed - calls a (visual-only) bolt down onto the target and deals our own scaled damage
    public static final double LIGHTNING_STRIKE_RANGE = 40.0;
    private static final double LIGHTNING_STRIKE_DMG_MIN = 1.5;   // raw at spell level 1
    private static final double LIGHTNING_STRIKE_DMG_MAX = 3.2;   // ... at the cap
    public static final int LIGHTNING_STRIKE_FIRE_SECONDS = 2;

    public static double lightningStrikeDamage(int spellLevel) {
        return LIGHTNING_STRIKE_DMG_MIN + (LIGHTNING_STRIKE_DMG_MAX - LIGHTNING_STRIKE_DMG_MIN) * effectiveness(spellLevel);
    }

    // Speed of Wind: a channelled beam onto an ally - faster attacks + move speed while it drains your mana
    public static final double SPEED_OF_WIND_RANGE = 22.0;
    public static final double SPEED_OF_WIND_MANA_PER_TICK = 0.9;   // ~18 / second
    public static final double SPEED_OF_WIND_ATTACK_SPEED = 0.25;   // +25% weapon attack speed on the target
    public static final double SPEED_OF_WIND_MOVE_SPEED = 0.10;     // +10% movement speed

    // Chain Shock: instant hitscan; the hit arcs to nearby targets for less and less
    public static final double CHAIN_SHOCK_RANGE = 34.0;
    public static final double CHAIN_SHOCK_JUMP_RANGE = 5.5;        // how far the arc can jump between targets
    public static final int CHAIN_SHOCK_MAX_JUMPS = 3;              // 1 primary + up to 3 chained
    public static final double[] CHAIN_SHOCK_FALLOFF = {0.40, 0.30, 0.20};
    private static final double CHAIN_SHOCK_DMG_MIN = 2.4;          // raw primary hit at spell level 1
    private static final double CHAIN_SHOCK_DMG_MAX = 5.0;          // ... at the cap

    public static double chainShockDamage(int spellLevel) {
        return CHAIN_SHOCK_DMG_MIN + (CHAIN_SHOCK_DMG_MAX - CHAIN_SHOCK_DMG_MIN) * effectiveness(spellLevel);
    }

    // Wind Lure: yank the target up and toward the caster (allies or enemies)
    public static final double WIND_LURE_RANGE = 30.0;
    public static final double WIND_LURE_PULL = 1.5;               // horizontal velocity toward the caster
    public static final double WIND_LURE_LIFT = 0.55;              // upward kick so they clear the ground

    // Howling Impact: slow-cast bolt that bursts into a damaging gust; direct hits hurt more
    public static final double HOWLING_SPEED = 1.6;
    public static final double HOWLING_RADIUS = 3.6;
    public static final double HOWLING_KNOCKBACK = 0.8;            // outward gust
    private static final double HOWLING_DIRECT_MIN = 4.5;         // raw, taken by the entity struck directly
    private static final double HOWLING_DIRECT_MAX = 8.0;
    private static final double HOWLING_SPLASH_MIN = 2.0;        // raw, taken by everything else in the gust
    private static final double HOWLING_SPLASH_MAX = 3.5;

    public static double howlingDirectDamage(int spellLevel) {
        return HOWLING_DIRECT_MIN + (HOWLING_DIRECT_MAX - HOWLING_DIRECT_MIN) * effectiveness(spellLevel);
    }

    public static double howlingSplashDamage(int spellLevel) {
        return HOWLING_SPLASH_MIN + (HOWLING_SPLASH_MAX - HOWLING_SPLASH_MIN) * effectiveness(spellLevel);
    }

    // --- Incantation Magic (utility chants) ---

    // Chant of Growth: nudge nearby crops / saplings along
    public static final double GROWTH_RADIUS = 6.0;
    public static final double GROWTH_TICK_CHANCE = 0.6;         // chance to advance each growable block in range

    // Battle Hymn: a channelled team buff - +N to Strength / Quickness / Vitality for 15 minutes
    public static final int BATTLE_HYMN_STAT_BONUS = 10;
    public static final int BATTLE_HYMN_DURATION_TICKS = 18000;  // 15 minutes
    public static final double BATTLE_HYMN_RADIUS = 9.0;         // allies within this of the caster are empowered

    // Word of Unmaking: delete a small cluster of unclaimed blocks (drop someone through the floor)
    public static final double UNMAKING_RANGE = 6.0;
    public static final int UNMAKING_DEPTH = 2;                  // blocks removed straight down from the aim point
    public static final int UNMAKING_SPREAD = 1;                 // + a 1-block ring around the top

    // Fearcraft: a 3 s chant that sends nearby monsters running for 5 s
    public static final double FEARCRAFT_RADIUS = 10.0;         // monsters within this at cast time are frightened
    public static final int FEARCRAFT_DURATION_TICKS = 100;     // 5 s
    public static final double FEARCRAFT_FLEE_SPEED = 1.35;     // navigation speed multiplier while fleeing
    public static final double FEARCRAFT_FLEE_DISTANCE = 12.0;  // how far ahead the flee path is aimed

    // Silencing Whisper: interrupt an enemy's cast and lock that school for a beat
    public static final double SILENCE_RANGE = 32.0;
    public static final int SILENCE_LOCK_TICKS = 40;            // 2 s

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
    /** Global multiplier on all magic XP (spell + school). Bumped for testing new spells; drop to 1.0 for live. */
    public static final double SCHOOL_XP_MULT = 1.6;
    /** Fraction of a spell's XP that also feeds its school - a school hits 100 near when its spells all reach ~70. */
    public static final double SCHOOL_XP_SHARE = 0.5;
    public static final double XP_CAST_SPELL = 10.0;       // per cast, damage spells
    public static final double XP_CAST_TRANSFER = 22.0;    // per cast, Weak Magic transfers - they level noticeably faster
    public static final double XP_SPELL_HIT = 30.0;        // per enemy an offensive spell actually lands on

    /** School XP for landing an offensive spell on 1-3 enemies at once. */
    public static double spellHitXp(int enemies) {
        int n = Math.max(1, Math.min(3, enemies));
        double mult = switch (n) {
            case 1 -> 1.0;
            case 2 -> 1.8;
            default -> 2.8;
        };
        return XP_SPELL_HIT * mult;
    }
    public static final double XP_FISH_CATCH = 6.0;
    public static final double XP_HARVEST_CROP = 2.0;
    public static final double XP_ENCHANT = 15.0;
}
