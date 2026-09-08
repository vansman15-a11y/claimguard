package net.robmc.claimguard.siege;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every active siege (keyed by the besieged beacon's position) and every
 * post-conquest lockout area. Server-wide, persisted so a siege survives a
 * restart mid-fight.
 */
public class SiegeManager extends SavedData {

    private static final String DATA_NAME = "claimguard_sieges";

    public static final int BEACON_MAX_HITS = 50;
    /** Ticks with no attacker damage before the defenders win by attrition (2 min). */
    public static final long INACTIVITY_TICKS = 2400L;
    /** Ticks nobody may re-claim a conquered area (5 min). */
    public static final long LOCKOUT_TICKS = 6000L;

    public static class Siege {
        public final UUID attackerClan;
        public final UUID defenderClan;
        public final BlockPos core;
        public final ResourceKey<Level> dimension;
        public int hitsRemaining;
        public long lastActivityTick;

        public Siege(UUID attackerClan, UUID defenderClan, BlockPos core, ResourceKey<Level> dimension,
                     int hitsRemaining, long lastActivityTick) {
            this.attackerClan = attackerClan;
            this.defenderClan = defenderClan;
            this.core = core;
            this.dimension = dimension;
            this.hitsRemaining = hitsRemaining;
            this.lastActivityTick = lastActivityTick;
        }
    }

    public record Lockout(BlockPos center, int radius, ResourceKey<Level> dimension, long expiryTick) {
        public boolean covers(BlockPos pos) {
            return Math.abs(pos.getX() - center.getX()) <= radius && Math.abs(pos.getZ() - center.getZ()) <= radius;
        }
    }

    private final Map<BlockPos, Siege> sieges = new HashMap<>();
    private final List<Lockout> lockouts = new ArrayList<>();

    public static SiegeManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SiegeManager::load, SiegeManager::new, DATA_NAME);
    }

    // --- sieges ---

    public Siege getSiege(BlockPos core) {
        return sieges.get(core);
    }

    public boolean isUnderSiege(BlockPos core) {
        return sieges.containsKey(core);
    }

    public void startSiege(Siege siege) {
        sieges.put(siege.core, siege);
        setDirty();
    }

    /** Call after mutating a Siege's fields so the change is saved. */
    public void markDirty() {
        setDirty();
    }

    public void endSiege(BlockPos core) {
        if (sieges.remove(core) != null) {
            setDirty();
        }
    }

    public List<Siege> activeSieges() {
        return new ArrayList<>(sieges.values());
    }

    // --- lockouts ---

    public void addLockout(BlockPos center, int radius, ResourceKey<Level> dimension, long nowTick) {
        lockouts.add(new Lockout(center.immutable(), radius, dimension, nowTick + LOCKOUT_TICKS));
        setDirty();
    }

    public boolean isLockedOut(ResourceKey<Level> dimension, BlockPos pos, long nowTick) {
        for (Lockout lockout : lockouts) {
            if (lockout.dimension().equals(dimension) && lockout.expiryTick() > nowTick && lockout.covers(pos)) {
                return true;
            }
        }
        return false;
    }

    public long lockoutSecondsLeft(ResourceKey<Level> dimension, BlockPos pos, long nowTick) {
        long latest = 0;
        for (Lockout lockout : lockouts) {
            if (lockout.dimension().equals(dimension) && lockout.covers(pos)) {
                latest = Math.max(latest, lockout.expiryTick());
            }
        }
        return Math.max(0, (latest - nowTick) / 20);
    }

    public void pruneExpiredLockouts(long nowTick) {
        boolean changed = lockouts.removeIf(l -> l.expiryTick() <= nowTick);
        if (changed) {
            setDirty();
        }
    }

    // --- NBT ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag siegeList = new ListTag();
        for (Siege siege : sieges.values()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Attacker", siege.attackerClan);
            s.putUUID("Defender", siege.defenderClan);
            s.putLong("Core", siege.core.asLong());
            s.putString("Dim", siege.dimension.location().toString());
            s.putInt("Hits", siege.hitsRemaining);
            s.putLong("LastActivity", siege.lastActivityTick);
            siegeList.add(s);
        }
        tag.put("Sieges", siegeList);

        ListTag lockoutList = new ListTag();
        for (Lockout lockout : lockouts) {
            CompoundTag l = new CompoundTag();
            l.putLong("Center", lockout.center().asLong());
            l.putInt("Radius", lockout.radius());
            l.putString("Dim", lockout.dimension().location().toString());
            l.putLong("Expiry", lockout.expiryTick());
            lockoutList.add(l);
        }
        tag.put("Lockouts", lockoutList);
        return tag;
    }

    public static SiegeManager load(CompoundTag tag) {
        SiegeManager manager = new SiegeManager();
        ListTag siegeList = tag.getList("Sieges", Tag.TAG_COMPOUND);
        for (int i = 0; i < siegeList.size(); i++) {
            CompoundTag s = siegeList.getCompound(i);
            BlockPos core = BlockPos.of(s.getLong("Core"));
            manager.sieges.put(core, new Siege(
                    s.getUUID("Attacker"), s.getUUID("Defender"), core,
                    dim(s.getString("Dim")), s.getInt("Hits"), s.getLong("LastActivity")));
        }
        ListTag lockoutList = tag.getList("Lockouts", Tag.TAG_COMPOUND);
        for (int i = 0; i < lockoutList.size(); i++) {
            CompoundTag l = lockoutList.getCompound(i);
            manager.lockouts.add(new Lockout(
                    BlockPos.of(l.getLong("Center")), l.getInt("Radius"),
                    dim(l.getString("Dim")), l.getLong("Expiry")));
        }
        return manager;
    }

    private static ResourceKey<Level> dim(String id) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id));
    }

    // convenience for iteration in the tick handler
    public Iterator<Map.Entry<BlockPos, Siege>> siegeEntries() {
        return sieges.entrySet().iterator();
    }
}
