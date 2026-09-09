package net.robmc.rpgstats;

import net.minecraftforge.fml.common.Mod;

/**
 * Second mod in this jar: a Dark-Age-of-Camelot-style stat system. Health,
 * Stamina and Mana pools (~300, cap 450) fed by six trainable stats.
 *
 * No registries yet - events auto-subscribe, the sync packet rides the ClaimGuard
 * network channel, and progression persists in RpgData (a SavedData).
 */
@Mod(RpgStats.MOD_ID)
public class RpgStats {

    public static final String MOD_ID = "rpgstats";

    public RpgStats() {
    }
}
