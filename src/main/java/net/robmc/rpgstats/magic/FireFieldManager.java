package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cinder Maelstrom's lingering fire field. It sits on the ground for a few
 * seconds, and every {@link StatFormulas#CINDER_FIELD_DAMAGE_INTERVAL} ticks it
 * deals its own damage to everything inside (allies and the caster included) and
 * tops up the {@link BurnManager} burn on them.
 */
public final class FireFieldManager {

    private static final class Field {
        final UUID owner;
        final String dimension;
        final Vec3 center;
        final double radius;
        final long endTick;
        long nextDamageTick;
        final double tickDamage;
        final double burnPerStack;

        Field(UUID owner, String dimension, Vec3 center, double radius, long now, double tickDamage, double burnPerStack) {
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.endTick = now + StatFormulas.CINDER_FIELD_DURATION_TICKS;
            this.nextDamageTick = now + StatFormulas.CINDER_FIELD_DAMAGE_INTERVAL;
            this.tickDamage = tickDamage;
            this.burnPerStack = burnPerStack;
        }
    }

    private static final List<Field> fields = new ArrayList<>();

    private FireFieldManager() {
    }

    public static void spawn(ServerPlayer caster, Vec3 center, int spellLevel) {
        long now = caster.serverLevel().getGameTime();
        fields.add(new Field(caster.getUUID(),
                caster.serverLevel().dimension().location().toString(),
                center, StatFormulas.CINDER_FIELD_RADIUS, now,
                StatFormulas.cinderFieldTickDamage(spellLevel),
                StatFormulas.fireBurnPerStack(spellLevel)));
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (fields.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        fields.removeIf(f -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(f.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }

            // ring of embers on the ground so people can see where not to stand
            level.sendParticles(ParticleTypes.FLAME,
                    f.center.x, f.center.y + 0.1, f.center.z,
                    (int) (f.radius * 8), f.radius * 0.7, 0.15, f.radius * 0.7, 0.01);
            level.sendParticles(ParticleTypes.LAVA,
                    f.center.x, f.center.y + 0.1, f.center.z,
                    (int) f.radius, f.radius * 0.6, 0.1, f.radius * 0.6, 0.0);

            while (now >= f.nextDamageTick) {
                f.nextDamageTick += StatFormulas.CINDER_FIELD_DAMAGE_INTERVAL;
                // a fire crackle for as long as the field burns (~1 s clip, replayed each field tick)
                level.playSound(null, BlockPos.containing(f.center), SoundEvents.FIRE_AMBIENT,
                        SoundSource.PLAYERS, 3.0f, 0.85f);
                ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
                DamageSource fromCaster = owner != null
                        ? owner.damageSources().indirectMagic(owner, owner)
                        : level.damageSources().onFire();
                DamageSource selfHit = level.damageSources().magic();
                double r2 = f.radius * f.radius;
                AABB box = new AABB(f.center.x - f.radius, f.center.y - 2.0, f.center.z - f.radius,
                        f.center.x + f.radius, f.center.y + 3.0, f.center.z + f.radius);
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (!le.isAlive()) {
                        continue;
                    }
                    double dx = le.getX() - f.center.x;
                    double dz = le.getZ() - f.center.z;
                    if (dx * dx + dz * dz > r2) {
                        continue;
                    }
                    // no exclusions - the caster and their allies take it just the same
                    le.hurt(le == owner ? selfHit : fromCaster, (float) f.tickDamage);
                    BurnManager.apply(le, f.burnPerStack);
                    FireMark.mark(le);
                }
                FireMelt.meltAround(level, f.center, f.radius); // the field keeps melting ice / snow under it
                EarthenPathManager.tryIgnite(level, f.center);
            }
            return now >= f.endTick;
        });
    }

    public static void clearAll() {
        fields.clear();
    }
}
