package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Fire Magic melts ice and snow it touches. */
public final class FireMelt {

    private FireMelt() {
    }

    /** Turn any ice / snow within {@code radius} of {@code centre} to water or air. */
    public static void meltAround(ServerLevel level, Vec3 centre, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(centre);
        double r2 = radius * radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    meltAt(level, c.offset(dx, dy, dz));
                }
            }
        }
    }

    public static void meltAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var b = state.getBlock();
        if (b == Blocks.ICE || b == Blocks.FROSTED_ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE) {
            level.setBlockAndUpdate(pos, level.dimensionType().ultraWarm()
                    ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
            fizz(level, pos);
        } else if (b == Blocks.SNOW || b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW) {
            level.removeBlock(pos, false);
            fizz(level, pos);
        }
    }

    private static void fizz(ServerLevel level, BlockPos pos) {
        level.levelEvent(1501, pos, 0); // vanilla "fire extinguish" hiss + smoke
    }
}
