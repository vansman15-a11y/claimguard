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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ScreenFlashPacket;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aegis of Stars: a vanilla Absorption shield plus a Glowing marker. If the
 * shield is chewed down to nothing it bursts - light damage and a blink of
 * blindness on nearby enemies. If it just times out, nothing happens.
 */
public final class AegisManager {

    private static final class Aegis {
        final int spellLevel;
        final long endTick;

        Aegis(int spellLevel, long endTick) {
            this.spellLevel = spellLevel;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, Aegis> active = new HashMap<>();

    private AegisManager() {
    }

    public static void grant(ServerPlayer player, int spellLevel) {
        long now = player.serverLevel().getGameTime();
        active.put(player.getUUID(), new Aegis(spellLevel, now + StatFormulas.AEGIS_MAX_TICKS));
        player.removeEffect(MobEffects.ABSORPTION);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, StatFormulas.AEGIS_MAX_TICKS,
                StatFormulas.AEGIS_ABSORB_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, StatFormulas.AEGIS_MAX_TICKS, 0, false, false, true));
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.03);
        player.displayClientMessage(Component.literal("An aegis of starlight wraps you.").withStyle(ChatFormatting.AQUA), true);
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
                burst(p, entry.getValue());
                p.removeEffect(MobEffects.GLOWING);
                return true;
            }
            if (now >= entry.getValue().endTick) {
                p.removeEffect(MobEffects.GLOWING);
                p.removeEffect(MobEffects.ABSORPTION);
                p.displayClientMessage(Component.literal("Your aegis fades.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            return false;
        });
    }

    private static void burst(ServerPlayer caster, Aegis a) {
        ServerLevel level = caster.serverLevel();
        Vec3 at = caster.position();
        double r = StatFormulas.AEGIS_BURST_RADIUS;
        float dmg = (float) (StatFormulas.aegisBurstDamage(a.spellLevel)
                * StatFormulas.spellDamageMultiplier(RpgManager.stats(caster)) * Afflictions.spellDamageMult(caster));

        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(r), LivingEntity::isAlive)) {
            if (le == caster) {
                continue;
            }
            boolean foe = le instanceof Enemy
                    || (le instanceof Player p && !ClanActions.areFriendly(caster.server, caster.getUUID(), p.getUUID()));
            if (!foe) {
                continue;
            }
            le.hurt(caster.damageSources().indirectMagic(caster, caster), dmg);
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, StatFormulas.AEGIS_BURST_BLIND_TICKS, 0, false, false, true));
            if (le instanceof ServerPlayer sp) {
                ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new ScreenFlashPacket(StatFormulas.AEGIS_BURST_BLIND_TICKS));
            }
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 80, r * 0.5, 0.8, r * 0.5, 0.25);
        level.playSound(null, caster.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 1.4f);
        caster.displayClientMessage(Component.literal("Your aegis shatters in a burst of light!").withStyle(ChatFormatting.AQUA), true);
    }
}
