package net.robmc.rpgstats;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.robmc.rpgstats.registry.RpgEntities;

/**
 * Second mod in this jar: a Dark-Age-of-Camelot-style stat system. Health,
 * Stamina and Mana pools (~300, cap 450) fed by six trainable stats, plus a
 * small Weak Magic spell school.
 *
 * Progression persists in RpgData (a SavedData); packets ride the ClaimGuard
 * network channel; events auto-subscribe.
 */
@Mod(RpgStats.MOD_ID)
public class RpgStats {

    public static final String MOD_ID = "rpgstats";

    public RpgStats() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RpgEntities.register(modEventBus);
    }
}
