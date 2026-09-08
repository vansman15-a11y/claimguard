package net.robmc.claimguard.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;

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

    /**
     * Cleans up claims whose core block has gone missing (worldedit, /setblock,
     * chunk regen, or an old buggy build). Runs as each chunk loads.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ClaimManager.get(serverLevel).forgetClaimsWithMissingCore(event.getChunk());
        }
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
     * Allowed if there's no claim here, the player is an operator, or the player is
     * a member (with build access) of the clan that owns the claim. Claims with no
     * clan (legacy) fall back to the single owner UUID.
     */
    private static boolean isAllowed(ServerLevel level, net.minecraft.core.BlockPos pos, Player player) {
        if (player == null) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
            return true; // operators bypass claim protection
        }
        Optional<Claim> claim = ClaimManager.get(level).getClaimAt(pos);
        if (claim.isEmpty()) {
            return true;
        }
        Claim c = claim.get();
        if (c.getClanId() != null) {
            return net.robmc.claimguard.clan.ClanManager.get(level.getServer())
                    .canBuildInClanClaim(c.getClanId(), player.getUUID());
        }
        return c.isOwnedBy(player.getUUID());
    }

    private static void sendDenyMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(Component.literal("This area is protected by a claim."), true);
        }
    }
}
