package net.robmc.rpgstats.food;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.registry.RpgEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Since hunger no longer does anything on its own (see RpgEvents pinning the food
 * level), eating is what keeps natural Health/Stamina/Mana regen switched on:
 * every meal refreshes a 30-minute "Nourished" window (RpgManager.regenTick checks
 * this before it does anything), so you have to eat every so often or your pools
 * stop topping themselves up.
 */
public final class NourishmentManager {

    private static final Map<UUID, Long> nourishedUntil = new HashMap<>();

    private NourishmentManager() {
    }

    public static void refresh(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        boolean wasStarved = !isNourished(player.getUUID(), now);
        nourishedUntil.put(player.getUUID(), now + StatFormulas.NOURISHMENT_DURATION_TICKS);
        // the effect itself is just a HUD marker (top-right, with everyone else's buffs) -
        // the regen gate above is what actually matters and doesn't depend on this applying
        player.removeEffect(RpgEffects.NOURISHED.get());
        player.addEffect(new MobEffectInstance(RpgEffects.NOURISHED.get(),
                StatFormulas.NOURISHMENT_DURATION_TICKS, 0, false, false, true));
        if (wasStarved) {
            player.displayClientMessage(Component.literal("Nourished - your pools regenerate again.")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    public static boolean isNourished(UUID id, long now) {
        Long until = nourishedUntil.get(id);
        return until != null && now < until;
    }

    /** Ticks left before the player needs to eat again, or 0 if already starved. */
    public static int ticksLeft(UUID id, long now) {
        Long until = nourishedUntil.get(id);
        return until == null ? 0 : (int) Math.max(0, until - now);
    }

    public static void clear(UUID id) {
        nourishedUntil.remove(id);
    }

    /** Called every server tick from RpgEvents - just announces when someone runs out. */
    public static void tick(MinecraftServer server) {
        if (nourishedUntil.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        nourishedUntil.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                p.removeEffect(RpgEffects.NOURISHED.get());
                p.displayClientMessage(Component.literal("You're famished - eat something to keep regenerating.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        });
    }
}
