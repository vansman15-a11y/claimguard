package net.robmc.claimguard.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.block.entity.ClaimCoreBlockEntity;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ClaimGuard.MOD_ID);

    public static final RegistryObject<BlockEntityType<ClaimCoreBlockEntity>> CLAIM_CORE =
            BLOCK_ENTITIES.register("claim_core", () -> BlockEntityType.Builder.of(
                    ClaimCoreBlockEntity::new,
                    ModBlocks.CLAIM_CORE.get()
            ).build(null));
            // The `null` above is a DataFixer type argument Mojang uses for very old-world
            // migration - it's safe to leave null for a brand new block type like ours.

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
