package net.robmc.claimguard.clan;

/**
 * Who in a clan may do what to whom.
 *
 * - Leader: everything, on anyone (except themselves / the leader slot).
 * - Officer: promote / demote / kick, but only players ranked strictly below
 *   them, and a promotion can't reach the officer's own rank. Never ban.
 * - Member / Recruit: whisper and invite only.
 */
public final class ClanPermissions {

    private ClanPermissions() {
    }

    public static boolean canInvite(ClanRank actor) {
        return true; // every member can invite
    }

    public static boolean canKick(ClanRank actor, ClanRank target) {
        if (target == ClanRank.LEADER) {
            return false;
        }
        if (actor == ClanRank.LEADER) {
            return true;
        }
        return actor == ClanRank.OFFICER && actor.outranks(target);
    }

    public static boolean canBan(ClanRank actor, ClanRank target) {
        return actor == ClanRank.LEADER && target != ClanRank.LEADER;
    }

    public static boolean canPromote(ClanRank actor, ClanRank target) {
        if (target == ClanRank.LEADER) {
            return false;
        }
        ClanRank result = target.promoted();
        if (result == ClanRank.LEADER) {
            return false; // leadership isn't handed over through "promote"
        }
        if (actor == ClanRank.LEADER) {
            return true;
        }
        // Officer: target must end up strictly below the officer.
        return actor == ClanRank.OFFICER && actor.outranks(result);
    }

    public static boolean canDemote(ClanRank actor, ClanRank target) {
        if (target == ClanRank.LEADER || target == ClanRank.RECRUIT) {
            return false;
        }
        if (actor == ClanRank.LEADER) {
            return true;
        }
        return actor == ClanRank.OFFICER && actor.outranks(target);
    }
}
