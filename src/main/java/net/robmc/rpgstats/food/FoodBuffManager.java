package net.robmc.rpgstats.food;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A meal's Health/Stamina/Mana gain, paid out a little each second instead of all
 * at once. Doesn't stack with itself - eating a second, weaker food while one is
 * still ticking is wasted (the better meal keeps going); eating a stronger one
 * replaces it outright and starts the payout over.
 */
public final class FoodBuffManager {

    private static final class Grant {
        final double totalGain;   // what this meal is worth - used to compare tiers
        final double perPulse;
        int pulsesLeft;
        long nextPulseTick;

        Grant(double totalGain, long now) {
            this.totalGain = totalGain;
            int pulses = StatFormulas.FOOD_TICK_TOTAL_TICKS / StatFormulas.FOOD_TICK_INTERVAL;
            this.perPulse = totalGain / pulses;
            this.pulsesLeft = pulses;
            this.nextPulseTick = now + StatFormulas.FOOD_TICK_INTERVAL;
        }
    }

    private static final Map<UUID, Grant> grants = new HashMap<>();

    private FoodBuffManager() {
    }

    /** Start (or ignore, if a stronger meal is already digesting) a gradual gain. */
    public static void grant(ServerPlayer player, double totalGain) {
        long now = player.serverLevel().getGameTime();
        Grant existing = grants.get(player.getUUID());
        if (existing != null && existing.totalGain >= totalGain) {
            return; // already digesting something at least this good - don't downgrade it
        }
        grants.put(player.getUUID(), new Grant(totalGain, now));
    }

    public static void clear(UUID id) {
        grants.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (grants.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        grants.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                return true;
            }
            Grant g = entry.getValue();
            if (now < g.nextPulseTick) {
                return false;
            }
            g.nextPulseTick += StatFormulas.FOOD_TICK_INTERVAL;
            g.pulsesLeft--;

            PlayerStats s = RpgManager.stats(player);
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal((float) g.perPulse);
            }
            s.setStamina(s.getStamina() + g.perPulse);
            s.setMana(s.getMana() + g.perPulse);
            RpgManager.sync(player);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);

            return g.pulsesLeft <= 0;
        });
    }
}
