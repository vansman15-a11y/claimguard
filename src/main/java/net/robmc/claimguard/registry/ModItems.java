package net.robmc.claimguard.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.claimguard.ClaimGuard;

/**
 * Every placeable block also needs an Item registered - the Item is what actually
 * shows up in your inventory/hotbar and creative menu; the Block is what exists once
 * it's placed in the world. BlockItem is the standard bridge between the two.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ClaimGuard.MOD_ID);

    public static final RegistryObject<Item> CLAIM_CORE = ITEMS.register(
            "claim_core",
            () -> new BlockItem(ModBlocks.CLAIM_CORE.get(), new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
