package net.robmc.claimguard.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.robmc.claimguard.clan.ClanManager;
import net.robmc.claimguard.registry.ModRecipes;

/**
 * A normal shaped recipe that only produces its result for a player who is in a
 * clan. Used for the Claim Core (clan beacon). Wraps a vanilla {@link ShapedRecipe}
 * for all the parsing/matching and just adds the membership check.
 *
 * The player is available during crafting via {@code ForgeHooks.getCraftingPlayer()}
 * - Forge sets it around the server-side recipe resolution.
 */
public class ClanGatedShapedRecipe implements CraftingRecipe {

    private final ShapedRecipe delegate;

    public ClanGatedShapedRecipe(ShapedRecipe delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (!delegate.matches(container, level)) {
            return false;
        }
        Player player = net.minecraftforge.common.ForgeHooks.getCraftingPlayer();
        if (player == null || level.isClientSide()) {
            return true; // no player context (autocrafter / client preview) - let it show
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return true;
        }
        return ClanManager.get(server).getClanOf(player.getUUID()).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return delegate.assemble(container, registryAccess);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return delegate.getResultItem(registryAccess);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return delegate.getIngredients();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public CraftingBookCategory category() {
        return delegate.category();
    }

    @Override
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CLAN_GATED_SHAPED.get();
    }

    public static class Serializer implements RecipeSerializer<ClanGatedShapedRecipe> {

        @Override
        public ClanGatedShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ClanGatedShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public ClanGatedShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ClanGatedShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ClanGatedShapedRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe.delegate);
        }
    }
}
