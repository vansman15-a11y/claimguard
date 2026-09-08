package net.robmc.claimguard.clan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.item.ClanCharterItem;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanMemberActionPacket;
import net.robmc.claimguard.network.OpenClanRosterPacket;
import net.robmc.claimguard.network.OpenCreateClanScreenPacket;
import net.robmc.claimguard.registry.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side clan logic: using a charter, gathering signatures, and founding a
 * clan. (Roster / rank actions land in a later phase.)
 */
public final class ClanActions {

    /** target player id -> the requester whose signature request is waiting for a response. */
    private static final Map<UUID, UUID> pendingSignatureRequests = new HashMap<>();

    private ClanActions() {
    }

    // --- charter ---

    public static void useCharter(ServerPlayer player, ItemStack charter) {
        if (ClanManager.get(player.server).getClanOf(player.getUUID()).isPresent()) {
            player.displayClientMessage(Component.literal("You're already in a clan."), true);
            return;
        }

        UUID owner = ClanCharterItem.getOwner(charter);
        if (owner == null) {
            ClanCharterItem.setOwner(charter, player.getUUID(), player.getGameProfile().getName());
            ClanCharterItem.addSignature(charter, player.getUUID(), player.getGameProfile().getName());
            player.displayClientMessage(Component.literal(
                    "Charter started. You need " + Clan.REQUIRED_SIGNATURES + " signature(s) total"
                            + " - use /signature <player> to gather more."), false);
            return;
        }

        if (!owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal(
                    "This charter belongs to " + ClanCharterItem.getOwnerName(charter) + "."), true);
            return;
        }

