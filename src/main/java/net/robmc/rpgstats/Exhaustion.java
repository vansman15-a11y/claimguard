package net.robmc.rpgstats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Exhausted" - what happens when your Stamina bottoms out. While exhausted you
 * can't attack, draw a bow, raise a shield, or jump, and you move at a crawl.
 * Hysteresis: you only recover once Stamina climbs back to a fraction of max.
 */
public final class Exhaustion {

    private static final Set<UUID> exhausted = new HashSet<>();

    private Exhaustion() {
    }

    public static boolean is(UUID id) {
        return exhausted.contains(id);
    }

    public static boolean is(Player player) {
        return exhausted.contains(player.getUUID());
    }

    /** Called each tick from RpgEvents with the player's current stamina. */
    public static void update(ServerPlayer player, double stamina, double maxStamina) {
        boolean was = exhausted.contains(player.getUUID());
        if (!was && stamina <= 0.0) {
            exhausted.add(player.getUUID());
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    MobEffectInstance.INFINITE_DURATION, StatFormulas.EXHAUSTION_SLOWNESS_AMPLIFIER, false, false, true));
            player.displayClientMessage(
                    Component.literal("Exhausted!").withStyle(ChatFormatting.RED), true);
        } else if (was && stamina >= maxStamina * StatFormulas.EXHAUSTION_RECOVER_FRACTION) {
            exhausted.remove(player.getUUID());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    public static void clear(UUID id) {
        exhausted.remove(id);
    }
}
