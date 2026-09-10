package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Short-lived blocks a spell conjures (e.g. Seismic Pillar's stone), removed on a timer. */
public final class ConjuredBlocks {

    private static final class Batch {
        final String dimension;
        final List<BlockPos> blocks;
        final BlockState placed;
        final long endTick;

        Batch(String dimension, List<BlockPos> blocks, BlockState placed, long endTick) {
            this.dimension = dimension;
            this.blocks = blocks;
            this.placed = placed;
            this.endTick = endTick;
        }
    }

    private static final List<Batch> batches = new ArrayList<>();

    private ConjuredBlocks() {
    }

    /** Place {@code state} at every replaceable, unprotected spot in {@code where}; remove it {@code lifetimeTicks} later. */
    public static void place(ServerPlayer caster, Iterable<BlockPos> where, BlockState state, int lifetimeTicks) {
        ServerLevel level = caster.serverLevel();
        List<BlockPos> done = new ArrayList<>();
        for (BlockPos p : where) {
            BlockState here = level.getBlockState(p);
            if ((here.isAir() || here.canBeReplaced())
                    && net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, p, caster)) {
                level.setBlockAndUpdate(p, state);
                done.add(p.immutable());
            }
        }
        if (!done.isEmpty()) {
            batches.add(new Batch(level.dimension().location().toString(), done, state,
                    level.getGameTime() + lifetimeTicks));
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (batches.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        batches.removeIf(batch -> {
            if (now < batch.endTick) {
                return false;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().toString().equals(batch.dimension)) {
                    continue;
                }
                for (BlockPos p : batch.blocks) {
                    if (level.getBlockState(p) == batch.placed) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        level.levelEvent(2001, p, Block.getId(batch.placed));
                    }
                }
                break;
            }
            return true;
        });
    }

    public static void clearAll() {
        batches.clear();
    }
}
