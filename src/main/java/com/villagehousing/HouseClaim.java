package com.villagehousing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class HouseClaim {
    public enum Type { BUYABLE, NPC }

    public UUID id = UUID.randomUUID();
    public ResourceLocation dimension;
    public BlockPos min;
    public BlockPos max;
    public BlockPos signPos;
    public BlockPos doorPos;
    public Type type = Type.BUYABLE;
    public UUID owner; // null if unowned
    public UUID guest;
    public int listPrice; // 0 = not listed for sale; diamonds
    public int defaultPrice = 8;
    public int purchasePrice; // what the current owner paid

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public boolean isGuest(UUID uuid) {
        return guest != null && guest.equals(uuid);
    }

    public boolean canEnter(UUID uuid) {
        return isOwner(uuid) || isGuest(uuid);
    }

    public boolean isListed() {
        return owner != null && listPrice > 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("dim", dimension.toString());
        t.putLong("min", min.asLong());
        t.putLong("max", max.asLong());
        if (signPos != null) t.putLong("sign", signPos.asLong());
        if (doorPos != null) t.putLong("door", doorPos.asLong());
        t.putString("type", type.name());
        if (owner != null) t.putUUID("owner", owner);
        if (guest != null) t.putUUID("guest", guest);
        t.putInt("listPrice", listPrice);
        t.putInt("defaultPrice", defaultPrice);
        t.putInt("purchasePrice", purchasePrice);
        return t;
    }

    public static HouseClaim load(CompoundTag t) {
        HouseClaim c = new HouseClaim();
        c.id = t.getUUID("id");
        c.dimension = new ResourceLocation(t.getString("dim"));
        c.min = BlockPos.of(t.getLong("min"));
        c.max = BlockPos.of(t.getLong("max"));
        if (t.contains("sign")) c.signPos = BlockPos.of(t.getLong("sign"));
        if (t.contains("door")) c.doorPos = BlockPos.of(t.getLong("door"));
        try { c.type = Type.valueOf(t.getString("type")); } catch (Exception ignored) {}
        if (t.hasUUID("owner")) c.owner = t.getUUID("owner");
        if (t.hasUUID("guest")) c.guest = t.getUUID("guest");
        c.listPrice = t.getInt("listPrice");
        c.defaultPrice = t.contains("defaultPrice") ? t.getInt("defaultPrice") : 8;
        c.purchasePrice = t.getInt("purchasePrice");
        return c;
    }
}
