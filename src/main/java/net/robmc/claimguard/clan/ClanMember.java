package net.robmc.claimguard.clan;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * One player's membership in a clan: who they are, the name they last logged in
 * with (so offline members still show a name), and their rank.
 */
public class ClanMember {

    private final UUID id;
    private String name;
    private ClanRank rank;

    public ClanMember(UUID id, String name, ClanRank rank) {
        this.id = id;
        this.name = name;
        this.rank = rank;
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

    public ClanRank getRank() {
        return rank;
    }

    public void setRank(ClanRank rank) {
        this.rank = rank;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putInt("Rank", rank.ordinal());
        return tag;
    }

    public static ClanMember load(CompoundTag tag) {
        return new ClanMember(
                tag.getUUID("Id"),
                tag.getString("Name"),
                ClanRank.byIndex(tag.getInt("Rank"))
        );
    }
}
