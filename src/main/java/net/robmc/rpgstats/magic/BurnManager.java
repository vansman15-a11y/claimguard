package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;

/**
 * Fire Magic's stacking burn. Ember Dart lays it on (one stack per hit, up to
 * {@link StatFormulas#FIRE_BURN_MAX_STACKS}); Sunburst and Cinder Maelstrom keep
 * it topped up. Each stack adds a little damage every {@link StatFormulas#FIRE_BURN_INTERVAL}
 * ticks, and the whole thing falls off {@link StatFormulas#FIRE_BURN_DURATION_TICKS}
 * ticks after it was last refreshed - so you have to keep re-applying it.
 */
public final class BurnManager {

    private static final class Burn {
        int stacks;
        long endTick;
        long nextTick;
        double perStackPerTick;
    }

    private static final Map<Integer, Burn> burning = new HashMap<>();

    private BurnManager() {
    }

    /** Add a burn stack to the target (capped), refresh the duration, and take the stronger per-stack damage. */
    public static void apply(LivingEntity target, double perStackPerTick) {
        if (target == null || !target.isAlive()) {
            return;
        }
        long now = target.level().getGameTime();
        Burn b = burning.computeIfAbsent(target.getId(), k -> {
            Burn nb = new Burn();
            nb.nextTick = now + StatFormulas.FIRE_BURN_INTERVAL;
            return nb;
        });
        b.stacks = Math.min(StatFormulas.FIRE_BURN_MAX_STACKS, b.stacks + 1);
        b.endTick = now + StatFormulas.FIRE_BURN_DURATION_TICKS;
        b.perStackPerTick = Math.max(b.perStackPerTick, perStackPerTick);
    }

    public static void clear(int entityId) {
        burning.remove(entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (burning.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        burning.entrySet().removeIf(entry -> {
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
            Burn b = entry.getValue();
            while (now >= b.nextTick) {
                b.nextTick += StatFormulas.FIRE_BURN_INTERVAL;
                float dmg = (float) (b.stacks * b.perStackPerTick);
                le.hurt(le.damageSources().onFire(), dmg); // fire-typed: fire resistance / protection counter it
                ServerLevel sl = (ServerLevel) le.level();
                sl.sendParticles(ParticleTypes.FLAME,
                        le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
                        3 + b.stacks * 2, 0.25, 0.4, 0.25, 0.01);
                sl.sendParticles(ParticleTypes.SMALL_FLAME,
                        le.getX(), le.getY() + 0.1, le.getZ(), 4, 0.25, 0.05, 0.25, 0.0);
            }
            return now >= b.endTick;
        });
    }
}
