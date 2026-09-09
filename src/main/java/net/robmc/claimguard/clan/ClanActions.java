package net.robmc.claimguard.clan;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;
import net.robmc.claimguard.item.ClanCharterItem;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanMemberActionPacket;
import net.robmc.claimguard.network.OpenClanBanListPacket;
import net.robmc.claimguard.network.OpenClanBrowsePacket;
import net.robmc.claimguard.network.OpenClanRosterPacket;
import net.robmc.claimguard.network.OpenCreateClanScreenPacket;
import net.robmc.claimguard.network.SyncAlliesPacket;
import net.robmc.claimguard.network.SyncClanViewPacket;
import net.robmc.claimguard.registry.ModItems;
import net.robmc.claimguard.siege.SiegeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side clan logic: using a charter, gathering signatures, and founding a
 * clan. (Roster / rank actions land in a later phase.)
 */
public final class ClanActions {

    /** target player id -> the requester whose signature request is waiting for a response. */
    private static final Map<UUID, UUID> pendingSignatureRequests = new HashMap<>();
    /** invited player id -> the clan id they've been invited to join. */
    private static final Map<UUID, UUID> pendingClanInvites = new HashMap<>();

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

        String cleanName = sanitize(name, 24);
        String cleanTag = sanitize(tag, 5);
        if (cleanName.length() < 3 || cleanName.length() > 24 || !cleanName.matches("[A-Za-z0-9 '_-]+")) {
            player.displayClientMessage(Component.literal("Clan name must be 3-24 letters, digits, spaces or - _ '."), true);
            return;
        }
        if (cleanTag.length() < 2 || cleanTag.length() > 5 || !cleanTag.matches("[A-Za-z0-9]+")) {
            player.displayClientMessage(Component.literal("Clan tag must be 2-5 letters or digits."), true);
            return;
        }
        if (manager.getClanByName(cleanName).isPresent()) {
            player.displayClientMessage(Component.literal("A clan called \"" + cleanName + "\" already exists."), true);
            return;
        }

