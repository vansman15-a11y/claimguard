package net.robmc.claimguard.clan;

/** How one clan regards another. Absence of an entry means NEUTRAL. */
public enum ClanRelation {

    ALLY("Ally"),
    ENEMY("Enemy");

    private final String displayName;

    ClanRelation(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ClanRelation byIndexOrNull(int index) {
        return (index >= 0 && index < values().length) ? values()[index] : null;
    }
}
