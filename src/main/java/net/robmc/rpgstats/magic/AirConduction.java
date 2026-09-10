package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightning Strike, Chain Shock and Howling Impact all conduct through water:
 * more damage on a soaked target, and the charge jumps to everyone else standing
 * in a ~5x5 patch of water around the hit.
 */
public final class AirConduction {

    private AirConduction() {
    }

    public static boolean inWater(LivingEntity e) {
        return e != null && e.isInWater();
    }

    /** Damage multiplier for a direct hit ({@code 1.4x} if the target is in water). */
    public static double directMult(LivingEntity target) {
        return inWater(target) ? StatFormulas.WATER_CONDUCT_MULT : 1.0;
    }

    /**
     * If {@code at} is in/over water, arc the charge to every other living thing
     * standing in water within {@link StatFormulas#WATER_CONDUCT_RADIUS}. Returns
     * the number of extra targets zapped (for XP).
     */
    public static int conduct(ServerLevel level, ServerPlayer owner, Vec3 at, int spellLevel, Set<Integer> exclude) {
        boolean wetSpot = !level.getFluidState(net.minecraft.core.BlockPos.containing(at)).isEmpty()
                && level.getFluidState(net.minecraft.core.BlockPos.containing(at)).is(net.minecraft.tags.FluidTags.WATER);
        double r = StatFormulas.WATER_CONDUCT_RADIUS;
        Set<Integer> hit = exclude == null ? new HashSet<>() : exclude;
        float dmg = (float) (StatFormulas.waterConductSplashDamage(spellLevel)
                * (owner != null ? StatFormulas.spellDamageMultiplier(RpgManager.stats(owner)) : 1.0));

        int zapped = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(r), LivingEntity::isAlive)) {
            if (le == owner || !inWater(le) || !hit.add(le.getId())) {
                continue;
            }
            if (!wetSpot && at.distanceToSqr(le.position()) > r * r) {
                continue;
            }
            le.hurt(owner != null ? owner.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), dmg);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 8, 0.25, 0.35, 0.25, 0.05);
            zapped++;
        }
        if (zapped > 0) {
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, r * 0.5, 0.3, r * 0.5, 0.15);
        }
        return zapped;
    }
}
