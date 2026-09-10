package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quake Stomp: the caster is lifted, hovers for a few seconds, then dives to
 * wherever they were aiming at the end and crashes down for AOE damage. Range is
 * clamped between {@link StatFormulas#QUAKE_STOMP_MIN_RANGE} and {@code MAX}.
 */
public final class QuakeStomp {

    private enum Phase { HOLD, DIVE }

    private static final class Stomp {
        Phase phase = Phase.HOLD;
        long phaseEnd;
        final int spellLevel;

        Stomp(int spellLevel, long phaseEnd) {
            this.spellLevel = spellLevel;
            this.phaseEnd = phaseEnd;
        }
    }

    private static final Map<UUID, Stomp> active = new HashMap<>();

    private QuakeStomp() {
    }

    public static void start(ServerPlayer player, int spellLevel) {
        long now = player.serverLevel().getGameTime();
        active.put(player.getUUID(), new Stomp(spellLevel, now + StatFormulas.QUAKE_STOMP_HOLD_TICKS));
        player.setDeltaMovement(player.getDeltaMovement().x, StatFormulas.QUAKE_STOMP_LIFT, player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.2f, 0.5f);
    }

    public static void clear(UUID id) {
        active.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        active.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                return true;
            }
            Stomp s = entry.getValue();
            ServerLevel level = player.serverLevel();

            if (s.phase == Phase.HOLD) {
                // hover: kill vertical drift, keep them up
                Vec3 dm = player.getDeltaMovement();
                player.setDeltaMovement(dm.x * 0.6, Math.max(dm.y, -0.02) + 0.04, dm.z * 0.6);
                player.hurtMarked = true;
                player.fallDistance = 0.0f;
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(), player.getZ(),
                        4, 0.3, 0.2, 0.3, 0.0);
                if (now >= s.phaseEnd) {
                    launchDive(player, s);
                }
                return false;
            }

            // DIVE
            player.fallDistance = 0.0f;
            boolean grounded = player.onGround() || player.verticalCollision;
            if (grounded || now >= s.phaseEnd) {
                crash(player, s);
                return true;
            }
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
                    3, 0.2, 0.2, 0.2, 0.0);
            return false;
        });
    }

    private static void launchDive(ServerPlayer player, Stomp s) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.QUAKE_STOMP_MAX_RANGE));
        HitResult hr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 aim = hr.getType() != HitResult.Type.MISS ? hr.getLocation() : far;

        Vec3 toAim = aim.subtract(player.position());
        double dist = Math.max(StatFormulas.QUAKE_STOMP_MIN_RANGE,
                Math.min(StatFormulas.QUAKE_STOMP_MAX_RANGE, toAim.length()));
        Vec3 dir = toAim.lengthSqr() < 1.0e-4 ? look : toAim.normalize();
        Vec3 target = player.position().add(dir.scale(dist));

        Vec3 vel = target.subtract(player.position()).normalize().scale(StatFormulas.QUAKE_STOMP_DIVE_SPEED);
        player.setDeltaMovement(vel.x, Math.min(vel.y, -0.15), vel.z);
        player.hurtMarked = true;
        s.phase = Phase.DIVE;
        s.phaseEnd = player.serverLevel().getGameTime() + 60; // safety: land within 3 s or crash anyway
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    private static void crash(ServerPlayer player, Stomp s) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        double r = StatFormulas.QUAKE_STOMP_CRASH_RADIUS;
        float dmg = (float) (StatFormulas.quakeStompDamage(s.spellLevel)
                * StatFormulas.spellDamageMultiplier(RpgManager.stats(player)) * Afflictions.spellDamageMult(player));

        int enemies = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(r), LivingEntity::isAlive)) {
            if (le == player) {
                continue;
            }
            le.hurt(player.damageSources().indirectMagic(player, player), dmg);
            Vec3 away = le.position().subtract(at);
            if (away.lengthSqr() > 1.0e-4) {
                away = away.normalize().scale(StatFormulas.QUAKE_STOMP_KNOCKBACK);
                le.push(away.x, 0.35, away.z);
                le.hurtMarked = true;
            }
            if (le instanceof Enemy || (le instanceof Player p
                    && !net.robmc.claimguard.clan.ClanActions.areFriendly(player.server, player.getUUID(), p.getUUID()))) {
                enemies++;
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                at.x, at.y + 0.2, at.z, 60, r * 0.5, 0.2, r * 0.5, 0.25);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 2, 0.4, 0.1, 0.4, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.7f);
        if (enemies > 0) {
            RpgManager.addSpellXp(player, Spell.QUAKE_STOMP, StatFormulas.spellHitXp(enemies));
        }
    }
}
