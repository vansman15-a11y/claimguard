package net.robmc.claimguard;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.registry.ModBlockEntities;
import net.robmc.claimguard.registry.ModBlocks;
import net.robmc.claimguard.registry.ModCreativeTabs;
import net.robmc.claimguard.registry.ModItems;
import net.robmc.claimguard.registry.ModRecipes;

/**
 * The mod's entry point. @Mod("claimguard") is what actually tells Forge "this class
 * is a mod, load it" - it must match the modid in mods.toml exactly.
 *
 * There's exactly one of these per mod. Its constructor runs once, very early, during
 * game startup - this is where you hook your registries up to the game's event bus.
 */
@Mod(ClaimGuard.MOD_ID)
public class ClaimGuard {

    public static final String MOD_ID = "claimguard";

    public ClaimGuard() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModRecipes.register(modEventBus);
        ClaimGuardNetwork.register();

        // ProtectionEvents and TerritoryEvents register themselves via
        // @Mod.EventBusSubscriber - nothing to do here for them.
    }
}