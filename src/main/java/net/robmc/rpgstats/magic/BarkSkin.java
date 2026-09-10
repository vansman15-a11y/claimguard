package net.robmc.rpgstats.magic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bark Skin: a timed buff that shaves {@link net.robmc.rpgstats.StatFormulas#BARK_SKIN_REDUCTION}
 * off incoming melee and projectile damage. The reduction itself is applied in
 * {@code RpgEvents.onLivingHurt}; this class just holds the timer.
 */
public final class BarkSkin {

    private static final Map<UUID, Long> until = new HashMap<>();

    private BarkSkin() {
    }

    public static void grant(ServerPlayer player, int durationTicks) {
        until.put(player.getUUID(), player.serverLevel().getGameTime() + durationTicks);
    }

    public static boolean isProtected(UUID id, long now) {
        Long end = until.get(id);
        return end != null && now < end;
    }

    public static void clear(UUID id) {
        until.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (until.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        until.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("Bark Skin wears off.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            return true;
        });
    }
}
