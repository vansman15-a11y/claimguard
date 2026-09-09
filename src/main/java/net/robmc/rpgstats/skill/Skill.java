package net.robmc.rpgstats.skill;

/**
 * General (non-magic) skills. Like spells, they sit in the spell-bar slots and
 * fire on that slot's key - but they are activated abilities / toggles rather
 * than casts. Trained by use, capped at {@code StatFormulas.LEVEL_CAP}.
 */
public enum Skill {

    REST("Rest", "hunker down, recover faster - not for combat");

    private final String displayName;
    private final String blurb;

    Skill(String displayName, String blurb) {
        this.displayName = displayName;
        this.blurb = blurb;
    }

    public String displayName() {
        return displayName;
    }

    public String blurb() {
        return blurb;
    }

    public static Skill byName(String name) {
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
