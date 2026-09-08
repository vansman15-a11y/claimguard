package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * Plain data class representing one protected area in the world.
 * One Claim = one placed Claim Core block + everything it protects.
 *
 * This does NOT extend anything special from Forge/Minecraft - it's just a
 * regular Java object that holds fields, similar to a Lua table you'd pass
 * around, except the fields and their types are fixed at compile time.
 */
public class Claim {

    private final BlockPos corePos;
    private final UUID owner;
    private ClaimTier tier;

    // FUTURE: when the clan system exists, add a `UUID clanId` (or similar) field here
    // and let ClaimManager check clan membership in addition to (or instead of) owner UUID.
    // FUTURE: when bed-style respawn/waypoint is added, this is the natural place to store
    // "this is the claim the player last set as their spawn point" - probably as a flag here,
    // or as a separate per-player mapping in ClaimManager pointing at a corePos.

    public Claim(BlockPos corePos, UUID owner, ClaimTier tier) {
        this.corePos = corePos;
        this.owner = owner;
        this.tier = tier;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    public UUID getOwner() {
        return owner;
    }

    public ClaimTier getTier() {
        return tier;
    }

    public void setTier(ClaimTier tier) {
        this.tier = tier;
    }

    public boolean isOwnedBy(UUID playerId) {
        return owner.equals(playerId);
    }

    /**
     * The protected cube as an AABB (axis-aligned bounding box), centered on the core,
     * extending `radius` blocks in every direction (a true cube, per your earlier choice).
     */
    public AABB getBounds() {
        int r = tier.getRadius();
        return new AABB(
                corePos.getX() - r, corePos.getY() - r, corePos.getZ() - r,
                corePos.getX() + r + 1, corePos.getY() + r + 1, corePos.getZ() + r + 1
        );
    }

    public boolean contains(BlockPos pos) {
        return getBounds().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // --- Saving/loading to NBT (Minecraft's binary save-file format) ---
    // Every field you want to survive a server restart has to be written here manually.

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("CorePos", corePos.asLong());
        tag.putUUID("Owner", owner);
        tag.putInt("Tier", tier.ordinal());
        return tag;
    }

    public static Claim load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("CorePos"));
        UUID owner = tag.getUUID("Owner");
        ClaimTier tier = ClaimTier.byIndex(tag.getInt("Tier"));
        return new Claim(pos, owner, tier);
    }
}
