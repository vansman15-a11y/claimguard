package net.robmc.claimguard.siege;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.robmc.claimguard.bind.BindManager;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;
import net.robmc.claimguard.clan.Clan;
import net.robmc.claimguard.clan.ClanManager;
import net.robmc.claimguard.clan.ClanMember;
import net.robmc.claimguard.clan.ClanRelation;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side siege logic: starting one from the clan-browse screen, counting
 * pickaxe hits on the beacon, and resolving a win/loss.
 */
public final class SiegeActions {

    private SiegeActions() {
    }

    public static void startSiege(ServerPlayer attacker, UUID targetClanId) {
        MinecraftServer server = attacker.server;
        ClanManager clans = ClanManager.get(server);

        Optional<Clan> myClan = clans.getClanOf(attacker.getUUID());
        if (myClan.isEmpty()) {
            deny(attacker, "You're not in a clan.");
            return;
        }
        if (myClan.get().getId().equals(targetClanId)) {
            deny(attacker, "You can't siege your own clan.");
            return;
        }
        Optional<Clan> targetClan = clans.getClanById(targetClanId);
        if (targetClan.isEmpty()) {
            deny(attacker, "That clan no longer exists.");
            return;
        }
        if (myClan.get().getRelation(targetClanId) != ClanRelation.ENEMY) {
            deny(attacker, "Mark " + targetClan.get().getName() + " as an Enemy first.");
            return;
        }
        if (!targetClan.get().isRaidWindowOpenNow()) {
            deny(attacker, targetClan.get().getName() + " can only be sieged during "
                    + targetClan.get().formatRaidWindow() + " (server time).");
            return;
        }

        ServerLevel level = attacker.serverLevel();
        Optional<Claim> claim = ClaimManager.get(level).getClaimAt(attacker.blockPosition());
        if (claim.isEmpty() || !targetClanId.equals(claim.get().getClanId())) {
            deny(attacker, "Stand inside " + targetClan.get().getName() + "'s territory to start the siege.");
            return;
        }

        SiegeManager sieges = SiegeManager.get(server);
        BlockPos core = claim.get().getCorePos();
        if (sieges.isUnderSiege(core)) {
            deny(attacker, "That claim is already under siege.");
            return;
        }

        long now = server.overworld().getGameTime();
        sieges.startSiege(new SiegeManager.Siege(
                myClan.get().getId(), targetClanId, core, level.dimension(),
                SiegeManager.BEACON_MAX_HITS, now));

        broadcast(server, myClan.get(), Component.literal(
                "Siege started on " + targetClan.get().getName() + ". Break the beacon: 0 / "
                        + SiegeManager.BEACON_MAX_HITS + " hits.").withStyle(ChatFormatting.GOLD));
        broadcast(server, targetClan.get(), Component.literal(
                "⚠ " + myClan.get().getName() + " is sieging your claim at "
                        + core.getX() + ", " + core.getZ() + "!").withStyle(ChatFormatting.RED));
    }

    /** One accepted pickaxe hit on a besieged beacon. */
    public static void hitBeacon(ServerPlayer attacker, BlockPos core) {
        MinecraftServer server = attacker.server;
        SiegeManager sieges = SiegeManager.get(server);
        SiegeManager.Siege siege = sieges.getSiege(core);
        if (siege == null) {
            return;
        }
        boolean isAttacker = ClanManager.get(server).getClanOf(attacker.getUUID())
                .map(c -> c.getId().equals(siege.attackerClan)).orElse(false);
        if (!isAttacker) {
            attacker.displayClientMessage(Component.literal("Only the attacking clan can damage this beacon."), true);
            return;
        }

        siege.hitsRemaining--;
        siege.lastActivityTick = server.overworld().getGameTime();
        sieges.markDirty();

        int done = SiegeManager.BEACON_MAX_HITS - siege.hitsRemaining;
        if (siege.hitsRemaining <= 0) {
            attackersWin(server, siege);
        } else {
            attacker.displayClientMessage(Component.literal(
                    "Beacon: " + done + " / " + SiegeManager.BEACON_MAX_HITS), true);
        }
    }

    public static void attackersWin(MinecraftServer server, SiegeManager.Siege siege) {
        SiegeManager sieges = SiegeManager.get(server);
        ServerLevel level = server.getLevel(siege.dimension);
        int radius = 8;

        if (level != null) {
            ClaimManager claims = ClaimManager.get(level);
            Optional<Claim> claim = claims.getClaimByCore(siege.core);
            if (claim.isPresent()) {
                radius = claim.get().getRadius();
            }
            claims.removeClaim(siege.core);
            level.destroyBlock(siege.core, false);
        }
        BindManager.get(server).clearBindsTo(siege.core);

        long now = server.overworld().getGameTime();
        sieges.addLockout(siege.core, radius, siege.dimension, now);
        sieges.endSiege(siege.core);

        ClanManager clans = ClanManager.get(server);
        clans.getClanById(siege.attackerClan).ifPresent(c -> broadcast(server, c, Component.literal(
                "Siege won! The enemy beacon is destroyed. The area unlocks for anyone in "
                        + (SiegeManager.LOCKOUT_TICKS / 1200) + " minutes.").withStyle(ChatFormatting.GREEN)));
        clans.getClanById(siege.defenderClan).ifPresent(c -> broadcast(server, c, Component.literal(
                "Your claim has fallen. The beacon is destroyed and the area is unprotected.").withStyle(ChatFormatting.RED)));
    }

    public static void defendersWin(MinecraftServer server, SiegeManager.Siege siege, String reason) {
        SiegeManager.get(server).endSiege(siege.core);
        ClanManager clans = ClanManager.get(server);
        clans.getClanById(siege.defenderClan).ifPresent(c -> broadcast(server, c, Component.literal(
                "Siege repelled - " + reason + ". Your beacon held.").withStyle(ChatFormatting.GREEN)));
        clans.getClanById(siege.attackerClan).ifPresent(c -> broadcast(server, c, Component.literal(
                "Siege failed - " + reason + ".").withStyle(ChatFormatting.YELLOW)));
    }

    // --- helpers ---

    private static void deny(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void broadcast(MinecraftServer server, Clan clan, Component message) {
        for (ClanMember member : clan.getMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member.getId());
            if (online != null) {
                online.sendSystemMessage(message);
            }
        }
    }
}
