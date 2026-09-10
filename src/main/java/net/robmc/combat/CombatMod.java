package net.robmc.combat;

import net.minecraftforge.fml.common.Mod;
import net.robmc.combat.network.CombatNetwork;

/**
 * Reforged Melee - a standalone melee overhaul. Third-person while a melee
 * weapon is out, slower/heavier swings, a wide cleave arc, and a 5-hit combo
 * that crits on the finisher. Nothing here depends on the other mods in the jar.
 */
@Mod(CombatMod.MODID)
public class CombatMod {

    public static final String MODID = "combat";

    public CombatMod() {
        CombatNetwork.register();
    }
}
