package net.robmc.rpgstats.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;

/**
 * Custom status effects. Currently just Nourished - a pure visual/HUD marker for
 * whether food's 30-minute regen gate is up (see net.robmc.rpgstats.food); the
 * actual gating logic lives in NourishmentManager/RpgManager.regenTick, this is
 * just so the buff shows up in the vanilla effect-icon HUD like everything else.
 */
public final class RpgEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgStats.MOD_ID);

    public static final RegistryObject<MobEffect> NOURISHED = EFFECTS.register("nourished",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x55DD55) {
            });

    private RpgEffects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
