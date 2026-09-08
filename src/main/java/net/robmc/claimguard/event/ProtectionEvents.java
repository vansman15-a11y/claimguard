package net.robmc.claimguard.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
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

        // A beacon core is never hand-broken. Operators may (admin cleanup); anyone
        // else gets pointed at the menu. Enemies bring one down through the siege
        // hit system, not by mining.
        if (event.getState().is(net.robmc.claimguard.registry.ModBlocks.CLAIM_CORE.get())
                || event.getState().is(net.robmc.claimguard.registry.ModBlocks.ADMIN_CORE.get())) {
            if (player instanceof ServerPlayer op && op.hasPermissions(2)) {
                return; // operators may break a core (admin cleanup)
            }
            event.setCanceled(true);
            if (player != null) {
                boolean ownClaim = ClaimManager.get(serverLevel).getClaimByCore(event.getPos())
                        .filter(c -> c.getClanId() != null)
                        .map(c -> net.robmc.claimguard.clan.ClanManager.get(serverLevel.getServer())
                                .getClanOf(player.getUUID()).map(cl -> cl.getId().equals(c.getClanId())).orElse(false))
                        .orElse(false);
                player.displayClientMessage(Component.literal(ownClaim
                        ? "Right-click the beacon - only the Leader can take it down (Remove)."
                        : "This beacon can't be broken by hand."), true);
            }
            return;
        }

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

    /**
     * Stops explosions from destroying blocks inside a claim - EXCEPT a claim
     * that's currently under siege, where TNT is the only way through the walls.
     * The beacon block itself stays explosion-proof even during a siege (it can
     * only be brought down by pickaxe hits).
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ClaimManager manager = ClaimManager.get(serverLevel);
        net.robmc.claimguard.siege.SiegeManager sieges =
                net.robmc.claimguard.siege.SiegeManager.get(serverLevel.getServer());
        event.getAffectedBlocks().removeIf(pos -> {
            Optional<Claim> claim = manager.getClaimAt(pos);
            if (claim.isEmpty()) {
                return false;
            }
            net.minecraft.core.BlockPos core = claim.get().getCorePos();
            if (pos.equals(core)) {
                return true; // beacon is always explosion-proof
            }
            return !sieges.isUnderSiege(core); // sieged claim: let TNT through
        });
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
        if (c.isAdmin()) {
            return false; // admin zones: only operators (who already returned true above)
        }
        if (c.getClanId() != null) {
            return net.robmc.claimguard.clan.ClanManager.get(level.getServer())
                    .canBuildInClanClaim(c.getClanId(), player.getUUID());
        }
        return c.isOwnedBy(player.getUUID());
    }

    /**
     * No hostile-mob spawns inside an admin zone. PositionCheck is the earliest,
     * most reliable hook (before the mob entity is even built), covering natural
     * spawns and spawners; FinalizeSpawn is a backstop for anything that slips past.
     */
    @SubscribeEvent
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity() instanceof Enemy && inAdminZone(event.getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Enemy && inAdminZone(event.getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }

    /** Sweeps hostile mobs out of admin zones a few times a minute (catches strays and pre-existing mobs). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 60L != 0L) { // every 3 seconds
            return;
        }
        ClaimManager manager = ClaimManager.get(level);
        if (!manager.hasAdminClaims()) {
            return;
        }
        for (Claim claim : manager.getAllClaims()) {
            if (!claim.isAdmin()) {
                continue;
            }
            AABB box = claim.getBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
            for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof Enemy)) {
                entity.discard();
            }
        }
    }

    private static boolean inAdminZone(ServerLevelAccessor levelAccessor, double x, double y, double z) {
        ServerLevel level = levelAccessor.getLevel();
        ClaimManager manager = ClaimManager.get(level);
        return manager.hasAdminClaims() && manager.isInAdminClaim(BlockPos.containing(x, y, z));
    }

    /**
     * No player-vs-player damage when EITHER the victim or the attacker is standing
     * in an admin zone (stops both spawn-killing and sniping out of a safe zone).
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player attackerPlayer)) {
            return; // only cancel player-vs-player
        }
        ClaimManager manager = ClaimManager.get(level);
        if (!manager.hasAdminClaims()) {
            return;
        }
        if (manager.isInAdminClaim(victim.blockPosition()) || manager.isInAdminClaim(attackerPlayer.blockPosition())) {
            event.setCanceled(true);
        }
    }

    private static void sendDenyMessage(Player player) {
        if (player != null) {
            player.displayClientMessage(Component.literal("This area is protected by a claim."), true);
        }
    }
}
