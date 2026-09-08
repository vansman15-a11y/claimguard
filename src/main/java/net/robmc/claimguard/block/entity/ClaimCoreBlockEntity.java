package net.robmc.claimguard.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.robmc.claimguard.registry.ModBlockEntities;

import java.util.UUID;

/**
 * A BlockEntity is "extra data attached to one specific block position."
 * A vanilla example: a Chest is a Block (the visual box + collision) PLUS a
 * BlockEntity (the 27 item slots it remembers). Ours works the same way:
 * the ClaimCoreBlock is what you see/place/break, and THIS class is where we
 * remember who owns it, purely so the block itself can show info to the owner
 * (e.g. via a tooltip or message). The actual claim logic/lookup lives in
 * ClaimManager - this class just mirrors the owner for convenience/display.
 */
public class ClaimCoreBlockEntity extends BlockEntity {

    private UUID owner;

    public ClaimCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAIM_CORE.get(), pos, state);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged(); // marks this block entity as needing to be re-saved
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
    }
}
