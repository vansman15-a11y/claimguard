package net.robmc.rpgstats.magic;

/**
 * The magic schools. Each will hold up to 4 spells. Only Weak Magic has its
 * spells built so far; the rest are placeholders in the spellbook.
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
    INCANTATION("Incantation Magic");

    /** How many spell slots a school is expected to fill. */
    public static final int SPELLS_PER_SCHOOL = 4;

    private final String displayName;

    School(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
