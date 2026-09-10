package net.robmc.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The wide forward cleave: one swing hits everything in a fan in front of you. */
public final class MeleeArc {

    /** True while we're applying a cleave hit, so the crit hook doesn't double-multiply it. */
    private static final ThreadLocal<Boolean> APPLYING_ARC = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MeleeArc() {
    }

    public static boolean isApplyingArcHit() {
        return APPLYING_ARC.get();
    }

    /**
     * Resolve a swing: cleave everything in the fan in front of {@code player}
     * (skipping {@code primary}, which vanilla already hit). If anything is hit -
     * or {@code primaryLanded} is true - the combo advances and the whole swing
     * (arc + the vanilla primary, via {@link ComboTracker#isCritThisSwing}) may crit.
     *
     * @return how many extra targets were cleaved.
     */
    public static int sweep(ServerPlayer player, LivingEntity primary, boolean primaryLanded) {
        float charge = player.getAttackStrengthScale(0.5f);
        if (charge < CombatConfig.ARC_MIN_CHARGE) {
            return 0;
        }
        List<LivingEntity> targets = arcTargets(player, primary);
        if (!primaryLanded && targets.isEmpty()) {
            return 0;
        }

        // the combo tracks one target: the one you aimed at, or - for a swing at air -
        // whatever is most in front of you.
        LivingEntity comboTarget = primary != null ? primary : mostCentred(player, targets);
        boolean crit = ComboTracker.registerSwing(player, comboTarget);
        ServerLevel level = player.serverLevel();
        ItemStack weapon = player.getMainHandItem();

        int hits = 0;
        for (LivingEntity le : targets) {
            float dmg = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            dmg += EnchantmentHelper.getDamageBonus(weapon, le.getMobType());
            dmg *= (float) (CombatConfig.ARC_SECONDARY_MULT * (0.2f + 0.8f * charge));
            if (crit) {
                dmg *= (float) CombatConfig.COMBO_CRIT_MULT;
            }
            boolean landed;
            APPLYING_ARC.set(Boolean.TRUE);
            try {
                landed = le.hurt(player.damageSources().playerAttack(player), dmg);
            } finally {
                APPLYING_ARC.set(Boolean.FALSE);
            }
            if (landed) {
                EnchantmentHelper.doPostHurtEffects(le, player);
                EnchantmentHelper.doPostDamageEffects(player, le);
                float yaw = player.getYRot() * ((float) Math.PI / 180F);
                le.knockback(0.3F, Math.sin(yaw), -Math.cos(yaw));
                if (crit) {
                    critFx(player, le);
                }
                hits++;
            }
        }

        if (hits > 0) {
            weapon.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                eye.x + look.x, eye.y - 0.2 + look.y, eye.z + look.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 0.95f);
        return hits;
    }

    /** Of the cleaved targets, the one closest to dead-centre of the player's aim. */
    private static LivingEntity mostCentred(ServerPlayer player, List<LivingEntity> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        LivingEntity best = null;
        double bestDot = -1;
        for (LivingEntity le : targets) {
            Vec3 to = le.getBoundingBox().getCenter().subtract(eye).normalize();
            double d = look.dot(to);
            if (d > bestDot) {
                bestDot = d;
                best = le;
            }
        }
        return best;
    }

    /** The valid living targets inside the cleave fan (does not touch them). */
    private static List<LivingEntity> arcTargets(ServerPlayer player, LivingEntity primary) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).normalize();
        double range = CombatConfig.ARC_RANGE;
        double cos = Math.cos(Math.toRadians(CombatConfig.ARC_HALF_ANGLE_DEG));

        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, eye).inflate(range + 1.0))) {
            if (le == player || le == primary || !le.isAlive() || le.isSpectator() || player.isAlliedTo(le)) {
                continue;
            }
            Vec3 toTarget = le.getBoundingBox().getCenter().subtract(eye);
            double dist = toTarget.length();
            if (dist > range || dist < 1.0e-3) {
                continue;
            }
            if (look.dot(toTarget.scale(1.0 / dist)) < cos) {
                continue;
            }
            if (!player.hasLineOfSight(le)) {
                continue;
            }
            out.add(le);
            if (out.size() >= CombatConfig.ARC_MAX_TARGETS) {
                break;
            }
        }
        return out;
    }

    /** Visual + sound feedback when the combo finisher lands. */
    public static void critFx(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        double y = target.getY() + target.getBbHeight() * 0.6;
        level.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 22, 0.3, 0.3, 0.3, 0.45);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), y, target.getZ(), 12, 0.25, 0.25, 0.25, 0.2);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.85f);
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, false, false, false));
        }
    }
}
