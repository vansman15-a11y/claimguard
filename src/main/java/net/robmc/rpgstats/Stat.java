package net.robmc.rpgstats;

/**
 * The six trainable stats. Each is an integer "level" that goes up as you
 * perform the matching actions (see RpgEvents), and feeds into your resource
 * pools and damage (see StatFormulas).
 */
public enum Stat {

    STRENGTH("Strength", "mining, melee"),
    VITALITY("Vitality", "chopping trees, melee"),
    DEXTERITY("Dexterity", "bows & crossbows"),
    QUICKNESS("Quickness", "moving, swimming"),
    INTELLIGENCE("Intelligence", "casting spells"),
    WISDOM("Wisdom", "fishing, farming, enchanting");

    private final String displayName;
    private final String trainedBy;

    Stat(String displayName, String trainedBy) {
        this.displayName = displayName;
        this.trainedBy = trainedBy;
    }

    public String displayName() {
        return displayName;
    }

    public String trainedBy() {
        return trainedBy;
    }
}
