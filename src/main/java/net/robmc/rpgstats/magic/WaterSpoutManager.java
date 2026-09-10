package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.item.Weapons;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Water Spout: a toggled beam. While it's on it hitscans from the caster's eye
 * every tick and only pays out when it's actually on a target - a tracking test.
 * Draining, and it drops itself the moment the caster casts something else,
 * swaps to a melee weapon, runs dry, or is interrupted.
 */
public final class WaterSpoutManager {

    private static final Map<UUID, Integer> active = new HashMap<>(); // caster -> spell level at toggle-on

    private WaterSpoutManager() {
    }

    public static boolean isActive(UUID casterId) {
        return active.containsKey(casterId);
    }

    public static void start(ServerPlayer caster, int spellLevel) {
        active.put(caster.getUUID(), spellLevel);
        caster.displayClientMessage(Component.literal("Water Spout on.").withStyle(ChatFormatting.AQUA), true);
    }

    public static void stop(ServerPlayer caster) {
        stop(caster.getUUID(), caster.getServer());
    }

    public static void stop(UUID casterId, MinecraftServer server) {
        if (active.remove(casterId) != null && server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(casterId);
            if (p != null) {
                p.displayClientMessage(Component.literal("Water Spout off.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    public static void clear(UUID id) {
        active.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        active.entrySet().removeIf(entry -> {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster == null || !caster.isAlive()) {
                return true;
            }
            if (!Weapons.isStaff(caster.getMainHandItem()) && !caster.getMainHandItem().isEmpty()) {
                stop(caster.getUUID(), server); // swapped to a weapon
                return true;
            }
            var s = RpgManager.stats(caster);
            if (s.getMana() < StatFormulas.WATER_SPOUT_MANA_PER_TICK) {
                stop(caster.getUUID(), server);
                return true;
            }
            s.setMana(s.getMana() - StatFormulas.WATER_SPOUT_MANA_PER_TICK);
            RpgManager.sync(caster);

            ServerLevel level = caster.serverLevel();
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getViewVector(1.0f);
            Vec3 far = eye.add(look.scale(StatFormulas.WATER_SPOUT_RANGE));
            HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : far;

            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, eye, end,
                    new AABB(eye, end).inflate(0.6),
                    e -> e instanceof LivingEntity && e != caster && e.isAlive() && !e.isSpectator());

            Vec3 tip = hit != null ? hit.getLocation() : end;
            int steps = (int) Math.max(4, eye.distanceTo(tip));
            for (int i = 0; i <= steps; i++) {
                Vec3 p = eye.lerp(tip, i / (double) steps);
                level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            if (caster.serverLevel().getGameTime() % 4 == 0) {
                level.playSound(null, caster.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 0.5f, 1.4f);
            }

            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                float dmg = (float) (StatFormulas.waterSpoutDamagePerTick(entry.getValue())
                        * StatFormulas.spellDamageMultiplier(s) * Afflictions.spellDamageMult(caster));
                target.hurt(caster.damageSources().indirectMagic(caster, caster), dmg);
                target.setDeltaMovement(target.getDeltaMovement().add(look.x * 0.04, 0.02, look.z * 0.04));
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.SPLASH,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
            }
            return false;
        });
    }
}
