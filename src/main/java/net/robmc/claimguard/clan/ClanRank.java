package net.robmc.claimguard.clan;

/**
 * Clan ranks, most senior first. {@code ordinal()} is the seniority index
 * (LEADER = 0), so "a outranks b" is {@code a.ordinal() < b.ordinal()}.
 */
public enum ClanRank {

    LEADER("Leader"),
    OFFICER("Officer"),
    MEMBER("Member"),
    RECRUIT("Recruit");

    private final String displayName;

    ClanRank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True if this rank is strictly more senior than {@code other}. */
    public boolean outranks(ClanRank other) {
        return this.ordinal() < other.ordinal();
    }

    /** The rank one step up (LEADER stays LEADER), used by "promote". */
    public ClanRank promoted() {
        return this == LEADER ? LEADER : values()[this.ordinal() - 1];
    }

    /** The rank one step down (RECRUIT stays RECRUIT), used by "demote". */
    public ClanRank demoted() {
        return this == RECRUIT ? RECRUIT : values()[this.ordinal() + 1];
    }

    public static ClanRank byIndex(int index) {
        ClanRank[] all = values();
        if (index < 0 || index >= all.length) {
            return MEMBER;
        }
        return all[index];
    }
}
