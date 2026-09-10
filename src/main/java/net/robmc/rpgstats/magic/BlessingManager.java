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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blessing of Protection: an Absorption shield on an ally. When it's chewed
 * away it bursts into healing light - everyone friendly near the shielded player
 * (including them) is healed. If it just times out, nothing happens.
 */
public final class BlessingManager {

    private static final class Blessing {
        final UUID caster;
        final int schoolLevel;
        final long endTick;

        Blessing(UUID caster, int schoolLevel, long endTick) {
            this.caster = caster;
            this.schoolLevel = schoolLevel;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, Blessing> active = new HashMap<>();

    private BlessingManager() {
    }

    public static void grant(ServerPlayer caster, ServerPlayer target, int schoolLevel) {
        long now = target.serverLevel().getGameTime();
        active.put(target.getUUID(), new Blessing(caster.getUUID(), schoolLevel, now + StatFormulas.BLESSING_MAX_TICKS));
        target.removeEffect(MobEffects.ABSORPTION);
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, StatFormulas.BLESSING_MAX_TICKS,
                StatFormulas.BLESSING_ABSORB_AMPLIFIER, false, false, true));
        target.serverLevel().sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + 1.0, target.getZ(), 36, 0.5, 0.9, 0.5, 0.03);
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f);
        target.displayClientMessage(Component.literal("A blessing shields you.").withStyle(ChatFormatting.YELLOW), true);
        if (target != caster) {
            caster.displayClientMessage(Component.literal("Blessing of Protection placed on " + target.getGameProfile().getName() + ".")
                    .withStyle(ChatFormatting.YELLOW), true);
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
        long now = server.overworld().getGameTime();
        active.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !p.isAlive()) {
                return true;
            }
            if (p.getAbsorptionAmount() <= 0.5f) {
                burst(server, p, entry.getValue());
                return true;
            }
            if (now >= entry.getValue().endTick) {
                p.removeEffect(MobEffects.ABSORPTION);
                p.displayClientMessage(Component.literal("Your blessing fades.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            return false;
        });
    }

    private static void burst(MinecraftServer server, ServerPlayer shielded, Blessing b) {
        ServerLevel level = shielded.serverLevel();
        Vec3 at = shielded.position();
        float heal = (float) StatFormulas.blessingBurstHeal(b.schoolLevel);

        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(StatFormulas.BLESSING_BURST_RADIUS), LivingEntity::isAlive)) {
            boolean friendly = le == shielded
                    || (le instanceof Player other && ClanActions.areFriendly(server, b.caster, other.getUUID()));
            if (friendly) {
                le.heal(heal);
                if (le instanceof ServerPlayer sp && sp != shielded) {
                    sp.displayClientMessage(Component.literal("Blessing light mends you.").withStyle(ChatFormatting.YELLOW), true);
                }
            }
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 80,
                StatFormulas.BLESSING_BURST_RADIUS * 0.4, 0.8, StatFormulas.BLESSING_BURST_RADIUS * 0.4, 0.2);
        level.sendParticles(ParticleTypes.HEART, at.x, at.y + 1.2, at.z, 8, 1.2, 0.6, 1.2, 0.0);
        level.playSound(null, shielded.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.5f);
        shielded.displayClientMessage(Component.literal("Your blessing shatters into healing light!").withStyle(ChatFormatting.YELLOW), true);
    }
}
