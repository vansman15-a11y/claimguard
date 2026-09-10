package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Riptide's travelling wave. Each tick the front rolls forward, scooping up and
 * damaging anything it passes over; when it runs out (or hits a wall) it bursts.
 */
public final class RiptideWave {

    private static final class Wave {
        final UUID owner;
        final String dimension;
        Vec3 pos;
        final Vec3 dir;
        final int spellLevel;
        int ticksLeft;
        final Set<Integer> hit = new HashSet<>();

        Wave(UUID owner, String dimension, Vec3 pos, Vec3 dir, int spellLevel, int ticksLeft) {
            this.owner = owner;
            this.dimension = dimension;
            this.pos = pos;
            this.dir = dir;
            this.spellLevel = spellLevel;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Wave> waves = new ArrayList<>();

    private RiptideWave() {
    }

    public static void launch(ServerPlayer caster, int spellLevel) {
        Vec3 dir = caster.getViewVector(1.0f);
        Vec3 start = caster.getEyePosition().add(dir.scale(1.0));
        waves.add(new Wave(caster.getUUID(), caster.serverLevel().dimension().location().toString(),
                start, dir, spellLevel, StatFormulas.RIPTIDE_TICKS));
        caster.serverLevel().playSound(null, caster.blockPosition(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (waves.isEmpty()) {
            return;
        }
        waves.removeIf(wave -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(wave.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);

            Vec3 next = wave.pos.add(wave.dir.scale(StatFormulas.RIPTIDE_SPEED));
            boolean blocked = !level.getBlockState(net.minecraft.core.BlockPos.containing(next)).getCollisionShape(level,
                    net.minecraft.core.BlockPos.containing(next)).isEmpty();
            wave.pos = next;
            wave.ticksLeft--;

            // wall of spray
            level.sendParticles(ParticleTypes.SPLASH, wave.pos.x, wave.pos.y, wave.pos.z, 18, 0.6, 0.7, 0.6, 0.1);
            level.sendParticles(ParticleTypes.FALLING_WATER, wave.pos.x, wave.pos.y + 0.8, wave.pos.z, 8, 0.7, 0.4, 0.7, 0.0);

            double r = StatFormulas.RIPTIDE_CATCH_RADIUS;
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(wave.pos, wave.pos).inflate(r))) {
                if (le.getId() == (owner != null ? owner.getId() : -1) || !le.isAlive() || !wave.hit.add(le.getId())) {
                    continue;
                }
                float dmg = (float) (StatFormulas.riptideWaveDamage(wave.spellLevel) * mult(owner));
                le.hurt(src(owner, level), dmg);
                Vec3 push = wave.dir.scale(0.6);
                le.setDeltaMovement(push.x, StatFormulas.RIPTIDE_LIFT, push.z);
                le.hurtMarked = true;
                le.setAirSupply(le.getMaxAirSupply());
                awardXp(owner, le, 1);
            }

            if (wave.ticksLeft <= 0 || blocked) {
                burst(level, owner, wave);
                return true;
            }
            return false;
        });
    }

    private static void burst(ServerLevel level, ServerPlayer owner, Wave wave) {
        level.sendParticles(ParticleTypes.EXPLOSION, wave.pos.x, wave.pos.y, wave.pos.z, 3, 0.6, 0.4, 0.6, 0.0);
        level.sendParticles(ParticleTypes.SPLASH, wave.pos.x, wave.pos.y, wave.pos.z, 60,
                StatFormulas.RIPTIDE_BURST_RADIUS * 0.5, 0.6, StatFormulas.RIPTIDE_BURST_RADIUS * 0.5, 0.2);
        level.playSound(null, net.minecraft.core.BlockPos.containing(wave.pos),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.3f);

        double r = StatFormulas.RIPTIDE_BURST_RADIUS;
        int enemies = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(wave.pos, wave.pos).inflate(r), LivingEntity::isAlive)) {
            if (owner != null && le.getId() == owner.getId()) {
                continue;
            }
            float dmg = (float) (StatFormulas.riptideBurstDamage(wave.spellLevel) * mult(owner));
            le.hurt(src(owner, level), dmg);
            Vec3 away = le.position().subtract(wave.pos);
            if (away.lengthSqr() > 1.0e-4) {
                away = away.normalize().scale(0.7);
                le.push(away.x, 0.35, away.z);
                le.hurtMarked = true;
            }
            if (isEnemy(owner, le)) {
                enemies++;
            }
        }
        awardXp(owner, null, enemies);
    }

    private static double mult(ServerPlayer owner) {
        return owner == null ? 1.0
                : StatFormulas.spellDamageMultiplier(RpgManager.stats(owner)) * Afflictions.spellDamageMult(owner);
    }

    private static net.minecraft.world.damagesource.DamageSource src(ServerPlayer owner, ServerLevel level) {
        return owner != null ? owner.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
    }

    private static boolean isEnemy(ServerPlayer owner, LivingEntity le) {
        if (owner == null || le == owner) {
            return false;
        }
        if (le instanceof Player other) {
            return !net.robmc.claimguard.clan.ClanActions.areFriendly(owner.server, owner.getUUID(), other.getUUID());
        }
        return le instanceof Enemy;
    }

    private static void awardXp(ServerPlayer owner, LivingEntity single, int count) {
        if (owner == null) {
            return;
        }
        int n = single != null ? (isEnemy(owner, single) ? 1 : 0) : count;
        if (n > 0) {
            RpgManager.addSpellXp(owner, Spell.RIPTIDE, StatFormulas.spellHitXp(n));
        }
    }

    public static void clearAll() {
        waves.clear();
    }
}
