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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Swarm of the Wild: for a few seconds every peaceful animal near the target
 * turns on it - chickens, rabbits, bees, bats, anything that would never normally
 * fight. The manager drives them so it works even on animals with no attack AI.
 */
public final class SwarmManager {

    private static final class Swarm {
        final UUID caster;
        final int targetId;
        final String dimension;
        final int spellLevel;
        final long endTick;
        final List<Integer> animalIds = new ArrayList<>();
        long nextAttackTick;

        Swarm(UUID caster, int targetId, String dimension, int spellLevel, long now) {
            this.caster = caster;
            this.targetId = targetId;
            this.dimension = dimension;
            this.spellLevel = spellLevel;
            this.endTick = now + StatFormulas.SWARM_TICKS;
            this.nextAttackTick = now + StatFormulas.SWARM_ATTACK_INTERVAL;
        }
    }

    private static final List<Swarm> swarms = new ArrayList<>();

    private SwarmManager() {
    }

    public static void create(ServerPlayer caster, LivingEntity target, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        long now = level.getGameTime();
        Swarm swarm = new Swarm(caster.getUUID(), target.getId(), level.dimension().location().toString(), spellLevel, now);

        AABB box = target.getBoundingBox().inflate(StatFormulas.SWARM_RECRUIT_RADIUS);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, SwarmManager::recruitable)) {
            if (le == target || le == caster) {
                continue;
            }
            swarm.animalIds.add(le.getId());
            if (le instanceof Mob mob) {
                mob.setTarget(target);
                mob.setLastHurtByMob(target);
            }
            if (swarm.animalIds.size() >= StatFormulas.SWARM_MAX_ANIMALS) {
                break;
            }
        }

        swarms.removeIf(s -> s.targetId == target.getId());
        swarms.add(swarm);

        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                target.getX(), target.getY() + 1.2, target.getZ(), 20, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 1.2f, 1.0f);
        caster.displayClientMessage(Component.literal("The wild turns on your quarry (" + swarm.animalIds.size() + " answer the call).")
                .withStyle(ChatFormatting.DARK_GREEN), true);
    }

    private static boolean recruitable(LivingEntity le) {
        return le.isAlive() && (le instanceof Animal || le instanceof AmbientCreature);
    }

    public static void clearTarget(int entityId) {
        swarms.removeIf(s -> s.targetId == entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (swarms.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        swarms.removeIf(swarm -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(swarm.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null || !(level.getEntity(swarm.targetId) instanceof LivingEntity target) || !target.isAlive()) {
                return true;
            }
            ServerPlayer caster = server.getPlayerList().getPlayer(swarm.caster);
            boolean attackTick = now >= swarm.nextAttackTick;
            if (attackTick) {
                swarm.nextAttackTick += StatFormulas.SWARM_ATTACK_INTERVAL;
            }
            float dmg = (float) StatFormulas.swarmAnimalDamage(swarm.spellLevel);

            for (int id : swarm.animalIds) {
                if (!(level.getEntity(id) instanceof LivingEntity animal) || !animal.isAlive()) {
                    continue;
                }
                if (animal instanceof Mob mob) {
                    mob.setTarget(target);
                    mob.getNavigation().moveTo(target, 1.35);
                    mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
                }
                double d = animal.distanceToSqr(target);
                if (d < 4.0) {
                    Vec3 push = target.position().subtract(animal.position());
                    if (push.lengthSqr() > 1.0e-4) {
                        push = push.normalize().scale(0.18);
                        target.push(push.x, 0.05, push.z);
                    }
                    if (attackTick) {
                        target.hurt(caster != null
                                ? level.damageSources().indirectMagic(animal, caster)
                                : level.damageSources().mobAttack(animal), dmg);
                        target.hurtMarked = true;
                        level.sendParticles(ParticleTypes.CRIT,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
            if (now >= swarm.endTick) {
                for (int id : swarm.animalIds) {
                    if (level.getEntity(id) instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.setLastHurtByMob(null);
                    }
                }
                return true;
            }
            return false;
        });
    }

    public static void clearAll() {
        swarms.clear();
    }
}
