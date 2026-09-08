package net.robmc.claimguard.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.robmc.claimguard.block.entity.ClaimCoreBlockEntity;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;
import net.robmc.claimguard.claim.ClaimTier;
import net.robmc.claimguard.registry.ModBlockEntities;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The block players place to start (and later upgrade) a claim.
 *
 * Extends BaseEntityBlock rather than plain Block because it needs a BlockEntity
 * (see ClaimCoreBlockEntity) to remember its owner.
 */
public class ClaimCoreBlock extends BaseEntityBlock {

    public ClaimCoreBlock(Properties properties) {
        super(properties);
    }

    // --- Placement: this runs the instant the block is placed in the world ---
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide() || !(placer instanceof ServerPlayer serverPlayer)) {
            // isClientSide() is true on the player's own screen-rendering copy of the
            // game; we only want this logic to run once, on the authoritative server.
            return;
        }

        ClaimManager manager = ClaimManager.get((ServerLevel) level);

        // Minimum-spacing rule: refuse the placement if another claim's core is
        // within MIN_CLAIM_SPACING blocks. This has to happen BEFORE createClaim,
        // otherwise the check would find the claim we just made and reject it.
        Optional<Claim> tooClose = manager.findClaimTooCloseTo(pos);
        if (tooClose.isPresent()) {
            level.removeBlock(pos, false); // pull the block back out of the world
            if (!serverPlayer.isCreative()) {
                // Survival: the item was already spent placing the block - hand it back.
                ItemStack refund = new ItemStack(stack.getItem());
                if (!serverPlayer.getInventory().add(refund)) {
                    serverPlayer.drop(refund, false);
                }
            }
            BlockPos other = tooClose.get().getCorePos();
            serverPlayer.displayClientMessage(Component.literal(
                    "Too close to an existing claim (core at " + other.getX() + ", " + other.getY()
                            + ", " + other.getZ() + "). Claims must be at least "
                            + ClaimManager.MIN_CLAIM_SPACING + " blocks apart."
            ), false);
            return;
        }

        Claim claim = manager.createClaim(pos, serverPlayer.getUUID());

        if (level.getBlockEntity(pos) instanceof ClaimCoreBlockEntity blockEntity) {
            blockEntity.setOwner(serverPlayer.getUUID());
        }

        serverPlayer.displayClientMessage(
                Component.literal("Claim created! Protected area: " + describeArea(claim.getTier())),
                false
        );
    }

    // --- Right-click: this is where upgrading happens ---
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            // Returning SUCCESS here lets the client play the "interact" animation/sound
            // without duplicating the actual logic - the server is doing the real work below.
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ClaimManager manager = ClaimManager.get(serverLevel);
        Optional<Claim> maybeClaim = manager.getClaimByCore(pos);

        if (maybeClaim.isEmpty()) {
            // Shouldn't normally happen (the claim is created the moment the block is
            // placed) but guards against, e.g., claims wiped by an admin command.
            return InteractionResult.PASS;
        }

        Claim claim = maybeClaim.get();

        if (!claim.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("This claim belongs to someone else."), true);
            return InteractionResult.FAIL;
        }

        ClaimTier currentTier = claim.getTier();

        if (currentTier.isMaxTier()) {
            player.displayClientMessage(Component.literal("This claim is already at maximum size."), true);
            return InteractionResult.FAIL;
        }

        ItemStack held = player.getItemInHand(hand);
        boolean holdingCorrectItem = held.is(currentTier.getUpgradeItem());

        if (!holdingCorrectItem) {
            player.displayClientMessage(Component.literal(
                    "Current size: " + describeArea(currentTier)
                            + ". Hold " + currentTier.getUpgradeCost() + "x "
                            + currentTier.getUpgradeItem().getDescription().getString()
                            + " and right-click to upgrade."
            ), true);
            return InteractionResult.PASS;
        }

        if (held.getCount() < currentTier.getUpgradeCost()) {
            player.displayClientMessage(Component.literal(
                    "You need " + currentTier.getUpgradeCost() + "x "
                            + currentTier.getUpgradeItem().getDescription().getString()
                            + " to upgrade (you have " + held.getCount() + ")."
            ), true);
            return InteractionResult.FAIL;
        }

        // Consume the items and upgrade.
        held.shrink(currentTier.getUpgradeCost());
        manager.upgrade(pos);

        ClaimTier newTier = manager.getClaimByCore(pos).get().getTier();
        player.displayClientMessage(Component.literal(
                "Claim upgraded! New protected area: " + describeArea(newTier)
        ), false);

        return InteractionResult.CONSUME;
    }

    // --- Breaking: only the owner (or an operator) should be able to remove the core ---
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ClaimManager manager = ClaimManager.get(serverLevel);
            boolean hadClaim = manager.getClaimByCore(pos).isPresent();
            manager.removeClaim(pos);

            if (hadClaim) {
                player.displayClientMessage(Component.literal("Claim removed."), false);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    private static String describeArea(ClaimTier tier) {
        int size = tier.getRadius() * 2 + 1;
        return size + "x" + size + "x" + size + " blocks";
    }

    // --- Boilerplate required because this block has a BlockEntity ---

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClaimCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // No per-tick behavior needed right now (no animation/particles yet), so null is fine.
        return null;
    }
}
