package net.robmc.claimguard.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;
import net.robmc.claimguard.registry.ModBlocks;

import java.util.Optional;

/**
 * This class doesn't get called directly by anything in our own code. Instead, the
 * @Mod.EventBusSubscriber annotation below tells Forge "scan this class for methods
 * marked @SubscribeEvent, and call them automatically whenever the matching event
 * happens anywhere in the game." This is the closest Forge equivalent to FiveM's
 * AddEventHandler("someEvent", ...) - except the "event name" is the Java type of the
 * method's parameter (e.g. BlockEvent.BreakEvent), not a string.
 *
 * bus = Bus.FORGE means "listen to gameplay events" (as opposed to Bus.MOD, which is
 * for mod-loading-lifecycle events like registering blocks).
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = event.getPlayer();
        if (isAllowed(serverLevel, event.getPos(), player)) {
            return;
        }
        event.setCanceled(true);
        sendDenyMessage(player);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // e.g. a piston pushing a block, not a player placing one - allow it
        }
        if (isAllowed(serverLevel, event.getPos(), player)) {
            return;
        }
        event.setCanceled(true);
        sendDenyMessage(player);
    }

    /**
     * Enforces {@link ClaimManager#MIN_CLAIM_SPACING}: a new Claim Core can't be
     * placed too close to an existing claim. Cancelling the place event makes Forge
     * revert the block and hand the item back to the player automatically.
     *
     * Separate from {@link #onBlockPlace} because this rule is specific to the
     * Claim Core block, not the general "don't build in someone's claim" rule.
     */
    @SubscribeEvent
    public static void onClaimCorePlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!event.getPlacedBlock().is(ModBlocks.CLAIM_CORE.get())) {
            return;
        }

        Optional<Claim> tooClose = ClaimManager.get(serverLevel).findClaimTooCloseTo(event.getPos());
        if (tooClose.isEmpty()) {
            return;
        }

        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            BlockPos core = tooClose.get().getCorePos();
            player.displayClientMessage(Component.literal(
                    "Too close to an existing claim (core at " + core.getX() + ", " + core.getY()
                            + ", " + core.getZ() + "). Claims must be at least "
                            + ClaimManager.MIN_CLAIM_SPACING + " blocks apart."
            ), true);
        }
    }

    /**
     * Blocks non-owners from opening containers (chests, furnaces, doors, etc.) and
     * other right-click interactions inside someone else's claim.
     * NOTE: this currently blocks ALL right-click block interactions for non-owners
     * inside a claim, including things like pressure plates or crafting tables. If you
     * want to allow certain "harmless" interactions later, this is the method to extend
     * with an allow-list.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = event.getEntity();
        if (isAllowed(serverLevel, event.getPos(), player)) {
            return;
        }
        event.setCanceled(true);
        sendDenyMessage(player);
    }

    /** Stops explosions (TNT, creepers, etc.) from destroying blocks inside a claim. */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ClaimManager manager = ClaimManager.get(serverLevel);
        event.getAffectedBlocks().removeIf(pos -> manager.getClaimAt(pos).isPresent());
    }

    /**
     * Central rule: is this player allowed to affect this position?
     * True if there's no claim here at all, OR the player owns the claim that's here,
     * OR the player is a server operator (so admins can always intervene).
     */
    private static boolean isAllowed(ServerLevel level, net.minecraft.core.BlockPos pos, Player player) {
        if (player == null) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
            return true; // operators bypass claim protection
        }
        ClaimManager manager = ClaimManager.get(level);
        Optional<Claim> claim = manager.getClaimAt(pos);
        return claim.isEmpty() || claim.get().isOwnedBy(player.getUUID());
    }

    private static void sendDenyMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(Component.literal("This area is protected by a claim."), true);
        }
    }
}
