package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plague: three frogs spat out by the beam swarm whoever it hit, poisoning them
 * for a few seconds before they keel over.
 */
public final class PlagueManager {

    private static final class Plague {
        final UUID caster;
        final int targetId;
        final String dimension;
        final int spellLevel;
        final long endTick;
        final List<Integer> frogIds = new ArrayList<>();
        long nextAttackTick;

        Plague(UUID caster, int targetId, String dimension, int spellLevel, long now) {
            this.caster = caster;
            this.targetId = targetId;
            this.dimension = dimension;
            this.spellLevel = spellLevel;
            this.endTick = now + StatFormulas.PLAGUE_FROG_TICKS;
            this.nextAttackTick = now + StatFormulas.PLAGUE_FROG_ATTACK_INTERVAL;
        }
    }

    private static final List<Plague> plagues = new ArrayList<>();

    private PlagueManager() {
    }

    public static void unleash(ServerPlayer caster, LivingEntity target, Vec3 at, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        long now = level.getGameTime();
        Plague plague = new Plague(caster.getUUID(), target.getId(), level.dimension().location().toString(), spellLevel, now);

        for (int i = 0; i < StatFormulas.PLAGUE_FROGS; i++) {
            Frog frog = EntityType.FROG.create(level);
            if (frog == null) {
                continue;
            }
            double a = i * (Math.PI * 2 / StatFormulas.PLAGUE_FROGS);
            frog.moveTo(at.x + Math.cos(a) * 1.2, at.y + 0.1, at.z + Math.sin(a) * 1.2, level.random.nextFloat() * 360f, 0f);
            frog.finalizeSpawn(level, level.getCurrentDifficultyAt(frog.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            frog.setPersistenceRequired();
            frog.setTarget(target);
            level.addFreshEntity(frog);
            plague.frogIds.add(frog.getId());
        }

        plagues.removeIf(p -> p.targetId == target.getId());
        plagues.add(plague);

        level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.5, at.z, 30, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, target.blockPosition(), SoundEvents.FROG_LONG_JUMP, SoundSource.PLAYERS, 1.2f, 0.8f);
        caster.displayClientMessage(Component.literal("The plague takes - frogs boil out of the blight.").withStyle(ChatFormatting.GREEN), true);
    }

    public static void clearTarget(int entityId) {
        plagues.removeIf(p -> p.targetId == entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (plagues.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        plagues.removeIf(plague -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(plague.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            boolean expired = now >= plague.endTick;
            LivingEntity target = level.getEntity(plague.targetId) instanceof LivingEntity le && le.isAlive() ? le : null;

            boolean attackTick = now >= plague.nextAttackTick;
            if (attackTick) {
                plague.nextAttackTick += StatFormulas.PLAGUE_FROG_ATTACK_INTERVAL;
            }
            float dmg = (float) StatFormulas.plagueFrogDamage(plague.spellLevel);
            ServerPlayer caster = server.getPlayerList().getPlayer(plague.caster);

            boolean anyFrog = false;
            for (int id : plague.frogIds) {
                if (!(level.getEntity(id) instanceof Frog frog) || !frog.isAlive()) {
                    continue;
                }
                anyFrog = true;
                if (expired) {
                    level.sendParticles(ParticleTypes.SNEEZE, frog.getX(), frog.getY() + 0.3, frog.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                    frog.discard();
                    continue;
                }
                if (target != null) {
                    frog.setTarget(target);
                    frog.getNavigation().moveTo(target, 1.4);
                    if (frog.distanceToSqr(target) < 4.0 && attackTick) {
                        target.hurt(caster != null
                                ? level.damageSources().indirectMagic(frog, caster)
                                : level.damageSources().mobAttack(frog), dmg);
                        target.addEffect(new MobEffectInstance(MobEffects.POISON, StatFormulas.PLAGUE_POISON_TICKS, 0, false, true, true));
                        level.sendParticles(ParticleTypes.ITEM_SLIME,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
            return expired || !anyFrog;
        });
    }
}
