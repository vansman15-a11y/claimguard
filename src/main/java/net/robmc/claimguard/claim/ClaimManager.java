package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.SavedData;
import net.robmc.claimguard.registry.ModBlocks;

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

    /**
     * Minimum gap between two claim cores, in blocks, measured as a square
     * (Chebyshev) distance on the X/Z plane - height is ignored. A new core may
     * only be placed if every existing core is at least this far away.
     */
    public static final int MIN_CLAIM_SPACING = 500;

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

    public Claim createClaim(BlockPos corePos, UUID owner, UUID clanId) {
        Claim claim = new Claim(corePos.immutable(), owner, ClaimTier.LEVEL_1, clanId);
        claimsByCore.put(claim.getCorePos(), claim);
        setDirty(); // tells Minecraft "something changed, please re-save this to disk"
        return claim;
    }

    /** An admin protection zone: no clan, largest tier, PvP disabled. */
    public Claim createAdminClaim(BlockPos corePos) {
        Claim claim = new Claim(corePos.immutable(), new UUID(0, 0), ClaimTier.LEVEL_6, null);
        claim.setAdmin(true);
        claimsByCore.put(claim.getCorePos(), claim);
        setDirty();
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

    /**
     * Returns an existing claim whose core sits within {@link #MIN_CLAIM_SPACING}
     * blocks of {@code pos} (square distance on X/Z, height ignored), or empty if
     * the spot is far enough from every claim to place a new core there.
     *
     * Linear scan, same as {@link #getClaimAt} - fine for hundreds of claims.
     */
    public Optional<Claim> findClaimTooCloseTo(BlockPos pos) {
        for (Claim claim : claimsByCore.values()) {
            BlockPos core = claim.getCorePos();
            int dx = Math.abs(core.getX() - pos.getX());
            int dz = Math.abs(core.getZ() - pos.getZ());
            if (Math.max(dx, dz) < MIN_CLAIM_SPACING) {
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

    /** True if any admin protection zone exists in this dimension (fast pre-check). */
    public boolean hasAdminClaims() {
        for (Claim claim : claimsByCore.values()) {
            if (claim.isAdmin()) {
                return true;
            }
        }
        return false;
    }

    /** True if the position is inside an admin protection zone. */
    public boolean isInAdminClaim(BlockPos pos) {
        for (Claim claim : claimsByCore.values()) {
            if (claim.isAdmin() && claim.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Every claim in this dimension owned by the given clan. */
    public List<Claim> claimsOwnedByClan(UUID clanId) {
        List<Claim> out = new ArrayList<>();
        for (Claim claim : claimsByCore.values()) {
            if (clanId.equals(claim.getClanId())) {
                out.add(claim);
            }
        }
        return out;
    }

    /**
     * Removes any claim whose core block is no longer present in the given chunk.
     *
     * A claim is normally deleted when its core is broken (see
     * ClaimCoreBlock.playerWillDestroy), but a core can also disappear without
     * that ever running - worldedit, /setblock, /fill, chunk regeneration, or (the
     * reason this exists) an older buggy build that reverted the placed block but
     * left the claim behind. Called every time a chunk loads, so those stale
     * claims get cleaned up the moment the area is next loaded.
     */
    public void forgetClaimsWithMissingCore(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<BlockPos> stale = new ArrayList<>();
        for (Claim claim : claimsByCore.values()) {
            BlockPos core = claim.getCorePos();
            boolean inThisChunk = (core.getX() >> 4) == chunkPos.x && (core.getZ() >> 4) == chunkPos.z;
            if (!inThisChunk) {
                continue;
            }
            // Admin claims use ADMIN_CORE, everyone else uses CLAIM_CORE.
            var expected = claim.isAdmin() ? ModBlocks.ADMIN_CORE.get() : ModBlocks.CLAIM_CORE.get();
            if (!chunk.getBlockState(core).is(expected)) {
                stale.add(core);
            }
        }
        for (BlockPos core : stale) {
            claimsByCore.remove(core);
            setDirty();
        }
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
