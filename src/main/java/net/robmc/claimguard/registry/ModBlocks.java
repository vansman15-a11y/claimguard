package net.robmc.claimguard.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.block.ClaimCoreBlock;

/**
 * DeferredRegister is Forge's way of registering things (blocks, items, etc.) safely.
 *
 * You don't call `new Block()` and expect the game to just know about it - Minecraft
 * needs every block/item to go through a registry so it can assign it a stable ID,
 * save/load it correctly, sync it to clients, etc. DeferredRegister collects all your
 * "I want to register this" requests and hands them to Forge at exactly the right
 * moment during game startup.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ClaimGuard.MOD_ID);

    public static final RegistryObject<Block> CLAIM_CORE = BLOCKS.register(
            "claim_core",
            () -> new ClaimCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .requiresCorrectToolForDrops()
                            .strength(50.0f, 1200.0f) // hard to break/blast-resistant, like a claim block should be
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel((state) -> 15) // it glows, matching the beacon-like top in your screenshot
                            // The beacon model is see-through glass. Without noOcclusion the game
                            // culls the touching faces of neighbouring blocks (assuming this cube
                            // hides them), so you end up looking straight through into caves/void.
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
            )
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
