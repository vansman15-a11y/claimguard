package net.robmc.claimguard.clan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One clan: its identity (name/tag), its message of the day, its members with
 * ranks, and the set of players it has banned.
 *
 * Server-wide data - see ClanManager, which owns the collection of these and the
 * player -> clan index.
 */
public class Clan {

    public static final int MAX_MEMBERS = 8;
    public static final int MAX_BINDSTONES = 3;
    /** Signatures needed on a charter to found a clan. TESTING VALUE - real value is 3. */
    public static final int REQUIRED_SIGNATURES = 1;

    private final UUID id;
    private String name;
    private String tag;
    private String motd;
    /** Minute-of-day (0-1439, server local time) the 3h raid window opens, or -1 if unset. */
    private int raidWindowStart = -1;

    private final List<ClanMember> members = new ArrayList<>();
    /** Banned player id -> last known name, so the ban list can show something readable. */
    private final Map<UUID, String> banned = new LinkedHashMap<>();
    /** How this clan regards other clans (other clan id -> relation). No entry = neutral. */
    private final Map<UUID, ClanRelation> relations = new LinkedHashMap<>();

    public Clan(UUID id, String name, String tag, String motd) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.motd = motd;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    /** Length of the daily raid window, in minutes. */
    public static final int RAID_WINDOW_MINUTES = 180;

    /** Minute-of-day the raid window opens (server local time), or -1 if never set. */
    public int getRaidWindowStart() {
        return raidWindowStart;
    }

    public void setRaidWindowStart(int minuteOfDay) {
        this.raidWindowStart = minuteOfDay;
    }

    /** True if the current server local time is inside this clan's 3h raid window. */
    public boolean isRaidWindowOpenNow() {
        if (raidWindowStart < 0) {
            return false;
        }
        java.time.LocalTime now = java.time.LocalTime.now();
        int nowMin = now.getHour() * 60 + now.getMinute();
        int end = (raidWindowStart + RAID_WINDOW_MINUTES) % 1440;
        if (raidWindowStart < end) {
            return nowMin >= raidWindowStart && nowMin < end;
        }
        return nowMin >= raidWindowStart || nowMin < end; // window wraps past midnight
    }

    /** e.g. "19:00-22:00", or "not set". */
    public String formatRaidWindow() {
        if (raidWindowStart < 0) {
            return "not set";
        }
        int end = (raidWindowStart + RAID_WINDOW_MINUTES) % 1440;
        return String.format("%02d:%02d-%02d:%02d",
                raidWindowStart / 60, raidWindowStart % 60, end / 60, end % 60);
    }

    public List<ClanMember> getMembers() {
        return members;
    }

    public Optional<ClanMember> getMember(UUID playerId) {
        return members.stream().filter(m -> m.getId().equals(playerId)).findFirst();
    }

    public boolean isMember(UUID playerId) {
        return getMember(playerId).isPresent();
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    public Optional<ClanMember> getLeader() {
        return members.stream().filter(m -> m.getRank() == ClanRank.LEADER).findFirst();
    }

    public void addMember(UUID playerId, String name, ClanRank rank) {
        if (!isMember(playerId)) {
            members.add(new ClanMember(playerId, name, rank));
        }
    }

    public void removeMember(UUID playerId) {
        members.removeIf(m -> m.getId().equals(playerId));
    }

    public Map<UUID, ClanRelation> getRelations() {
        return relations;
    }

    public ClanRelation getRelation(UUID otherClanId) {
        return relations.get(otherClanId);
    }

    public void setRelation(UUID otherClanId, ClanRelation relation) {
        if (relation == null) {
            relations.remove(otherClanId);
        } else {
            relations.put(otherClanId, relation);
        }
    }

    public Map<UUID, String> getBanned() {
        return banned;
    }

    public boolean isBanned(UUID playerId) {
        return banned.containsKey(playerId);
    }

    public void ban(UUID playerId, String name) {
        banned.put(playerId, name);
    }

    public void unban(UUID playerId) {
        banned.remove(playerId);
    }

    // --- NBT ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Tag", this.tag);
        tag.putString("Motd", motd);
        tag.putInt("RaidWindowStart", raidWindowStart);

        ListTag memberList = new ListTag();
        for (ClanMember member : members) {
            memberList.add(member.save());
        }
        tag.put("Members", memberList);

        ListTag banList = new ListTag();
        for (Map.Entry<UUID, String> entry : banned.entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putUUID("Id", entry.getKey());
            b.putString("Name", entry.getValue());
            banList.add(b);
        }
        tag.put("Banned", banList);

        ListTag relList = new ListTag();
        for (Map.Entry<UUID, ClanRelation> entry : relations.entrySet()) {
            CompoundTag r = new CompoundTag();
            r.putUUID("Clan", entry.getKey());
            r.putInt("Relation", entry.getValue().ordinal());
            relList.add(r);
        }
        tag.put("Relations", relList);
        return tag;
    }

    public static Clan load(CompoundTag tag) {
        Clan clan = new Clan(
                tag.getUUID("Id"),
                tag.getString("Name"),
                tag.getString("Tag"),
                tag.getString("Motd")
        );
        clan.raidWindowStart = tag.contains("RaidWindowStart") ? tag.getInt("RaidWindowStart") : -1;
        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            clan.members.add(ClanMember.load(memberList.getCompound(i)));
        }
        ListTag banList = tag.getList("Banned", Tag.TAG_COMPOUND);
        for (int i = 0; i < banList.size(); i++) {
            CompoundTag b = banList.getCompound(i);
            clan.banned.put(b.getUUID("Id"), b.getString("Name"));
        }
        ListTag relList = tag.getList("Relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < relList.size(); i++) {
            CompoundTag r = relList.getCompound(i);
            ClanRelation rel = ClanRelation.byIndexOrNull(r.getInt("Relation"));
            if (rel != null) {
                clan.relations.put(r.getUUID("Clan"), rel);
            }
        }
        return clan;
    }
}
