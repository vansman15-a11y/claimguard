package net.robmc.claimguard.bind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where each player is "bound" to respawn (a Claim Core they chose as their
 * bindstone). Server-wide, persisted. Also holds the transient per-death choice
 * of bind-vs-bed for players who have both.
 */
public class BindManager extends SavedData {

    private static final String DATA_NAME = "claimguard_binds";

    public record Bind(BlockPos pos, ResourceKey<Level> dimension) {
    }

    private final Map<UUID, Bind> binds = new HashMap<>();
    /** player id -> true = respawn at bindstone, false = respawn at bed. Cleared once used. */
    private final Map<UUID, Boolean> pendingRespawnChoice = new HashMap<>();

    public static BindManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BindManager::load, BindManager::new, DATA_NAME);
    }

    public Optional<Bind> getBind(UUID playerId) {
        return Optional.ofNullable(binds.get(playerId));
    }

    public boolean isBoundTo(UUID playerId, BlockPos corePos) {
        Bind bind = binds.get(playerId);
        return bind != null && bind.pos().equals(corePos);
    }

    public void setBind(UUID playerId, BlockPos pos, ResourceKey<Level> dimension) {
        binds.put(playerId, new Bind(pos.immutable(), dimension));
        setDirty();
    }

    public void clearBind(UUID playerId) {
        if (binds.remove(playerId) != null) {
            setDirty();
        }
    }

    /** Drop every player's bind that points at this core (used when a beacon is destroyed). */
    public void clearBindsTo(BlockPos corePos) {
        if (binds.values().removeIf(b -> b.pos().equals(corePos))) {
            setDirty();
        }
    }

    public void setRespawnChoice(UUID playerId, boolean atBind) {
        pendingRespawnChoice.put(playerId, atBind);
    }

    /** Returns the choice (true=bind, false=bed) and clears it, or null if none pending. */
    public Boolean takeRespawnChoice(UUID playerId) {
        return pendingRespawnChoice.remove(playerId);
    }

    // --- NBT ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Bind> entry : binds.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Player", entry.getKey());
            e.putLong("Pos", entry.getValue().pos().asLong());
            e.putString("Dim", entry.getValue().dimension().location().toString());
            list.add(e);
        }
        tag.put("Binds", list);
        return tag;
    }

    public static BindManager load(CompoundTag tag) {
        BindManager manager = new BindManager();
        ListTag list = tag.getList("Binds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(e.getString("Dim")));
            manager.binds.put(e.getUUID("Player"), new Bind(BlockPos.of(e.getLong("Pos")), dim));
        }
        return manager;
    }
}
