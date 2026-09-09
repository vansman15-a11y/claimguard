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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.robmc.claimguard.block.entity.ClaimCoreBlockEntity;
import net.robmc.claimguard.claim.ClaimActions;
import net.robmc.claimguard.claim.ClaimManager;

import javax.annotation.Nullable;

/**
 * The operator version of a Claim Core: no clan, always maximum size, PvP disabled
 * inside. Used to protect spawn and safe zones. Not craftable - given via creative
 * or /give - and only an operator can place or break it.
 */
public class AdminCoreBlock extends BaseEntityBlock {

    public AdminCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            level.removeBlock(pos, false);
            if (!player.isCreative()) {
                ItemStack refund = new ItemStack(stack.getItem());
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
            player.displayClientMessage(Component.literal("Only operators can place an Admin Core."), true);
            return;
        }
        ClaimManager.get((ServerLevel) level).createAdminClaim(pos);
        player.displayClientMessage(Component.literal(
                "Admin protection zone created - maximum size, PvP disabled inside."), false);
    }

    /** Right-click: bind your respawn here, or leave the bind if already bound. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ClaimActions.toggleAdminBind(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ClaimManager.get(serverLevel).removeClaim(pos);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

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
        return null;
    }
}
