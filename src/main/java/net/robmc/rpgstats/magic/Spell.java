package net.robmc.rpgstats.magic;

/**
 * The built-in spells. Right now just the Weak Magic school: three stat-transfer
 * spells (spend 1 of one pool, gain 2 of another) and a weak Magic Bolt. All are
 * known from Weak Magic level 1.
 *
 * Costs/effects are consumed from the pool named by {@code costPool}; a value of
 * 0 there means the spell's cost IS its transfer input (see SpellCasting).
 */
public enum Spell {

    MANA_TO_STAMINA("Transfer: Mana → Stamina", Pool.MANA, Pool.STAMINA, 8, 100, 0, "Weak Magic"),
    STAMINA_TO_HEALTH("Transfer: Stamina → Health", Pool.STAMINA, Pool.HEALTH, 8, 100, 0, "Weak Magic"),
    HEALTH_TO_MANA("Transfer: Health → Mana", Pool.HEALTH, Pool.MANA, 8, 100, 0, "Weak Magic"),
    MAGIC_BOLT("Magic Bolt", Pool.MANA, null, 16, 30, 20, "Weak Magic");

    public enum Pool { HEALTH, STAMINA, MANA }

    private final String displayName;
    private final Pool costPool;
    private final Pool gainPool;   // null = not a transfer
    private final int castTicks;
    private final int cooldownTicks;
    private final double flatCost;  // fixed pool cost (Magic Bolt); 0 for transfers
    private final String school;

    Spell(String displayName, Pool costPool, Pool gainPool, int castTicks, int cooldownTicks, double flatCost, String school) {
        this.displayName = displayName;
        this.costPool = costPool;
        this.gainPool = gainPool;
        this.castTicks = castTicks;
        this.cooldownTicks = cooldownTicks;
        this.flatCost = flatCost;
        this.school = school;
    }

    public String displayName() {
        return displayName;
    }

    public Pool costPool() {
        return costPool;
    }

    public Pool gainPool() {
        return gainPool;
    }

    public int castTicks() {
        return castTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public double flatCost() {
        return flatCost;
    }

    public String school() {
        return school;
    }

    public boolean isTransfer() {
        return gainPool != null;
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
