package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;

/**
 * Water that Fire Magic left behind when it melted ice. It's cleaned up after
 * {@link #LIFETIME} ticks so a mage can't flood a place by melting ice at it -
 * removing the source block lets any water that flowed off it drain on its own.
 */
public final class MeltedWater {

    private static final int LIFETIME = 400; // 20 s
    private static final int CAP = 6000;

    private static final Map<BlockPos, Entry> tracked = new HashMap<>();

    private record Entry(String dimension, long endTick) {
    }

    private MeltedWater() {
    }

    public static void mark(ServerLevel level, BlockPos pos) {
        if (tracked.size() >= CAP) {
            return;
        }
        tracked.putIfAbsent(pos.immutable(),
                new Entry(level.dimension().location().toString(), level.getGameTime() + LIFETIME));
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (tracked.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        tracked.entrySet().removeIf(e -> {
            if (now < e.getValue().endTick()) {
                return false;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().toString().equals(e.getValue().dimension())) {
                    continue;
                }
                BlockPos p = e.getKey();
                if (level.getFluidState(p).getType() == Fluids.WATER || level.getFluidState(p).getType() == Fluids.FLOWING_WATER) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    level.levelEvent(1501, p, 0); // fizzle
                }
                break;
            }
            return true;
        });
    }

    public static void clearAll() {
        tracked.clear();
    }
}
