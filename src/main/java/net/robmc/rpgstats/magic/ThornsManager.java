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
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thorns: a timed buff that bounces a slice of any melee or projectile hit back
 * at whoever landed it. The reflect itself is applied in {@code RpgEvents.onLivingHurt};
 * this class just holds the timer and the reflect fraction chosen at cast time.
 */
public final class ThornsManager {

    private static final Map<UUID, Entry> active = new HashMap<>();

    private static final class Entry {
        final double reflect;
        final long endTick;

        Entry(double reflect, long endTick) {
            this.reflect = reflect;
            this.endTick = endTick;
        }
    }

    private ThornsManager() {
    }

    public static void grant(ServerPlayer target, int schoolLevel) {
        long now = target.serverLevel().getGameTime();
        active.put(target.getUUID(), new Entry(StatFormulas.thornsReflect(schoolLevel), now + StatFormulas.THORNS_TICKS));
        target.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + 1.0, target.getZ(), 24, 0.4, 0.8, 0.4, 0.02);
        target.level().playSound(null, target.blockPosition(), SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.9f, 0.7f);
        target.displayClientMessage(Component.literal("A hedge of thorns wraps you.").withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** Fraction of an incoming hit that Thorns sends back, or 0 if the buff isn't up. */
    public static double reflectFraction(UUID id, long now) {
        Entry e = active.get(id);
        return e != null && now < e.endTick ? e.reflect : 0.0;
    }

    public static void clear(UUID id) {
        active.remove(id);
    }

    /** Small green burst where a reflect landed - lets both players read it. */
    public static void reflectFx(LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        active.entrySet().removeIf(entry -> {
            if (now < entry.getValue().endTick) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                p.displayClientMessage(Component.literal("Your thorns wither.").withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        });
    }
}
