package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.bind.BindManager;
import net.robmc.claimguard.clan.Clan;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.claimguard.clan.ClanManager;
import net.robmc.claimguard.clan.ClanMember;
import net.robmc.claimguard.clan.ClanRank;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.OpenClaimMenuPacket;
import net.robmc.claimguard.network.ShowClaimBorderPacket;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side logic behind the claim menu: opening it, upgrading, removing, and
 * flashing the border. Called from ClaimCoreBlock (right-click) and from
 * ClaimActionPacket (menu button presses).
 *
 * Every entry point re-checks ownership and re-reads the claim, so a stale or
 * spoofed packet can't act on someone else's claim.
 */
public final class ClaimActions {

    /** How long the border outline shows (ticks). Re-triggering hides it early - see ClaimBorderClient. */
    public static final int BORDER_DISPLAY_TICKS = 600; // 30 seconds

    private ClaimActions() {
    }

    /** Right-click entry point: open the menu for a clan member or an allied player. */
    public static void openMenu(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = accessibleClaim(player, corePos);
        if (claim.isEmpty()) {
            return;
        }
        sendMenu(player, claim.get());
    }

    public static void showBorder(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> maybeClaim = accessibleClaim(player, corePos);
        if (maybeClaim.isEmpty()) {
            return;
        }
        // The claim protects bedrock-to-sky, but drawing a 384-block-tall cage is
        // unreadable - show a waist-high "fence" around the player's eye level instead.
        int centreY = (int) player.getY();
        var bounds = maybeClaim.get().getBounds(centreY - 6, centreY + 24);
        ClaimGuardNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ShowClaimBorderPacket(
                        (int) bounds.minX, (int) bounds.minY, (int) bounds.minZ,
                        (int) bounds.maxX, (int) bounds.maxY, (int) bounds.maxZ,
                        BORDER_DISPLAY_TICKS
                )
        );
    }

    public static void tryUpgrade(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> maybeClaim = memberClaim(player, corePos);
        if (maybeClaim.isEmpty()) {
            return;
        }
        Claim claim = maybeClaim.get();
        ClaimTier tier = claim.getTier();
        List<ItemStack> cost = tier.getUpgradeCost();

        if (cost.isEmpty()) {
            player.displayClientMessage(Component.literal("This claim is already at maximum size."), true);
            return;
        }
        if (!hasAll(player, cost)) {
            player.displayClientMessage(Component.literal(
                    "Upgrade costs " + tier.describeUpgradeCost() + ". " + shortfall(player, cost)
            ), true);
            sendMenu(player, claim); // refresh so the screen stays in sync
            return;
        }

        consumeAll(player, cost);
        ClaimManager.get(player.serverLevel()).upgrade(corePos);

        Claim updated = ClaimManager.get(player.serverLevel()).getClaimByCore(corePos).orElse(claim);
        player.displayClientMessage(Component.literal(
                "Claim upgraded to Level " + updated.getLevel() + " - "
                        + updated.getTier().describeFootprint() + " blocks, bedrock to sky."
        ), false);
        sendMenu(player, updated);
    }

    public static void remove(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> maybeClaim = memberClaim(player, corePos);
        if (maybeClaim.isEmpty()) {
            return;
        }
        ClanRank rank = rankInOwningClan(player, maybeClaim.get());
        if (rank != null && rank != ClanRank.LEADER) {
            player.displayClientMessage(Component.literal("Only the clan Leader can remove a claim."), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        ClaimManager.get(level).removeClaim(corePos);
        // Drop the core in survival so it can be re-placed elsewhere; creative just deletes it.
        level.destroyBlock(corePos, !player.isCreative(), player);
        player.displayClientMessage(Component.literal("Claim removed."), false);
    }

    // --- helpers ---

    private static boolean isMemberOfOwningClan(ServerPlayer player, Claim claim) {
        if (claim.getClanId() != null) {
            return ClanManager.get(player.server).getClanOf(player.getUUID())
                    .map(cl -> cl.getId().equals(claim.getClanId())).orElse(false);
        }
        return claim.isOwnedBy(player.getUUID());
    }

    /** Claim at this core if the player is a member of the owning clan (or the legacy owner). */
    private static Optional<Claim> memberClaim(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = ClaimManager.get(player.serverLevel()).getClaimByCore(corePos);
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        if (!isMemberOfOwningClan(player, claim.get())) {
            player.displayClientMessage(Component.literal("This claim belongs to another clan."), true);
            return Optional.empty();
        }
        return claim;
    }

    /** Claim at this core if the player is a member OR in an allied clan. */
    private static Optional<Claim> accessibleClaim(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = ClaimManager.get(player.serverLevel()).getClaimByCore(corePos);
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        Claim c = claim.get();
        if (isMemberOfOwningClan(player, c)) {
            return claim;
        }
        if (c.getClanId() != null
                && ClanActions.isAllyOf(player.server, c.getClanId(), player.getUUID())) {
            return claim;
        }
        player.displayClientMessage(Component.literal("This claim belongs to another clan."), true);
        return Optional.empty();
    }

    /** The player's rank in the clan that owns this claim, or null if not a clan member of it. */
    private static ClanRank rankInOwningClan(ServerPlayer player, Claim claim) {
        if (claim.getClanId() == null) {
            return null;
        }
        return ClanManager.get(player.server).getClanOf(player.getUUID())
                .filter(c -> c.getId().equals(claim.getClanId()))
                .flatMap(c -> c.getMember(player.getUUID()))
                .map(ClanMember::getRank)
                .orElse(null);
    }

    // --- bindstone ---

    public static void bind(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = accessibleClaim(player, corePos); // members AND allies may bind
        if (claim.isEmpty()) {
            return;
        }

        BindManager binds = BindManager.get(player.server);
        if (!binds.isBoundTo(player.getUUID(), corePos) && exceedsClanBindstoneCap(player, corePos, binds)) {
            player.displayClientMessage(Component.literal(
                    "Your clan already has " + Clan.MAX_BINDSTONES
                            + " bindstones. Someone needs to Leave bind first."), true);
            return;
        }

        binds.setBind(player.getUUID(), corePos, player.serverLevel().dimension());
        player.displayClientMessage(Component.literal("Bound to this claim. You'll revive here when you die."), false);
        sendMenu(player, claim.get());
    }

    private static boolean exceedsClanBindstoneCap(ServerPlayer player, BlockPos corePos, BindManager binds) {
        Optional<Clan> clan = ClanManager.get(player.server).getClanOf(player.getUUID());
        if (clan.isEmpty()) {
            return false;
        }
        Set<BlockPos> used = new HashSet<>();
        clan.get().getMembers().forEach(m -> binds.getBind(m.getId()).ifPresent(b -> used.add(b.pos())));
        return !used.contains(corePos) && used.size() >= Clan.MAX_BINDSTONES;
    }

    public static void unbind(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = accessibleClaim(player, corePos);
        if (claim.isEmpty()) {
            return;
        }
        BindManager.get(player.server).clearBind(player.getUUID());
        player.displayClientMessage(Component.literal("Bind removed. You'll respawn at your bed, or world spawn if you have none."), false);
        sendMenu(player, claim.get());
    }

    private static void sendMenu(ServerPlayer player, Claim claim) {
        String clanName = ClanManager.get(player.server).clanNameOrNull(claim.getClanId());
        String name = clanName != null ? clanName : player.getGameProfile().getName();
        boolean boundHere = BindManager.get(player.server).isBoundTo(player.getUUID(), claim.getCorePos());
        boolean canManage = isMemberOfOwningClan(player, claim);
        ClaimGuardNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenClaimMenuPacket(claim.getCorePos(), claim.getTier().ordinal(), name, boundHere, canManage)
        );
    }

    private static boolean hasAll(ServerPlayer player, List<ItemStack> cost) {
        for (ItemStack stack : cost) {
            if (countItem(player, stack.getItem()) < stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** "you have 30 Diamond, 0 Blaze Rod." - only the items still short. */
    private static String shortfall(ServerPlayer player, List<ItemStack> cost) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack stack : cost) {
            int have = countItem(player, stack.getItem());
            if (have < stack.getCount()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append("you have ").append(have).append(" ").append(stack.getHoverName().getString());
            }
        }
        return sb.length() == 0 ? "" : sb.append(".").toString();
    }

    private static int countItem(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                total += inv.getItem(i).getCount();
            }
        }
        return total;
    }

    private static void consumeAll(ServerPlayer player, List<ItemStack> cost) {
        Inventory inv = player.getInventory();
        for (ItemStack needed : cost) {
            int remaining = needed.getCount();
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(needed.getItem())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
    }
}