        ItemStack charter = maybeCharter.get();
        Clan clan = manager.createClan(cleanName, cleanTag, sanitize(motd, 120), player.getUUID(), player.getGameProfile().getName());

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
            case TOGGLE_BUILD -> {
                if (!ClanPermissions.canKick(a, t)) { // same "manages this member" gate as kick
                    denied(actor);
                    return;
                }
                target.setCanBuild(!target.canBuild());
                manager.markDirty();
                String state = target.canBuild() ? "granted" : "revoked";
                actor.displayClientMessage(Component.literal(
                        "Building access " + state + " for " + target.getName() + "."), false);
                messageMember(actor, targetId, "Your building access in " + clan.getName()
                        + "'s claims was " + state + ".");
                return; // leave the actor on the Permissions screen; no roster refresh
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
            rows.add(new OpenClanRosterPacket.MemberRow(
                    member.getId(), member.getName(), member.getRank().ordinal(), online, member.canBuild()));
        }
        rows.sort((x, y) -> {
            if (x.rankOrdinal() != y.rankOrdinal()) {
                return Integer.compare(x.rankOrdinal(), y.rankOrdinal());
            }
            return x.name().compareToIgnoreCase(y.name());
        });
        int viewerRank = clan.getMember(viewer.getUUID()).map(m -> m.getRank().ordinal()).orElse(ClanRank.RECRUIT.ordinal());
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new OpenClanRosterPacket(clan.getName(), clan.getTag(), clan.getMotd(), clan.formatRaidWindow(), viewerRank, rows));
    }

    // --- leaving ---

    public static void leaveClan(ServerPlayer player) {
        ClanManager manager = ClanManager.get(player.server);
        Optional<Clan> maybeClan = manager.getClanOf(player.getUUID());
        if (maybeClan.isEmpty()) {
            player.displayClientMessage(Component.literal("You're not in a clan."), true);
            return;
        }
        Clan clan = maybeClan.get();
        boolean wasLeader = clan.getMember(player.getUUID()).map(m -> m.getRank() == ClanRank.LEADER).orElse(false);
        java.util.UUID clanId = clan.getId();

        manager.removeMember(clan, player.getUUID()); // disbands the clan if that emptied it

        if (clan.getMembers().isEmpty()) {
            destroyClanBeacons(player.server, clanId);
            player.displayClientMessage(Component.literal("You left " + clan.getName() + ". The clan is disbanded and its beacons removed."), false);
            return;
        }

        // Leader left but others remain - hand leadership to the most senior member.
        if (wasLeader) {
            ClanMember heir = clan.getMembers().stream()
                    .min((a, b) -> Integer.compare(a.getRank().ordinal(), b.getRank().ordinal()))
                    .orElse(null);
            if (heir != null && heir.getRank() != ClanRank.LEADER) {
                heir.setRank(ClanRank.LEADER);
                manager.markDirty();
                messageMember(player, heir.getId(), "You are now the Leader of " + clan.getName() + ".");
            }
        }

        player.displayClientMessage(Component.literal("You left " + clan.getName() + "."), false);
        broadcastRosterRefresh(player, clan);
    }

    // --- invites ---

    public static void invitePlayer(ServerPlayer inviter, ServerPlayer target) {
        ClanManager manager = ClanManager.get(inviter.server);
        Optional<Clan> maybeClan = manager.getClanOf(inviter.getUUID());
        if (maybeClan.isEmpty()) {
            inviter.displayClientMessage(Component.literal("You're not in a clan."), true);
            return;
        }
        Clan clan = maybeClan.get();
        ClanRank rank = clan.getMember(inviter.getUUID()).map(ClanMember::getRank).orElse(ClanRank.RECRUIT);
        if (!ClanPermissions.canInvite(rank)) {
            denied(inviter);
            return;
        }
        if (target.getUUID().equals(inviter.getUUID())) {
            inviter.displayClientMessage(Component.literal("You're already in the clan."), true);
            return;
        }
        if (clan.isFull()) {
            inviter.displayClientMessage(Component.literal("Your clan is full (" + Clan.MAX_MEMBERS + ")."), true);
            return;
        }
        if (clan.isBanned(target.getUUID())) {
            inviter.displayClientMessage(Component.literal(target.getGameProfile().getName() + " is banned from the clan."), true);
            return;
        }
        if (manager.getClanOf(target.getUUID()).isPresent()) {
            inviter.displayClientMessage(Component.literal(target.getGameProfile().getName() + " is already in a clan."), true);
            return;
        }

        pendingClanInvites.put(target.getUUID(), clan.getId());
        inviter.displayClientMessage(Component.literal("Invited " + target.getGameProfile().getName() + " to " + clan.getName() + "."), false);
        target.sendSystemMessage(Component.literal(
                inviter.getGameProfile().getName() + " invited you to the clan \"" + clan.getName() + "\". ")
                .append(Component.literal("/clan accept").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(Component.literal("/clan decline").withStyle(ChatFormatting.YELLOW)));
    }

    public static void acceptInvite(ServerPlayer target) {
        UUID clanId = pendingClanInvites.remove(target.getUUID());
        if (clanId == null) {
            target.displayClientMessage(Component.literal("You have no pending clan invite."), true);
            return;
        }
        ClanManager manager = ClanManager.get(target.server);
        if (manager.getClanOf(target.getUUID()).isPresent()) {
            target.displayClientMessage(Component.literal("You're already in a clan."), true);
            return;
        }
        Optional<Clan> maybeClan = manager.getClanById(clanId);
        if (maybeClan.isEmpty()) {
            target.displayClientMessage(Component.literal("That clan no longer exists."), true);
            return;
        }
        Clan clan = maybeClan.get();
        if (clan.isFull()) {
            target.displayClientMessage(Component.literal("That clan is now full."), true);
            return;
        }
        manager.addMember(clan, target.getUUID(), target.getGameProfile().getName(), ClanRank.RECRUIT);
        target.displayClientMessage(Component.literal("You joined " + clan.getName() + " as a Recruit."), false);
        broadcastRosterRefresh(target, clan);
    }

    public static void declineInvite(ServerPlayer target) {
        if (pendingClanInvites.remove(target.getUUID()) == null) {
            target.displayClientMessage(Component.literal("You have no pending clan invite."), true);
            return;
        }
        target.displayClientMessage(Component.literal("Invite declined."), false);
    }

    // --- browse & relations ---

    public static void openBrowse(ServerPlayer player) {
        ClanManager clans = ClanManager.get(player.server);
        Optional<Clan> myClan = clans.getClanOf(player.getUUID());
        if (myClan.isEmpty()) {
            player.displayClientMessage(Component.literal("You're not in a clan."), true);
            return;
        }
        SiegeManager sieges = SiegeManager.get(player.server);
        Set<UUID> defendingClans = new HashSet<>();
        sieges.activeSieges().forEach(s -> defendingClans.add(s.defenderClan));

        List<OpenClanBrowsePacket.ClanRow> rows = new ArrayList<>();
        for (Clan c : clans.getAllClans()) {
            if (c.getId().equals(myClan.get().getId())) {
                continue;
            }
            ClanRelation rel = myClan.get().getRelation(c.getId());
            rows.add(new OpenClanBrowsePacket.ClanRow(
                    c.getId(), c.getName(), c.getTag(), c.getMembers().size(),
                    c.formatRaidWindow(), c.isRaidWindowOpenNow(),
                    rel == null ? -1 : rel.ordinal(), defendingClans.contains(c.getId())));
        }
        rows.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        int viewerRank = myClan.get().getMember(player.getUUID()).map(m -> m.getRank().ordinal()).orElse(ClanRank.RECRUIT.ordinal());
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenClanBrowsePacket(viewerRank, rows));
    }

    public static void setRelation(ServerPlayer player, UUID targetClanId, ClanRelation relation) {
        ClanManager manager = ClanManager.get(player.server);
        Optional<Clan> myClan = manager.getClanOf(player.getUUID());
        if (myClan.isEmpty() || myClan.get().getId().equals(targetClanId)) {
            return;
        }
        ClanRank rank = myClan.get().getMember(player.getUUID()).map(ClanMember::getRank).orElse(ClanRank.RECRUIT);
        if (rank != ClanRank.LEADER && rank != ClanRank.OFFICER) {
            denied(player);
            return;
        }
        if (manager.getClanById(targetClanId).isEmpty()) {
            return;
        }
        myClan.get().setRelation(targetClanId, relation);
        manager.markDirty();
        String name = manager.clanNameOrNull(targetClanId);
        player.displayClientMessage(Component.literal(relation == null
                ? "Cleared relation with " + name + "."
                : "Set " + name + " as " + relation.displayName() + "."), false);
        // refresh browse + ally tags for every online member of our clan
        for (ClanMember member : myClan.get().getMembers()) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(member.getId());
            if (online != null) {
                openBrowse(online);
                syncAllies(online);
            }
        }
        syncClanViewToAll(player.server); // relation changed - refresh target-frame colours everywhere
    }

    public static void setRaidWindow(ServerPlayer player, String hhmm) {
        ClanManager manager = ClanManager.get(player.server);
        Optional<Clan> myClan = manager.getClanOf(player.getUUID());
        if (myClan.isEmpty()) {
            player.displayClientMessage(Component.literal("You're not in a clan."), true);
            return;
        }
        if (myClan.get().getMember(player.getUUID()).map(ClanMember::getRank).orElse(ClanRank.RECRUIT) != ClanRank.LEADER) {
            player.displayClientMessage(Component.literal("Only the clan Leader can set the raid window."), true);
            return;
        }
        int minutes;
        try {
            String[] parts = hhmm.trim().split(":");
            int h = Integer.parseInt(parts[0]);
            int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                throw new NumberFormatException();
            }
            minutes = h * 60 + m;
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.literal("Use 24-hour time, e.g. /clan raidwindow 19:00"), true);
            return;
        }
        myClan.get().setRaidWindowStart(minutes);
        manager.markDirty();
        player.displayClientMessage(Component.literal(
                "Raid window set to " + myClan.get().formatRaidWindow() + " (server time). "
                        + "Your claims can only be sieged during this 3-hour window."), false);
    }

    /**
     * Send {@code viewer} a row for every online player - name, clan tag, and how
     * {@code viewer}'s clan regards them. Feeds the client target frame.
     */
    public static void syncClanView(ServerPlayer viewer) {
        ClanManager clans = ClanManager.get(viewer.server);
        Clan myClan = clans.getClanOf(viewer.getUUID()).orElse(null);

        List<SyncClanViewPacket.Row> rows = new ArrayList<>();
        for (ServerPlayer other : viewer.server.getPlayerList().getPlayers()) {
            Clan theirClan = clans.getClanOf(other.getUUID()).orElse(null);
            String tag = theirClan == null ? "" : theirClan.getTag();
            byte relation = SyncClanViewPacket.ENEMY;
            if (myClan != null && theirClan != null) {
                if (myClan.getId().equals(theirClan.getId())) {
                    relation = SyncClanViewPacket.SELF;
                } else if (myClan.getRelation(theirClan.getId()) == ClanRelation.ALLY) {
                    relation = SyncClanViewPacket.ALLY;
                }
            }
            rows.add(new SyncClanViewPacket.Row(other.getUUID(), other.getGameProfile().getName(), tag, relation));
        }
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new SyncClanViewPacket(rows));
    }

    /** Refresh the clan view for every online player (call after any clan / relation change). */
    public static void syncClanViewToAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncClanView(player);
        }
    }

    public static void syncAllies(ServerPlayer player) {
        ClanManager clans = ClanManager.get(player.server);
        Set<UUID> allied = new HashSet<>();
        clans.getClanOf(player.getUUID()).ifPresent(myClan ->
                myClan.getRelations().forEach((clanId, rel) -> {
                    if (rel == ClanRelation.ALLY) {
                        clans.getClanById(clanId).ifPresent(ally ->
                                ally.getMembers().forEach(m -> allied.add(m.getId())));
                    }
                }));
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncAlliesPacket(new ArrayList<>(allied)));
    }

    /** True if 'playerId' is in a clan that 'ownerClanId' considers an ally. */
    public static boolean isAllyOf(net.minecraft.server.MinecraftServer server, UUID ownerClanId, UUID playerId) {
        ClanManager clans = ClanManager.get(server);
        Clan owner = clans.getClanById(ownerClanId).orElse(null);
        if (owner == null) {
            return false;
        }
        UUID theirClan = clans.getClanOf(playerId).map(Clan::getId).orElse(null);
        return theirClan != null && owner.getRelation(theirClan) == ClanRelation.ALLY;
    }

    // --- ban list & motd ---

    public static void openBanList(ServerPlayer player) {
        Optional<Clan> maybeClan = ClanManager.get(player.server).getClanOf(player.getUUID());
        if (maybeClan.isEmpty()) {
            player.displayClientMessage(Component.literal("You're not in a clan."), true);
            return;
        }
        Clan clan = maybeClan.get();
        boolean canUnban = clan.getMember(player.getUUID()).map(m -> m.getRank() == ClanRank.LEADER).orElse(false);
        List<OpenClanBanListPacket.BanRow> rows = new ArrayList<>();
        clan.getBanned().forEach((id, name) -> rows.add(new OpenClanBanListPacket.BanRow(id, name)));
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenClanBanListPacket(clan.getName(), canUnban, rows));
    }

    public static void unban(ServerPlayer player, UUID targetId) {
        ClanManager manager = ClanManager.get(player.server);
        Optional<Clan> maybeClan = manager.getClanOf(player.getUUID());
        if (maybeClan.isEmpty()) {
            return;
        }
        Clan clan = maybeClan.get();
        if (!clan.getMember(player.getUUID()).map(m -> m.getRank() == ClanRank.LEADER).orElse(false)) {
            denied(player);
            return;
        }
        clan.unban(targetId);
        manager.markDirty();
        player.displayClientMessage(Component.literal("Unbanned."), false);
        openBanList(player);
    }

    public static void setMotd(ServerPlayer player, String motd) {
        ClanManager manager = ClanManager.get(player.server);
        Optional<Clan> maybeClan = manager.getClanOf(player.getUUID());
        if (maybeClan.isEmpty()) {
            return;
        }
        Clan clan = maybeClan.get();
        ClanRank rank = clan.getMember(player.getUUID()).map(ClanMember::getRank).orElse(ClanRank.RECRUIT);
        if (rank != ClanRank.LEADER && rank != ClanRank.OFFICER) {
            denied(player);
            return;
        }
        clan.setMotd(sanitize(motd, 120));
        manager.markDirty();
        player.displayClientMessage(Component.literal("Clan MOTD updated."), false);
        broadcastRosterRefresh(player, clan);
    }

    /**
     * Removes every Claim Core owned by a (now disbanded) clan, in every dimension:
     * deletes the claim data and breaks the block in the world.
     */
    private static void destroyClanBeacons(net.minecraft.server.MinecraftServer server, java.util.UUID clanId) {
        for (ServerLevel level : server.getAllLevels()) {
            ClaimManager claims = ClaimManager.get(level);
            for (Claim claim : claims.claimsOwnedByClan(clanId)) {
                BlockPos core = claim.getCorePos();
                claims.removeClaim(core);
                level.destroyBlock(core, false);
            }
        }
    }

    // --- helpers ---

    /**
     * Strips formatting/section signs and control characters, collapses whitespace,
     * trims, and hard-caps the length. Applied to every player-supplied clan string
     * so a hacked client can't inject colour codes or newlines into names shown
     * server-wide.
     */
    private static String sanitize(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\u00a7\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

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
