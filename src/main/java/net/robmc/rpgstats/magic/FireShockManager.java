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
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fire Shock: a burning damage-over-time. When the last tick lands, the caster
 * is healed for a little.
 */
public final class FireShockManager {

    private static final class Shock {
        final UUID caster;
        final int targetId;
        final String dimension;
        final int spellLevel;
        final float perTick;
        final long endTick;
        long nextTick;

        Shock(UUID caster, int targetId, String dimension, int spellLevel, float perTick, long now) {
            this.caster = caster;
            this.targetId = targetId;
            this.dimension = dimension;
            this.spellLevel = spellLevel;
            this.perTick = perTick;
            this.endTick = now + StatFormulas.FIRE_SHOCK_TICKS;
            this.nextTick = now + StatFormulas.FIRE_SHOCK_INTERVAL;
        }
    }

    private static final List<Shock> shocks = new ArrayList<>();

    private FireShockManager() {
    }

    public static void start(ServerPlayer caster, LivingEntity target, int spellLevel, float perTick) {
        ServerLevel level = caster.serverLevel();
        shocks.removeIf(s -> s.targetId == target.getId());
        shocks.add(new Shock(caster.getUUID(), target.getId(), level.dimension().location().toString(),
                spellLevel, perTick, level.getGameTime()));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    public static void clearTarget(int entityId) {
        shocks.removeIf(s -> s.targetId == entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (shocks.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        shocks.removeIf(sh -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(sh.dimension)) {
                    level = sl;
                    break;
                }
            }
            ServerPlayer caster = server.getPlayerList().getPlayer(sh.caster);
            if (level == null || !(level.getEntity(sh.targetId) instanceof LivingEntity target) || !target.isAlive()) {
                return true;
            }
            if (now < sh.nextTick) {
                return false;
            }
            sh.nextTick += StatFormulas.FIRE_SHOCK_INTERVAL;
            target.hurt(caster != null
                    ? level.damageSources().indirectMagic(caster, caster)
                    : level.damageSources().magic(), sh.perTick);
            level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.25, 0.35, 0.25, 0.02);

            if (now >= sh.endTick) {
                if (caster != null && caster.isAlive()) {
                    caster.heal((float) StatFormulas.fireShockHeal(sh.spellLevel));
                    caster.serverLevel().sendParticles(ParticleTypes.HEART,
                            caster.getX(), caster.getY() + 1.2, caster.getZ(), 5, 0.3, 0.4, 0.3, 0.0);
                    caster.displayClientMessage(Component.literal("Fire Shock burns out and warms you.").withStyle(ChatFormatting.GOLD), true);
                }
                return true;
            }
            return false;
        });
    }
}
