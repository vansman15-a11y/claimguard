package com.villagehousing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All house claims for the server, in one SavedData on the overworld (not
 * per-dimension - villages only physically exist in whichever dimension a
 * claim's {@code dimension} field says, but the claim *list itself* lives in
 * exactly one place so a player logging in anywhere still gets their pending
 * payout and every claim, regardless of which level they're standing in).
 */
public class HouseManager extends SavedData {
    private static final String DATA_NAME = "villagehousing_claims";
    private final List<HouseClaim> claims = new ArrayList<>();
    /** Diamonds owed to a seller who was offline when their house sold to another player. */
    private final Map<UUID, Integer> pendingPayouts = new HashMap<>();

    public static HouseManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HouseManager::load, HouseManager::new, DATA_NAME);
    }

    public static HouseManager load(CompoundTag tag) {
        HouseManager m = new HouseManager();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            m.claims.add(HouseClaim.load(list.getCompound(i)));
        }
        ListTag pending = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag p = pending.getCompound(i);
            m.pendingPayouts.put(p.getUUID("player"), p.getInt("amount"));
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (HouseClaim c : claims) list.add(c.save());
        tag.put("claims", list);

        ListTag pending = new ListTag();
        for (Map.Entry<UUID, Integer> e : pendingPayouts.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("player", e.getKey());
            p.putInt("amount", e.getValue());
            pending.add(p);
        }
        tag.put("pending", pending);
        return tag;
    }

    public void add(HouseClaim claim) {
        claims.add(claim);
        setDirty();
    }

    public List<HouseClaim> all() {
        return claims;
    }

    public HouseClaim at(ResourceLocation dim, BlockPos pos) {
        for (HouseClaim c : claims) {
            if (c.dimension.equals(dim) && c.contains(pos)) return c;
        }
        return null;
    }

    public HouseClaim bySign(ResourceLocation dim, BlockPos pos) {
        for (HouseClaim c : claims) {
            if (c.dimension.equals(dim) && c.signPos != null && c.signPos.equals(pos)) return c;
        }
        return null;
    }

    public HouseClaim byOwner(UUID owner) {
        for (HouseClaim c : claims) {
            if (c.owner != null && c.owner.equals(owner)) return c;
        }
        return null;
    }

    public HouseClaim byResident(UUID player) {
        for (HouseClaim c : claims) {
            if (c.isOwner(player) || c.isGuest(player)) return c;
        }
        return null;
    }

    public boolean livesSomewhere(UUID player) {
        return byResident(player) != null;
    }

    /** Queue diamonds for a seller who was offline at sale time - paid out on their next login. */
    public void addPendingPayout(UUID player, int amount) {
        if (amount <= 0) return;
        pendingPayouts.merge(player, amount, Integer::sum);
        setDirty();
    }

    /** Claims (and clears) whatever is owed to this player. 0 if nothing's pending. */
    public int takePendingPayout(UUID player) {
        Integer amount = pendingPayouts.remove(player);
        if (amount != null) setDirty();
        return amount == null ? 0 : amount;
    }
}
