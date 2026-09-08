package net.robmc.claimguard.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.recipe.ClanGatedShapedRecipe;

/** Custom recipe serializers. */
public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ClaimGuard.MOD_ID);

    public static final RegistryObject<RecipeSerializer<ClanGatedShapedRecipe>> CLAN_GATED_SHAPED =
            SERIALIZERS.register("clan_gated_shaped", ClanGatedShapedRecipe.Serializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
