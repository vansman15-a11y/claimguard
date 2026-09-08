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
import net.robmc.claimguard.claim.ClaimActions;
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

        // Claim Cores are a clan thing - you must be in a clan to place one.
        Optional<net.robmc.claimguard.clan.Clan> clan =
                net.robmc.claimguard.clan.ClanManager.get(serverPlayer.server).getClanOf(serverPlayer.getUUID());
        if (clan.isEmpty()) {
            rollbackPlacement(level, pos, serverPlayer, stack);
            serverPlayer.displayClientMessage(Component.literal(
                    "You must be in a clan to place a Claim Core. Found a Clan Charter to start one."), false);
            return;
        }

        // Post-conquest lockout: no re-claiming an area for a few minutes after its beacon falls.
        net.robmc.claimguard.siege.SiegeManager sieges =
                net.robmc.claimguard.siege.SiegeManager.get(serverPlayer.server);
        long now = serverPlayer.server.overworld().getGameTime();
        if (sieges.isLockedOut(((ServerLevel) level).dimension(), pos, now)) {
            rollbackPlacement(level, pos, serverPlayer, stack);
            serverPlayer.displayClientMessage(Component.literal(
                    "This area was just conquered - locked for another "
                            + sieges.lockoutSecondsLeft(((ServerLevel) level).dimension(), pos, now) + "s."), false);
            return;
        }

        // Minimum-spacing rule: refuse the placement if another claim's core is
        // within MIN_CLAIM_SPACING blocks. This has to happen BEFORE createClaim,
        // otherwise the check would find the claim we just made and reject it.
        Optional<Claim> tooClose = manager.findClaimTooCloseTo(pos);
        if (tooClose.isPresent()) {
            rollbackPlacement(level, pos, serverPlayer, stack);
            BlockPos other = tooClose.get().getCorePos();
            // Directional only, no coordinates - don't hand out enemy base locations.
            serverPlayer.displayClientMessage(Component.literal(
                    "Too close to an existing claim (roughly " + roughDistance(pos, other) + " blocks "
                            + compassDirection(pos, other) + "). Claims must be at least "
                            + ClaimManager.MIN_CLAIM_SPACING + " blocks apart."
            ), false);
            return;
        }

        Claim claim = manager.createClaim(pos, serverPlayer.getUUID(), clan.get().getId());

        if (level.getBlockEntity(pos) instanceof ClaimCoreBlockEntity blockEntity) {
            blockEntity.setOwner(serverPlayer.getUUID());
        }

        serverPlayer.displayClientMessage(
                Component.literal("Claim created! Protected area: " + describeArea(claim.getTier())),
                false
        );
    }

    private static final String[] COMPASS = {"north", "northeast", "east", "southeast",
            "south", "southwest", "west", "northwest"};

    /** 8-point compass direction from {@code from} toward {@code to} (Minecraft axes). */
    private static String compassDirection(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360; // 0 = north, 90 = east
        return COMPASS[(int) Math.round(deg / 45) % 8];
    }

    /** Horizontal distance, rounded to the nearest 50 so it isn't a pinpoint. */
    private static long roughDistance(BlockPos a, BlockPos b) {
        double d = Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getZ() - b.getZ(), 2));
        return Math.round(d / 50.0) * 50;
    }

    /** Pull the just-placed core back out of the world and refund it (survival only). */
    private static void rollbackPlacement(Level level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        level.removeBlock(pos, false);
        if (!player.isCreative()) {
            ItemStack refund = new ItemStack(stack.getItem());
            if (!player.getInventory().add(refund)) {
                player.drop(refund, false);
            }
        }
    }

    // --- Right-click: opens the claim menu for the owner ---
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            // Returning SUCCESS lets the client play the interact animation; the server
            // decides what actually happens (opens the menu, or denies) below.
            return InteractionResult.SUCCESS;
        }

        ClaimManager manager = ClaimManager.get((ServerLevel) level);
        Optional<Claim> maybeClaim = manager.getClaimByCore(pos);

        if (maybeClaim.isEmpty()) {
            // Shouldn't normally happen (the claim is created the moment the block is
            // placed) but guards against, e.g., claims wiped by an admin command.
            return InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            // Owner check, menu packet, and everything the menu triggers live in ClaimActions.
            ClaimActions.openMenu(serverPlayer, pos);
        }
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
        return size + "x" + size + " blocks, bedrock to sky";
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
