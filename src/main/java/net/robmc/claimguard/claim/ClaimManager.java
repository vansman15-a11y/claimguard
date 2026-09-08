package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds every Claim that exists in ONE dimension (overworld, nether, etc each get
 * their own ClaimManager automatically - see get() below).
 *
 * This extends SavedData, which is Forge/Minecraft's built-in system for "a chunk of
 * data that gets written to a file in the world's save folder and loaded back on
 * server start." Think of it as the mod's own tiny database file, one per dimension,
 * stored under world/data/claimguard.dat (once saved).
 */
public class ClaimManager extends SavedData {

    private static final String DATA_NAME = "claimguard";

    // Keyed by the claim core's block position so lookups/removals by core are O(1)-ish.
    private final Map<BlockPos, Claim> claimsByCore = new HashMap<>();

    public ClaimManager() {
    }

    /**
     * Gets (or creates) the ClaimManager for a given server-side dimension.
     * Call this every time you need claim data - it's cheap, Minecraft caches the result
     * internally for you.
     */
    public static ClaimManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ClaimManager::load,
                ClaimManager::new,
                DATA_NAME
        );
    }

    public Claim createClaim(BlockPos corePos, UUID owner) {
        Claim claim = new Claim(corePos.immutable(), owner, ClaimTier.LEVEL_1);
        claimsByCore.put(claim.getCorePos(), claim);
        setDirty(); // tells Minecraft "something changed, please re-save this to disk"
        return claim;
    }

    public void removeClaim(BlockPos corePos) {
        if (claimsByCore.remove(corePos) != null) {
            setDirty();
        }
    }

    public Optional<Claim> getClaimByCore(BlockPos corePos) {
        return Optional.ofNullable(claimsByCore.get(corePos));
    }

    /**
     * Finds whichever claim (if any) protects the given position.
     * This does a linear scan over every claim in the dimension - completely fine for
     * dozens or even hundreds of claims. If you ever have thousands, this is the method
     * you'd optimize first (e.g. bucket claims by chunk).
     */
    public Optional<Claim> getClaimAt(BlockPos pos) {
        for (Claim claim : claimsByCore.values()) {
            if (claim.contains(pos)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public boolean upgrade(BlockPos corePos) {
        Claim claim = claimsByCore.get(corePos);
        if (claim == null) {
            return false;
        }
        ClaimTier next = claim.getTier().next();
        if (next == null) {
            return false;
        }
        claim.setTier(next);
        setDirty();
        return true;
    }

    public List<Claim> getAllClaims() {
        return new ArrayList<>(claimsByCore.values());
    }

    // --- NBT save/load: this is what makes claims survive a server restart ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Claim claim : claimsByCore.values()) {
            list.add(claim.save());
        }
        tag.put("Claims", list);
        return tag;
    }

    public static ClaimManager load(CompoundTag tag) {
        ClaimManager manager = new ClaimManager();
        ListTag list = tag.getList("Claims", 10); // 10 = NBT "compound tag" type id
        for (int i = 0; i < list.size(); i++) {
            Claim claim = Claim.load(list.getCompound(i));
            manager.claimsByCore.put(claim.getCorePos(), claim);
        }
        return manager;
    }
}
