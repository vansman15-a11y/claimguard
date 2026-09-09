package net.robmc.rpgstats.magic;

/**
 * Every built-in spell. Each belongs to a {@link School} and a tier (1-5) within
 * it; the tier decides the school level you need before you can cast it (see
 * {@link School#unlockLevel(int)}).
 */
public enum Spell {

    // --- Weak Magic (tiers 1-4, all usable from the start) ---
    MANA_TO_STAMINA("Transfer: Mana → Stamina", School.WEAK, 1, Kind.TRANSFER, Pool.MANA, Pool.STAMINA, 0, 14, 100),
    STAMINA_TO_HEALTH("Transfer: Stamina → Health", School.WEAK, 2, Kind.TRANSFER, Pool.STAMINA, Pool.HEALTH, 0, 14, 100),
    HEALTH_TO_MANA("Transfer: Health → Mana", School.WEAK, 3, Kind.TRANSFER, Pool.HEALTH, Pool.MANA, 0, 14, 100),
    MAGIC_BOLT("Magic Bolt", School.WEAK, 4, Kind.MAGIC_BOLT, Pool.MANA, null, 20, 16, 30),

    // --- Adept Magic ---
    SUNDER("Sunder", School.ADEPT, 1, Kind.SUNDER, Pool.MANA, null, 16, 16, 70),
    HEAL_OTHER("Heal Other", School.ADEPT, 2, Kind.HEAL_OTHER, Pool.MANA, null, 34, 30, 90),
    AWAY("Away", School.ADEPT, 3, Kind.AWAY, Pool.MANA, null, 16, 14, 120),
    SCATTER("Scatter", School.ADEPT, 4, Kind.SCATTER, Pool.MANA, null, 14, 12, 140),
    BRIGHT_LIGHT("Bright Light", School.ADEPT, 5, Kind.BRIGHT_LIGHT, Pool.MANA, null, 40, 24, 300);

    public enum Pool { HEALTH, STAMINA, MANA }

    /** How SpellCasting resolves the spell when the cast finishes. */
    public enum Kind { TRANSFER, MAGIC_BOLT, SUNDER, HEAL_OTHER, AWAY, SCATTER, BRIGHT_LIGHT }

    private final String displayName;
    private final School school;
    private final int tier;
    private final Kind kind;
    private final Pool costPool;
    private final Pool gainPool;    // transfers only
    private final double flatCost;  // fixed pool cost for non-transfers
    private final int castTicks;
    private final int cooldownTicks;

    Spell(String displayName, School school, int tier, Kind kind,
          Pool costPool, Pool gainPool, double flatCost, int castTicks, int cooldownTicks) {
        this.displayName = displayName;
        this.school = school;
        this.tier = tier;
        this.kind = kind;
        this.costPool = costPool;
        this.gainPool = gainPool;
        this.flatCost = flatCost;
        this.castTicks = castTicks;
        this.cooldownTicks = cooldownTicks;
    }

    public String displayName() {
        return displayName;
    }

    public School school() {
        return school;
    }

    public int tier() {
        return tier;
    }

    public Kind kind() {
        return kind;
    }

    public Pool costPool() {
        return costPool;
    }

    public Pool gainPool() {
        return gainPool;
    }

    public double flatCost() {
        return flatCost;
    }

    public int castTicks() {
        return castTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean isTransfer() {
        return kind == Kind.TRANSFER;
    }

    /** School level required to cast this spell. */
    public int unlockLevel() {
        return school.unlockLevel(tier);
    }

    /** The spell of a school at a given tier, or null. */
    public static Spell of(School school, int tier) {
        for (Spell s : values()) {
            if (s.school == school && s.tier == tier) {
                return s;
            }
        }
        return null;
    }

    public static Spell byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
