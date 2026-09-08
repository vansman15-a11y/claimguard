package net.robmc.claimguard.clan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The whole server's clan data: every clan, a player -> clan index for fast
 * lookups, and the per-player "block" list that stops someone from sending you
 * charter-signature requests.
 *
 * Server-wide, so it lives on the overworld's data storage (dimensions share it)
 * rather than one-per-dimension like ClaimManager.
 */
public class ClanManager extends SavedData {

    private static final String DATA_NAME = "claimguard_clans";

    private final Map<UUID, Clan> clansById = new HashMap<>();
    /** Rebuilt from clan membership on load; kept in sync on every add/remove. */
    private final Map<UUID, UUID> playerToClan = new HashMap<>();
    /** target player -> requesters they've blocked from sending signature requests. */
    private final Map<UUID, Set<UUID>> signatureBlocks = new HashMap<>();

    public static ClanManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ClanManager::load, ClanManager::new, DATA_NAME
        );
    }

    // --- clans ---

    public Clan createClan(String name, String tag, String motd, UUID leaderId, String leaderName) {
        Clan clan = new Clan(UUID.randomUUID(), name, tag, motd);
        clan.addMember(leaderId, leaderName, ClanRank.LEADER);
        clansById.put(clan.getId(), clan);
        playerToClan.put(leaderId, clan.getId());
        setDirty();
        return clan;
    }

    public void addMember(Clan clan, UUID playerId, String name, ClanRank rank) {
        clan.addMember(playerId, name, rank);
        playerToClan.put(playerId, clan.getId());
        setDirty();
    }

    public void removeMember(Clan clan, UUID playerId) {
        clan.removeMember(playerId);
        playerToClan.remove(playerId);
        if (clan.getMembers().isEmpty()) {
            clansById.remove(clan.getId());
        }
        setDirty();
    }

    public void disband(UUID clanId) {
        Clan clan = clansById.remove(clanId);
        if (clan != null) {
            for (ClanMember member : clan.getMembers()) {
                playerToClan.remove(member.getId());
            }
            setDirty();
        }
    }

    public Optional<Clan> getClanOf(UUID playerId) {
        UUID clanId = playerToClan.get(playerId);
        return clanId == null ? Optional.empty() : Optional.ofNullable(clansById.get(clanId));
    }

    public Optional<Clan> getClanById(UUID clanId) {
        return Optional.ofNullable(clansById.get(clanId));
    }

    /**
     * Can this player edit blocks inside a claim owned by the given clan?
     * True only for a current member whose build access hasn't been revoked.
     */
    public boolean canBuildInClanClaim(UUID clanId, UUID playerId) {
        Clan clan = clansById.get(clanId);
        return clan != null && clan.getMember(playerId).map(ClanMember::canBuild).orElse(false);
    }

    /** Clan name for an id, or null if the id is null / unknown. */
    public String clanNameOrNull(UUID clanId) {
        if (clanId == null) {
            return null;
        }
        Clan clan = clansById.get(clanId);
        return clan == null ? null : clan.getName();
    }

    public Optional<Clan> getClanByName(String name) {
        return clansById.values().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<Clan> getAllClans() {
        return new ArrayList<>(clansById.values());
    }

    public void markDirty() {
        setDirty();
    }

    // --- signature block list ---

    public boolean isSignatureBlocked(UUID target, UUID requester) {
        Set<UUID> blocked = signatureBlocks.get(target);
        return blocked != null && blocked.contains(requester);
    }

    public void blockSignatureRequests(UUID target, UUID requester) {
        signatureBlocks.computeIfAbsent(target, k -> new HashSet<>()).add(requester);
        setDirty();
    }

    // --- NBT ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag clanList = new ListTag();
        for (Clan clan : clansById.values()) {
            clanList.add(clan.save());
        }
        tag.put("Clans", clanList);

        ListTag blockList = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> entry : signatureBlocks.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Target", entry.getKey());
            ListTag requesters = new ListTag();
            for (UUID requester : entry.getValue()) {
                CompoundTag r = new CompoundTag();
                r.putUUID("Id", requester);
                requesters.add(r);
            }
            e.put("Requesters", requesters);
            blockList.add(e);
        }
        tag.put("SignatureBlocks", blockList);
        return tag;
    }

    public static ClanManager load(CompoundTag tag) {
        ClanManager manager = new ClanManager();

        ListTag clanList = tag.getList("Clans", Tag.TAG_COMPOUND);
        for (int i = 0; i < clanList.size(); i++) {
            Clan clan = Clan.load(clanList.getCompound(i));
            manager.clansById.put(clan.getId(), clan);
            for (ClanMember member : clan.getMembers()) {
                manager.playerToClan.put(member.getId(), clan.getId());
            }
        }

        ListTag blockList = tag.getList("SignatureBlocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag e = blockList.getCompound(i);
            Set<UUID> requesters = new HashSet<>();
            ListTag rl = e.getList("Requesters", Tag.TAG_COMPOUND);
            for (int j = 0; j < rl.size(); j++) {
                requesters.add(rl.getCompound(j).getUUID("Id"));
            }
            manager.signatureBlocks.put(e.getUUID("Target"), requesters);
        }
        return manager;
    }
}
