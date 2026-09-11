package net.robmc.rpgstats.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;

import java.util.function.Supplier;

/**
 * Ore blocks for the five rare materials that feed the tiers above Netherite.
 * Not generated in the world yet - registered so the items exist and look
 * right while the crafting chain for Full Plate / Infernal / Dragon is worked out.
 */
public final class RpgBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RpgStats.MOD_ID);

    public static final RegistryObject<Block> SELENTINE_ORE = ore("selentine_ore");
    public static final RegistryObject<Block> LEENSPAR_ORE = ore("leenspar_ore");
    public static final RegistryObject<Block> NEITHAL_ORE = ore("neithal_ore");
    public static final RegistryObject<Block> VEILRON_ORE = ore("veilron_ore");
    public static final RegistryObject<Block> THEYRIL_ORE = ore("theyril_ore");

    private static RegistryObject<Block> ore(String name) {
        return BLOCKS.register(name, (Supplier<Block>) () -> new DropExperienceBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .requiresCorrectToolForDrops()
                        .strength(4.0F, 6.0F)
                        .pushReaction(PushReaction.NORMAL),
                net.minecraft.util.valueproviders.UniformInt.of(3, 7)));
    }

    private RpgBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
