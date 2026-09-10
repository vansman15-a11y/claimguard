package net.robmc.rpgstats.magic;

/**
 * The magic schools. Each school has its own level (0-100), trained by casting
 * any of its spells. Every school except Weak Magic unlocks spells by tier as
 * that level climbs: tier 1 from the start, then tiers 2-5 at school level 25,
 * 50, 75 and 100.
 */
public enum School {

    EARTH("Earth Magic"),
    FIRE("Fire Magic"),
    AIR("Air Magic"),
    WATER("Water Magic"),
    ARCANA("Arcana"),
    ADEPT("Adept Magic"),
    WEAK("Weak Magic"),
    CHAOS("Chaos Magic"),
    INCANTATION("Incantation Magic"),
    GRAVEMANCY("Gravemancy"),
    DRUID("Druid"),
    SHAMAN("Shaman"),
    CLERIC("Cleric");

    /** Most schools fill 5 tiers; Weak Magic only has 4. */
    public static final int MAX_TIERS = 5;

    private final String displayName;

    School(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** School level needed to have unlocked a spell of the given tier. Weak Magic's are all free. */
    public int unlockLevel(int tier) {
        if (this == WEAK) {
            return 1;
        }
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 25;
            case 3 -> 50;
            case 4 -> 75;
            default -> 100;
        };
    }

    public int tierCount() {
        return this == WEAK ? 4 : MAX_TIERS;
    }
}