        if (ClanCharterItem.isReady(charter)) {
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenCreateClanScreenPacket());
        } else {
            player.displayClientMessage(Component.literal(
                    "Charter has " + ClanCharterItem.signatureCount(charter) + " / "
                            + Clan.REQUIRED_SIGNATURES + " signatures. Use /signature <player>."), true);
        }
    }

    // --- signatures ---

    public static void requestSignature(ServerPlayer requester, ServerPlayer target) {
        if (target.getUUID().equals(requester.getUUID())) {
            requester.displayClientMessage(Component.literal("You can't request your own signature."), true);
            return;
        }

        Optional<ItemStack> charter = findOwnedCharter(requester);
        if (charter.isEmpty()) {
            requester.displayClientMessage(Component.literal("You aren't carrying a charter you own. Right-click a Clan Charter first."), true);
            return;
        }
        if (ClanCharterItem.hasSigned(charter.get(), target.getUUID())) {
            requester.displayClientMessage(Component.literal(target.getGameProfile().getName() + " has already signed."), true);
            return;
        }
        if (ClanManager.get(requester.server).isSignatureBlocked(target.getUUID(), requester.getUUID())) {
            // Don't reveal the block - just say it failed.
            requester.displayClientMessage(Component.literal("Couldn't send a signature request to that player."), true);
            return;
        }

        pendingSignatureRequests.put(target.getUUID(), requester.getUUID());
        requester.displayClientMessage(Component.literal("Signature request sent to " + target.getGameProfile().getName() + "."), false);
        target.sendSystemMessage(Component.literal(
                requester.getGameProfile().getName() + " asks you to sign their clan charter. ")
                .append(Component.literal("/accept").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(Component.literal("/deny").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  "))
                .append(Component.literal("/block").withStyle(ChatFormatting.RED)));
    }

    public static void acceptSignature(ServerPlayer target) {
        UUID requesterId = pendingSignatureRequests.remove(target.getUUID());
        if (requesterId == null) {
            target.displayClientMessage(Component.literal("You have no pending signature request."), true);
            return;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(requesterId);
        if (requester == null) {
            target.displayClientMessage(Component.literal("That player is no longer online."), true);
            return;
        }
        Optional<ItemStack> charter = findOwnedCharter(requester);
        if (charter.isEmpty()) {
            target.displayClientMessage(Component.literal("They aren't carrying their charter anymore."), true);
            return;
        }

        ClanCharterItem.addSignature(charter.get(), target.getUUID(), target.getGameProfile().getName());
        int count = ClanCharterItem.signatureCount(charter.get());
        target.displayClientMessage(Component.literal("You signed " + requester.getGameProfile().getName() + "'s charter."), false);
        requester.displayClientMessage(Component.literal(
                target.getGameProfile().getName() + " signed your charter (" + count + " / " + Clan.REQUIRED_SIGNATURES + ")."
                        + (ClanCharterItem.isReady(charter.get()) ? " Right-click it to found your clan." : "")), false);
    }

    public static void denySignature(ServerPlayer target) {
        UUID requesterId = pendingSignatureRequests.remove(target.getUUID());
        if (requesterId == null) {
            target.displayClientMessage(Component.literal("You have no pending signature request."), true);
            return;
        }
        target.displayClientMessage(Component.literal("Signature request denied."), false);
        ServerPlayer requester = target.server.getPlayerList().getPlayer(requesterId);
        if (requester != null) {
            requester.displayClientMessage(Component.literal(target.getGameProfile().getName() + " denied your signature request."), false);
        }
    }

    public static void blockSignature(ServerPlayer target) {
        UUID requesterId = pendingSignatureRequests.remove(target.getUUID());
        if (requesterId == null) {
            target.displayClientMessage(Component.literal("You have no pending signature request to block."), true);
            return;
        }
        ClanManager.get(target.server).blockSignatureRequests(target.getUUID(), requesterId);
        target.displayClientMessage(Component.literal("Blocked. That player can't send you signature requests again."), false);
    }

    // --- clan creation ---

    public static void createClan(ServerPlayer player, String name, String tag, String motd) {
        ClanManager manager = ClanManager.get(player.server);
        if (manager.getClanOf(player.getUUID()).isPresent()) {
            player.displayClientMessage(Component.literal("You're already in a clan."), true);
            return;
        }

        Optional<ItemStack> maybeCharter = findOwnedCharter(player);
        if (maybeCharter.isEmpty() || !ClanCharterItem.isReady(maybeCharter.get())) {
            player.displayClientMessage(Component.literal("You need a fully-signed charter to found a clan."), true);
            return;
        }

        String cleanName = name.trim();
        String cleanTag = tag.trim();
        if (cleanName.length() < 3 || cleanName.length() > 24) {
            player.displayClientMessage(Component.literal("Clan name must be 3-24 characters."), true);
            return;
        }
        if (cleanTag.length() < 2 || cleanTag.length() > 5) {
            player.displayClientMessage(Component.literal("Clan tag must be 2-5 characters."), true);
            return;
        }
        if (manager.getClanByName(cleanName).isPresent()) {
            player.displayClientMessage(Component.literal("A clan called \"" + cleanName + "\" already exists."), true);
            return;
        }

        ItemStack charter = maybeCharter.get();
        Clan clan = manager.createClan(cleanName, cleanTag, motd.trim(), player.getUUID(), player.getGameProfile().getName());

        // Every other signer becomes a founding Member.
        for (Map.Entry<UUID, String> signer : ClanCharterItem.getSignatures(charter).entrySet()) {
            if (!signer.getKey().equals(player.getUUID()) && !clan.isFull()) {
                manager.addMember(clan, signer.getKey(), signer.getValue(), ClanRank.MEMBER);
            }
        }

        charter.shrink(1);
        player.displayClientMessage(Component.literal(
                "Clan \"" + clan.getName() + "\" [" + clan.getTag() + "] founded with "
                        + clan.getMembers().size() + " member(s)."), false);
    }

    // --- roster & member management ---

    public static void openRoster(ServerPlayer player) {
        Optional<Clan> maybeClan = ClanManager.get(player.server).getClanOf(player.getUUID());
        if (maybeClan.isEmpty()) {
            player.displayClientMessage(Component.literal("You're not in a clan. Craft a Clan Charter to start one."), true);
            return;
        }
        sendRoster(player, maybeClan.get());
    }

    public static void memberAction(ServerPlayer actor, UUID targetId, ClanMemberActionPacket.Action action) {
        ClanManager manager = ClanManager.get(actor.server);
        Optional<Clan> maybeClan = manager.getClanOf(actor.getUUID());
        if (maybeClan.isEmpty()) {
            return;
        }
        Clan clan = maybeClan.get();

        Optional<ClanMember> maybeActorMember = clan.getMember(actor.getUUID());
        Optional<ClanMember> maybeTarget = clan.getMember(targetId);
        if (maybeActorMember.isEmpty() || maybeTarget.isEmpty()) {
            return;
        }
        ClanMember actorMember = maybeActorMember.get();
        ClanMember target = maybeTarget.get();

        if (target.getId().equals(actor.getUUID())) {
            actor.displayClientMessage(Component.literal("You can't do that to yourself."), true);
            return;
        }

        ClanRank a = actorMember.getRank();
        ClanRank t = target.getRank();

        switch (action) {
            case PROMOTE -> {
                if (!ClanPermissions.canPromote(a, t)) {
                    denied(actor);
                    return;
                }
                target.setRank(t.promoted());
                manager.markDirty();
                notifyRankChange(actor, clan, target, "promoted");
            }
            case DEMOTE -> {
                if (!ClanPermissions.canDemote(a, t)) {
                    denied(actor);
                    return;
                }
                target.setRank(t.demoted());
                manager.markDirty();
                notifyRankChange(actor, clan, target, "demoted");
            }
            case KICK -> {
                if (!ClanPermissions.canKick(a, t)) {
                    denied(actor);
                    return;
                }
                manager.removeMember(clan, targetId);
                messageMember(actor, targetId, "You were removed from " + clan.getName() + ".");
                actor.displayClientMessage(Component.literal("Removed " + target.getName() + " from the clan."), false);
            }
            case BAN -> {
                if (!ClanPermissions.canBan(a, t)) {
                    denied(actor);
                    return;
                }
                clan.ban(targetId, target.getName());
                manager.removeMember(clan, targetId);
                messageMember(actor, targetId, "You were banned from " + clan.getName() + ".");
                actor.displayClientMessage(Component.literal("Banned " + target.getName() + " from the clan."), false);
            }
        }

        // Refresh the roster for everyone in the clan who has it open (cheap: just online members).
        broadcastRosterRefresh(actor, clan);
    }

    private static void notifyRankChange(ServerPlayer actor, Clan clan, ClanMember target, String verb) {
        actor.displayClientMessage(Component.literal(
                verb.substring(0, 1).toUpperCase() + verb.substring(1) + " " + target.getName()
                        + " to " + target.getRank().displayName() + "."), false);
        messageMember(actor, target.getId(), "You were " + verb + " to " + target.getRank().displayName()
                + " in " + clan.getName() + ".");
    }

    private static void denied(ServerPlayer actor) {
        actor.displayClientMessage(Component.literal("You don't have permission to do that."), true);
    }

    private static void messageMember(ServerPlayer actor, UUID memberId, String message) {
        ServerPlayer member = actor.server.getPlayerList().getPlayer(memberId);
        if (member != null) {
            member.displayClientMessage(Component.literal(message), false);
        }
    }

    private static void broadcastRosterRefresh(ServerPlayer actor, Clan clan) {
        for (ClanMember member : clan.getMembers()) {
            ServerPlayer online = actor.server.getPlayerList().getPlayer(member.getId());
            if (online != null) {
                sendRoster(online, clan);
            }
        }
    }

    private static void sendRoster(ServerPlayer viewer, Clan clan) {
        List<OpenClanRosterPacket.MemberRow> rows = new ArrayList<>();
        for (ClanMember member : clan.getMembers()) {
            boolean online = viewer.server.getPlayerList().getPlayer(member.getId()) != null;
            rows.add(new OpenClanRosterPacket.MemberRow(member.getId(), member.getName(), member.getRank().ordinal(), online));
        }
        rows.sort((x, y) -> {
            if (x.rankOrdinal() != y.rankOrdinal()) {
                return Integer.compare(x.rankOrdinal(), y.rankOrdinal());
            }
            return x.name().compareToIgnoreCase(y.name());
        });
        int viewerRank = clan.getMember(viewer.getUUID()).map(m -> m.getRank().ordinal()).orElse(ClanRank.RECRUIT.ordinal());
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new OpenClanRosterPacket(clan.getName(), clan.getTag(), clan.getMotd(), viewerRank, rows));
    }

    // --- helpers ---

    private static Optional<ItemStack> findOwnedCharter(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.CLAN_CHARTER.get())) {
                UUID owner = ClanCharterItem.getOwner(stack);
                if (owner != null && owner.equals(player.getUUID())) {
                    return Optional.of(stack);
                }
            }
        }
        return Optional.empty();
    }
}
