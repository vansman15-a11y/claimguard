package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fearcraft's effect: frightened monsters drop their target and path away from
 * the caster for a few seconds. There's no vanilla "flee from player" goal, so
 * each tick we clear the mob's target and steer its navigation away.
 */
public final class FearManager {

    private static final class Fear {
        final UUID source;
        final long endTick;
        long nextRepath;

        Fear(UUID source, long endTick) {
            this.source = source;
            this.endTick = endTick;
        }
    }

    private static final Map<Integer, Fear> feared = new HashMap<>();

    private FearManager() {
    }

    public static void frighten(Mob mob, ServerPlayer source, int durationTicks) {
        long now = mob.level().getGameTime();
        feared.put(mob.getId(), new Fear(source.getUUID(), now + durationTicks));
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
    }

    public static boolean isFeared(int entityId) {
        return feared.containsKey(entityId);
    }

    public static int remainingTicks(long now, int entityId) {
        Fear f = feared.get(entityId);
        return f == null ? 0 : (int) Math.max(0, f.endTick - now);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (feared.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        feared.entrySet().removeIf(entry -> {
            Fear f = entry.getValue();
            if (now >= f.endTick) {
                return true;
            }
            Entity ent = null;
            for (ServerLevel level : server.getAllLevels()) {
                ent = level.getEntity(entry.getKey());
                if (ent != null) {
                    break;
                }
            }
            if (!(ent instanceof Mob mob) || !mob.isAlive()) {
                return true;
            }
            ServerPlayer source = server.getPlayerList().getPlayer(f.source);
            if (source == null || source.level() != mob.level()
                    || source.distanceToSqr(mob) > 40 * 40) {
                return true; // caster gone or way out of range - fear breaks
            }

            mob.setTarget(null);
            mob.setLastHurtByMob(null);

            Vec3 away = mob.position().subtract(source.position());
            if (away.lengthSqr() < 1.0e-4) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize().scale(StatFormulas.FEARCRAFT_FLEE_DISTANCE);
            Vec3 goal = mob.position().add(away.x, 0, away.z);

            if (now >= f.nextRepath || mob.getNavigation().isDone()) {
                f.nextRepath = now + 10;
                mob.getNavigation().moveTo(goal.x, mob.getY(), goal.z, StatFormulas.FEARCRAFT_FLEE_SPEED);
            }
            mob.getLookControl().setLookAt(goal.x, mob.getEyeY(), goal.z);

            if (now % 6 == 0) {
                ((ServerLevel) mob.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
            return false;
        });
    }
}
