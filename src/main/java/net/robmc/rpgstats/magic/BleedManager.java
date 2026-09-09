package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.robmc.rpgstats.StatFormulas;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Sunder's bleed - small recurring damage on a target for a few seconds. */
public final class BleedManager {

    private static final Vector3f BLOOD = new Vector3f(0.7f, 0.05f, 0.05f);

    private static final class Bleed {
        long endTick;
        long nextTick;
        float perHit;
    }

    private static final Map<Integer, Bleed> bleeding = new HashMap<>();

    private BleedManager() {
    }

    public static void start(LivingEntity target, LivingEntity source, float perHit, int durationTicks) {
        long now = target.level().getGameTime();
        Bleed b = new Bleed();
        b.endTick = now + durationTicks;
        b.nextTick = now + StatFormulas.SUNDER_BLEED_INTERVAL;
        b.perHit = perHit;
        bleeding.put(target.getId(), b);
    }

    public static void clear(int entityId) {
        bleeding.remove(entityId);
    }

    /** Ticks of bleed left on an entity ({@code now} = current game time), or 0. For the target frame. */
    public static int remainingTicks(long now, int entityId) {
        Bleed b = bleeding.get(entityId);
        return b == null ? 0 : (int) Math.max(0, b.endTick - now);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (bleeding.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        bleeding.entrySet().removeIf(entry -> {
            Entity ent = null;
            for (ServerLevel level : server.getAllLevels()) {
                ent = level.getEntity(entry.getKey());
                if (ent != null) {
                    break;
                }
            }
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                return true;
            }
            Bleed b = entry.getValue();
            if (now >= b.nextTick) {
                b.nextTick += StatFormulas.SUNDER_BLEED_INTERVAL;
                le.hurt(le.damageSources().generic(), b.perHit); // generic so Dex can't shrug it off
                ((ServerLevel) le.level()).sendParticles(new DustParticleOptions(BLOOD, 1.2f),
                        le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
            }
            return now >= b.endTick;
        });
    }
}
