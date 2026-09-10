package net.robmc.rpgstats.magic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Water Breathing's effect: vanilla WATER_BREATHING (which times itself) plus a
 * temporary swim-speed boost that this class times and strips.
 */
public final class WaterBuff {

    private static final UUID SWIM_MOD_ID = UUID.fromString("aa7e5b00-0001-4a00-8000-0000000000d1");

    private static final Map<Integer, Long> swimUntil = new HashMap<>();

    private WaterBuff() {
    }

    public static void grant(LivingEntity target) {
        long now = target.level().getGameTime();
        target.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, StatFormulas.WATER_BREATHING_TICKS, 0, false, true, true));
        swimUntil.put(target.getId(), now + StatFormulas.WATER_BREATHING_TICKS);
        applySwim(target);
    }

    public static void clear(int entityId) {
        swimUntil.remove(entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (swimUntil.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        swimUntil.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            for (var level : server.getAllLevels()) {
                if (level.getEntity(entry.getKey()) instanceof LivingEntity le) {
                    removeSwim(le);
                    break;
                }
            }
            return true;
        });
    }

    private static void applySwim(LivingEntity e) {
        if (ForgeMod.SWIM_SPEED == null) {
            return;
        }
        AttributeInstance inst = e.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (inst != null && inst.getModifier(SWIM_MOD_ID) == null) {
            inst.addTransientModifier(new AttributeModifier(SWIM_MOD_ID, "water_breathing_swim",
                    StatFormulas.WATER_BREATHING_SWIM_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSwim(LivingEntity e) {
        if (ForgeMod.SWIM_SPEED == null) {
            return;
        }
        AttributeInstance inst = e.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (inst != null) {
            inst.removeModifier(SWIM_MOD_ID);
        }
    }
}
