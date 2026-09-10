package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Astral Nova: a gravity well ~5x5x5. Everything inside is dragged toward the
 * centre for 4 s. Barely damages - it's pure crowd control, and a determined
 * sprinter near the edge can still claw their way out.
 */
public final class AstralNovaManager {

    private static final class Nova {
        final UUID owner;
        final String dimension;
        final Vec3 center;
        final int spellLevel;
        final long endTick;
        long nextDamageTick;

        Nova(UUID owner, String dimension, Vec3 center, int spellLevel, long now) {
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.spellLevel = spellLevel;
            this.endTick = now + StatFormulas.ASTRAL_NOVA_TICKS;
            this.nextDamageTick = now + StatFormulas.ASTRAL_NOVA_DAMAGE_INTERVAL;
        }
    }

    private static final List<Nova> novas = new ArrayList<>();

    private AstralNovaManager() {
    }

    public static void create(ServerPlayer caster, Vec3 centre, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        novas.add(new Nova(caster.getUUID(), level.dimension().location().toString(), centre, spellLevel, level.getGameTime()));
        level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(centre), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.3f, 0.6f);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (novas.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        novas.removeIf(n -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(n.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(n.owner);

            // the vortex shell
            for (int i = 0; i < 20; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double p = (level.random.nextDouble() - 0.5) * Math.PI;
                double rad = StatFormulas.ASTRAL_NOVA_RADIUS * (0.7 + level.random.nextDouble() * 0.4);
                level.sendParticles(ParticleTypes.PORTAL,
                        n.center.x + Math.cos(a) * Math.cos(p) * rad,
                        n.center.y + Math.sin(p) * rad,
                        n.center.z + Math.sin(a) * Math.cos(p) * rad, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, n.center.x, n.center.y, n.center.z, 3, 0.1, 0.1, 0.1, 0.0);

            double r = StatFormulas.ASTRAL_NOVA_RADIUS;
            boolean damageTick = now >= n.nextDamageTick;
            if (damageTick) {
                n.nextDamageTick += StatFormulas.ASTRAL_NOVA_DAMAGE_INTERVAL;
            }
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(n.center, n.center).inflate(r + 0.5))) {
                if (le == owner || !le.isAlive()) {
                    continue;
                }
                Vec3 toCentre = n.center.subtract(le.position().add(0, le.getBbHeight() * 0.5, 0));
                double dist = toCentre.length();
                if (dist > r + 0.5) {
                    continue;
                }
                Vec3 pull = dist < 0.6 ? Vec3.ZERO : toCentre.normalize().scale(StatFormulas.ASTRAL_NOVA_PULL);
                le.setDeltaMovement(le.getDeltaMovement().scale(0.6).add(pull));
                le.hurtMarked = true;
                le.fallDistance = 0.0f;
                if (damageTick) {
                    float dmg = (float) StatFormulas.ASTRAL_NOVA_TICK_DAMAGE;
                    if (owner != null) {
                        dmg = ArcanaMark.onArcanaHit(owner, le, dmg);
                    }
                    le.hurt(level.damageSources().magic(), dmg);
                }
            }
            return now >= n.endTick;
        });
    }

    public static void clearAll() {
        novas.clear();
    }
}
