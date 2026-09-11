package com.villagehousing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class HouseEvents {

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (bypasses(player)) return;

        HouseClaim claim = HouseManager.get(level.getServer()).at(dim(level), event.getPos());
        if (claim == null || claim.type == HouseClaim.Type.NPC) return;
        if (claim.owner == null) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("This house is for sale. Buy it first."));
            return;
        }
        if (!claim.canEnter(player.getUUID())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You do not own this house."));
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (bypasses(player)) return;

        HouseClaim claim = HouseManager.get(level.getServer()).at(dim(level), event.getPos());
        if (claim == null) return;
        if (claim.type == HouseClaim.Type.NPC || claim.owner == null || !claim.canEnter(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        HouseManager manager = HouseManager.get(level.getServer());

        if (level.getBlockEntity(pos) instanceof SignBlockEntity) {
            HouseClaim bySign = manager.bySign(dim(level), pos);
            if (bySign != null) {
                handleSign(player, level, manager, bySign);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        HouseClaim claim = manager.at(dim(level), pos);
        if (claim == null || claim.type == HouseClaim.Type.NPC) return;

        boolean doorLike = level.getBlockState(pos).getBlock() instanceof DoorBlock
            || level.getBlockState(pos).getBlock() instanceof TrapDoorBlock
            || level.getBlockState(pos).getBlock() instanceof FenceGateBlock;

        if (doorLike || isContainerUse(event)) {
            if ((claim.owner == null || !claim.canEnter(player.getUUID())) && !bypasses(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                player.sendSystemMessage(Component.literal(doorLike ? "This door is locked." : "You don't have access to that."));
            }
        }
    }

    /** Pistons can't push or pull blocks across a buyable claim's border (their own owner's machines inside the claim still work). */
    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HouseManager manager = HouseManager.get(level.getServer());
        ResourceLocation dimension = dim(level);
        HouseClaim pistonClaim = manager.at(dimension, event.getPos());

        var helper = event.getStructureHelper();
        if (helper == null) return;
        for (BlockPos p : helper.getToPush()) {
            if (crossesBorder(manager, dimension, pistonClaim, p)) { event.setCanceled(true); return; }
        }
        for (BlockPos p : helper.getToDestroy()) {
            if (crossesBorder(manager, dimension, pistonClaim, p)) { event.setCanceled(true); return; }
        }
        if (crossesBorder(manager, dimension, pistonClaim, event.getFaceOffsetPos())) {
            event.setCanceled(true);
        }
    }

    private boolean crossesBorder(HouseManager manager, ResourceLocation dimension, HouseClaim pistonClaim, BlockPos target) {
        HouseClaim targetClaim = manager.at(dimension, target);
        return targetClaim != null && targetClaim.type == HouseClaim.Type.BUYABLE && targetClaim != pistonClaim;
    }

    /** Pays out any diamonds a player was owed from a sale while they were offline. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int amount = HouseManager.get(player.getServer()).takePendingPayout(player.getUUID());
        if (amount > 0) {
            player.addItem(new ItemStack(Items.DIAMOND, amount));
            player.sendSystemMessage(Component.literal("You received " + amount + " diamonds from a house sale while you were away."));
        }
    }

    private boolean bypasses(ServerPlayer player) {
        return player.hasPermissions(2) && player.isCreative();
    }

    private boolean isContainerUse(PlayerInteractEvent.RightClickBlock event) {
        var state = event.getLevel().getBlockState(event.getPos());
        return state.hasBlockEntity() && !(event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity);
    }

    private void handleSign(ServerPlayer player, ServerLevel level, HouseManager manager, HouseClaim claim) {
        if (claim.type == HouseClaim.Type.NPC) {
            player.sendSystemMessage(Component.literal("This is an NPC villager house."));
            return;
        }

        // Owner: right-clicking your own sign prints the commands you can use.
        if (claim.isOwner(player.getUUID())) {
            if (claim.isListed()) {
                player.sendSystemMessage(Component.literal("Your house is listed for " + claim.listPrice + " diamonds."));
                player.sendSystemMessage(Component.literal("Use /house unlist to cancel, or /house list <price> to change the price."));
            } else {
                player.sendSystemMessage(Component.literal("You own this house."));
                player.sendSystemMessage(Component.literal("Use /house list <diamonds> to sell it to another player."));
                player.sendSystemMessage(Component.literal("Use /house sell to sell it back for what you paid."));
                player.sendSystemMessage(Component.literal("Use /house invite <player> to allow one guest."));
            }
            return;
        }

        // Anyone else: this is a purchase attempt.
        boolean purchasable = claim.owner == null || claim.isListed();
        if (!purchasable) {
            player.sendSystemMessage(Component.literal("Owned by someone else. Not for sale."));
            return;
        }
        int price = claim.owner == null ? claim.defaultPrice : claim.listPrice;

        if (manager.livesSomewhere(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already live in a house. Sell or leave it first."));
            return;
        }
        if (price <= 0) {
            player.sendSystemMessage(Component.literal("No price set."));
            return;
        }

        int count = countDiamonds(player);
        if (count < price) {
            player.sendSystemMessage(Component.literal("Need " + price + " diamonds. You have " + count + "."));
            return;
        }

        takeDiamonds(player, price);
        UUID previousOwner = claim.owner;
        boolean wasListedSale = claim.isListed();
        if (previousOwner != null) {
            payout(level, previousOwner, price);
        }

        claim.owner = player.getUUID();
        claim.guest = null;
        claim.listPrice = 0;
        claim.purchasePrice = price;
        manager.setDirty();
        HouseSigns.update(level, claim);
        player.sendSystemMessage(Component.literal("You bought this house for " + price
                + (wasListedSale ? " diamonds from its previous owner." : " diamonds.")));
    }

    private int countDiamonds(ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(Items.DIAMOND)) n += stack.getCount();
        }
        return n;
    }

    private void takeDiamonds(ServerPlayer player, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.is(Items.DIAMOND)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /** Pays the previous owner now if they're online, otherwise queues it for their next login. */
    private void payout(ServerLevel level, UUID owner, int amount) {
        ServerPlayer seller = level.getServer().getPlayerList().getPlayer(owner);
        if (seller != null) {
            seller.addItem(new ItemStack(Items.DIAMOND, amount));
            seller.sendSystemMessage(Component.literal("Your house sold for " + amount + " diamonds."));
        } else {
            HouseManager.get(level.getServer()).addPendingPayout(owner, amount);
        }
    }

    private ResourceLocation dim(ServerLevel level) {
        return level.dimension().location();
    }
}
