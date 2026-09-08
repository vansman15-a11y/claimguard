package net.robmc.claimguard.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
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
    /** The clan that owns this claim (the founder's clan when the core was placed), or null. */
    @Nullable
    private UUID clanId;
    /** True for an admin-placed protection zone (no clan, always max size, PvP off). */
    private boolean admin;

    // FUTURE: when bed-style respawn/waypoint is added, this is the natural place to store
    // "this is the claim the player last set as their spawn point" - probably as a flag here,
    // or as a separate per-player mapping in ClaimManager pointing at a corePos.

    public Claim(BlockPos corePos, UUID owner, ClaimTier tier, @Nullable UUID clanId) {
        this.corePos = corePos;
        this.owner = owner;
        this.tier = tier;
        this.clanId = clanId;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    public UUID getOwner() {
        return owner;
    }

    @Nullable
    public UUID getClanId() {
        return clanId;
    }

    public void setClanId(@Nullable UUID clanId) {
        this.clanId = clanId;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
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

    /** 1-based level number for display (LEVEL_1 -> "Level 1"). */
    public int getLevel() {
        return tier.ordinal() + 1;
    }

    /** Horizontal half-width (X and Z) of the protected column, in blocks. */
    public int getRadius() {
        return tier.getRadius();
    }

    /**
     * True if the position is inside this claim. The claim is a full-height column:
     * it protects everything from bedrock to the build limit within `radius` blocks
     * of the core on X and Z, so Y is not checked here.
     */
    public boolean contains(BlockPos pos) {
        int r = tier.getRadius();
        return Math.abs(pos.getX() - corePos.getX()) <= r
                && Math.abs(pos.getZ() - corePos.getZ()) <= r;
    }

    /**
     * The protected volume as an AABB for the visual border - the X/Z column around
     * the core, running from {@code minY} to {@code maxY} (pass the level's build
     * limits, e.g. level.getMinBuildHeight() / level.getMaxBuildHeight()).
     */
    public AABB getBounds(int minY, int maxY) {
        int r = tier.getRadius();
        return new AABB(
                corePos.getX() - r, minY, corePos.getZ() - r,
                corePos.getX() + r + 1, maxY, corePos.getZ() + r + 1
        );
    }

    // --- Saving/loading to NBT (Minecraft's binary save-file format) ---
    // Every field you want to survive a server restart has to be written here manually.

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("CorePos", corePos.asLong());
        tag.putUUID("Owner", owner);
        tag.putInt("Tier", tier.ordinal());
        if (clanId != null) {
            tag.putUUID("ClanId", clanId);
        }
        if (admin) {
            tag.putBoolean("Admin", true);
        }
        return tag;
    }

    public static Claim load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("CorePos"));
        UUID owner = tag.getUUID("Owner");
        ClaimTier tier = ClaimTier.byIndex(tag.getInt("Tier"));
        UUID clanId = tag.hasUUID("ClanId") ? tag.getUUID("ClanId") : null;
        Claim claim = new Claim(pos, owner, tier, clanId);
        claim.admin = tag.getBoolean("Admin");
        return claim;
    }
}
