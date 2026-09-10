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
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hex of Frailty: a curse that saps a target's damage and speed for a few
 * seconds (vanilla Weakness + Slowness). If the target dies while hexed, the
 * caster is healed - handled from {@code RpgEvents} on the death event.
 */
public final class HexManager {

    private static final class Hex {
        final UUID caster;
        final int schoolLevel;
        final long endTick;

        Hex(UUID caster, int schoolLevel, long endTick) {
            this.caster = caster;
            this.schoolLevel = schoolLevel;
            this.endTick = endTick;
        }
    }

    private static final Map<Integer, Hex> hexed = new HashMap<>();

    private HexManager() {
    }

    public static void apply(ServerPlayer caster, LivingEntity target, int schoolLevel) {
        long now = target.level().getGameTime();
        hexed.put(target.getId(), new Hex(caster.getUUID(), schoolLevel, now + StatFormulas.HEX_TICKS));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, StatFormulas.HEX_TICKS, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, StatFormulas.HEX_TICKS, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, StatFormulas.HEX_TICKS, 1, false, true, true));
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.9f, 0.7f);
        }
        caster.displayClientMessage(Component.literal("A hex of frailty settles on your foe.").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    public static boolean isHexed(int entityId) {
        return hexed.containsKey(entityId);
    }

    /** The target died: pay the caster their kill heal if the hex was theirs and still up. */
    public static void onDeath(MinecraftServer server, LivingEntity dead) {
        Hex h = hexed.remove(dead.getId());
        if (h == null || dead.level().getGameTime() >= h.endTick) {
            return;
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(h.caster);
        if (caster == null || !caster.isAlive()) {
            return;
        }
        caster.heal((float) StatFormulas.hexKillHeal(h.schoolLevel));
        caster.serverLevel().sendParticles(ParticleTypes.HEART,
                caster.getX(), caster.getY() + 1.2, caster.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
        caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.8f);
        caster.displayClientMessage(Component.literal("The hex claims its due - you are restored.").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    public static void clearTarget(int entityId) {
        hexed.remove(entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (hexed.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        hexed.entrySet().removeIf(e -> now >= e.getValue().endTick);
    }
}
