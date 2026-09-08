package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.OpenClaimMenuPacket;
import net.robmc.claimguard.network.ShowClaimBorderPacket;

import java.util.List;
import java.util.Optional;

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

    /** Right-click entry point: open the menu for the owner, or tell others whose it is. */
    public static void openMenu(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = ownedClaim(player, corePos);
        if (claim.isEmpty()) {
            return;
        }
        sendMenu(player, claim.get());
    }

    public static void showBorder(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> maybeClaim = ownedClaim(player, corePos);
        if (maybeClaim.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var bounds = maybeClaim.get().getBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
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
        Optional<Claim> maybeClaim = ownedClaim(player, corePos);
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
        Optional<Claim> maybeClaim = ownedClaim(player, corePos);
        if (maybeClaim.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ClaimManager.get(level).removeClaim(corePos);
        // Drop the core in survival so it can be re-placed elsewhere; creative just deletes it.
        level.destroyBlock(corePos, !player.isCreative(), player);
        player.displayClientMessage(Component.literal("Claim removed."), false);
    }

    // --- helpers ---

    private static Optional<Claim> ownedClaim(ServerPlayer player, BlockPos corePos) {
        Optional<Claim> claim = ClaimManager.get(player.serverLevel()).getClaimByCore(corePos);
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        if (!claim.get().isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("This claim belongs to someone else."), true);
            return Optional.empty();
        }
        return claim;
    }

    private static void sendMenu(ServerPlayer player, Claim claim) {
        ClaimGuardNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenClaimMenuPacket(claim.getCorePos(), claim.getTier().ordinal(), claim.displayName())
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
