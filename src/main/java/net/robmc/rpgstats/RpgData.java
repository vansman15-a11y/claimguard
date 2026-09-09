package net.robmc.rpgstats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every player's {@link PlayerStats}, keyed by UUID. Server-wide, persisted. */
public class RpgData extends SavedData {

    private static final String DATA_NAME = "rpgstats";

    private final Map<UUID, PlayerStats> byPlayer = new HashMap<>();

    public static RpgData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RpgData::load, RpgData::new, DATA_NAME);
    }

    public PlayerStats getOrCreate(UUID playerId) {
        return byPlayer.computeIfAbsent(playerId, k -> new PlayerStats());
    }

    public void markDirty() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerStats> entry : byPlayer.entrySet()) {
            CompoundTag e = entry.getValue().save();
            e.putUUID("Player", entry.getKey());
            list.add(e);
        }
        tag.put("Players", list);
        return tag;
    }

    public static RpgData load(CompoundTag tag) {
        RpgData data = new RpgData();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            data.byPlayer.put(e.getUUID("Player"), PlayerStats.load(e));
        }
        return data;
    }
}
