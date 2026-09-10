package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ice Wall: a 3x3 pane of ice conjured where you aim, gone {@link StatFormulas#ICE_WALL_TICKS} ticks later. */
public final class IceWallManager {

    private static final class Wall {
        final String dimension;
        final List<BlockPos> blocks;
        final long endTick;

        Wall(String dimension, List<BlockPos> blocks, long endTick) {
            this.dimension = dimension;
            this.blocks = blocks;
            this.endTick = endTick;
        }
    }

    private static final List<Wall> walls = new ArrayList<>();

    private IceWallManager() {
    }

    /** Raise a wall facing away from the caster at their aim point. Returns true if any ice was placed. */
    public static boolean raise(ServerPlayer caster, Vec3 aim) {
        ServerLevel level = caster.serverLevel();
        // face the wall across the caster's look direction
        Direction facing = caster.getDirection();
        Direction across = facing.getClockWise(); // wall runs left-right in front of the player
        BlockPos base = BlockPos.containing(aim.x, Math.round(aim.y), aim.z);
        // settle the base onto the ground (fall up to a few blocks)
        for (int i = 0; i < 4 && base.getY() > level.getMinBuildHeight() + 1
                && level.getBlockState(base.below()).isAir(); i++) {
            base = base.below();
        }

        List<BlockPos> placed = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h < 3; h++) {
                BlockPos p = base.relative(across, w).above(h);
                if (!seen.add(p.immutable())) {
                    continue;
                }
                BlockState here = level.getBlockState(p);
                if ((here.isAir() || here.canBeReplaced())
                        && net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, p, caster)) {
                    level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                    placed.add(p.immutable());
                }
            }
        }
        if (placed.isEmpty()) {
            return false;
        }
        walls.add(new Wall(level.dimension().location().toString(), placed,
                level.getGameTime() + StatFormulas.ICE_WALL_TICKS));
        level.levelEvent(2005, base, 0); // sparkle
        return true;
    }

    /** Called every server tick from RpgEvents - drop expired walls (only blocks that are still our ice). */
    public static void tick(MinecraftServer server) {
        if (walls.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        walls.removeIf(wall -> {
            if (now < wall.endTick) {
                return false;
            }
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(wall.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level != null) {
                for (BlockPos p : wall.blocks) {
                    if (level.getBlockState(p).is(Blocks.ICE)) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                        level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(Blocks.ICE.defaultBlockState()));
                    }
                }
            }
            return true;
        });
    }

    public static void clearAll() {
        walls.clear();
    }
}
