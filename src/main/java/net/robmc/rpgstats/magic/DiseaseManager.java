package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;

/** Pestilence's disease - damage over 5 seconds, like a bleed but green. */
public final class DiseaseManager {

    private static final class Disease {
        long endTick;
        long nextTick;
        float perTick;
    }

    private static final Map<Integer, Disease> diseased = new HashMap<>();

    private DiseaseManager() {
    }

    public static void start(LivingEntity target, float perTick, int durationTicks) {
        long now = target.level().getGameTime();
        Disease d = new Disease();
        d.endTick = now + durationTicks;
        d.nextTick = now + StatFormulas.PESTILENCE_DOT_INTERVAL;
        d.perTick = perTick;
        diseased.put(target.getId(), d);
    }

    public static void clear(int entityId) {
        diseased.remove(entityId);
    }

    public static int remainingTicks(long now, int entityId) {
        Disease d = diseased.get(entityId);
        return d == null ? 0 : (int) Math.max(0, d.endTick - now);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (diseased.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        diseased.entrySet().removeIf(entry -> {
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
            Disease d = entry.getValue();
            while (now >= d.nextTick) {
                d.nextTick += StatFormulas.PESTILENCE_DOT_INTERVAL;
                le.hurt(le.damageSources().magic(), d.perTick);
                ServerLevel sl = (ServerLevel) le.level();
                sl.sendParticles(ParticleTypes.COMPOSTER,
                        le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 8, 0.25, 0.35, 0.25, 0.02);
                sl.sendParticles(ParticleTypes.SNEEZE,
                        le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            }
            return now >= d.endTick;
        });
    }
}
