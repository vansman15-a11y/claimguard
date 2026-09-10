package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Battle Hymn's team buff: a temporary +{@link StatFormulas#BATTLE_HYMN_STAT_BONUS}
 * to Strength / Vitality / Quickness for {@link StatFormulas#BATTLE_HYMN_DURATION_TICKS}.
 * The bonus rides on {@link net.robmc.rpgstats.PlayerStats#getLevel} so every pool and
 * combat multiplier picks it up for free; this class just tracks the timer.
 */
public final class HymnBuff {

    private static final Map<UUID, Long> until = new HashMap<>();

    private HymnBuff() {
    }

    public static void grant(ServerPlayer player) {
        RpgManager.stats(player).setHymnBonus(StatFormulas.BATTLE_HYMN_STAT_BONUS);
        until.put(player.getUUID(), player.serverLevel().getGameTime() + StatFormulas.BATTLE_HYMN_DURATION_TICKS);
        RpgManager.applyAttributes(player);
        RpgManager.sync(player);
        player.displayClientMessage(Component.literal("Battle Hymn empowers you!").withStyle(ChatFormatting.GOLD), true);
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
        until.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                RpgManager.stats(p).setHymnBonus(0);
                RpgManager.applyAttributes(p);
                RpgManager.sync(p);
                p.displayClientMessage(Component.literal("Battle Hymn fades.").withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        });
    }
}
