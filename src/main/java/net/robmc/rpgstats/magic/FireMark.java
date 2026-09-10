package net.robmc.rpgstats.magic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which entities were recently touched by Fire Magic. When one of them
 * dies its drops get smelted (raw pork -&gt; cooked, etc.), the same as Fire Aspect.
 */
public final class FireMark {

    /** How long after the last fire hit a kill still counts as a "fire kill". */
    private static final int WINDOW_TICKS = 60;

    private static final Map<Integer, Long> marked = new HashMap<>();

    private FireMark() {
    }

    public static void mark(Entity entity) {
        if (entity != null) {
            marked.put(entity.getId(), entity.level().getGameTime() + WINDOW_TICKS);
        }
    }

    public static boolean isMarked(int entityId, long now) {
        Long end = marked.get(entityId);
        return end != null && now < end;
    }

    /** Called every server tick from RpgEvents - drop stale entries. */
    public static void tick(MinecraftServer server) {
        if (marked.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        marked.values().removeIf(end -> now >= end);
    }
}
