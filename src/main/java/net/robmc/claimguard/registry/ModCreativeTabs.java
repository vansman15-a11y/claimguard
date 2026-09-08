package net.robmc.claimguard.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.claimguard.ClaimGuard;

/** Gives the mod its own tab in the creative inventory so the Claim Core is easy to find/give. */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ClaimGuard.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CLAIMGUARD_TAB = TABS.register(
            "claimguard_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.claimguard"))
                    .icon(() -> ModItems.CLAIM_CORE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CLAIM_CORE.get());
                    })
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
