package net.robmc.claimguard.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks when each player was last in PvP. Used to deny safe-zone protection to
 * someone who was fighting seconds ago, so they can't gank outside a safe area
 * and duck back in to be invulnerable.
 */
public final class CombatTracker {

    /** How long after a PvP hit a player counts as "in combat". */
    public static final long COMBAT_TICKS = 200L; // 10 seconds

    private static final Map<UUID, Long> lastCombat = new HashMap<>();

    private CombatTracker() {
    }

    public static void tag(UUID playerId, long gameTime) {
        lastCombat.put(playerId, gameTime);
    }

    public static boolean inCombat(UUID playerId, long gameTime) {
        Long t = lastCombat.get(playerId);
        return t != null && gameTime - t < COMBAT_TICKS;
    }
}
