package net.robmc.rpgstats.skill;

/**
 * The planned non-magic "fighter" classes - display-only for now (Skills Book
 * placeholders in {@code SpellbookScreen}). None of these have abilities yet;
 * they'll get designed and wired up as their own system later, the same way
 * {@link net.robmc.rpgstats.magic.School} backs the Spellbook.
 */
public enum FighterClass {

    BERSERKER("Berserker"),
    KNIGHT("Knight"),
    ASSASSIN("Assassin"),
    NINJA("Ninja"),
    BEAST_MASTER("Beast Master"),
    STALKER("Stalker"),
    SHARPSHOOTER("Sharpshooter"),
    TRICKSHOT("Trickshot"),
    MONK("Monk");

    private final String displayName;

    FighterClass(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
