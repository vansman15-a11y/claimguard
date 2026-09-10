package net.robmc.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.combat.network.CombatNetwork;
import net.robmc.combat.network.ComboSyncPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-player melee combo. Each landed swing bumps the streak; when it would
 * reach {@link CombatConfig#COMBO_HITS_FOR_CRIT} that swing crits and the streak
 * rolls back to zero. Going quiet for {@link CombatConfig#COMBO_TIMEOUT_TICKS}
 * drops it.
 */
public final class ComboTracker {

    private static final class Combo {
        int streak;
        int targetId = -1;
        long lastHitTick;
        long lastSwingTick = -1;
        boolean critThisSwing;
    }

    private static final Map<UUID, Combo> combos = new HashMap<>();

    private ComboTracker() {
    }

    /**
     * Call once per swing that lands. The combo only builds while every hit lands
     * on the <i>same</i> {@code target}; hitting anything else (or nothing) starts
     * it over. Returns whether this swing crits.
     */
    public static boolean registerSwing(ServerPlayer player, LivingEntity target) {
        long now = player.serverLevel().getGameTime();
        Combo c = combos.computeIfAbsent(player.getUUID(), k -> new Combo());
        if (c.lastSwingTick == now) {
            return c.critThisSwing; // an arc cleave in the same tick as the primary hit
        }
        c.lastSwingTick = now;

        int tid = target != null ? target.getId() : -1;
        boolean sameTarget = tid != -1 && tid == c.targetId;
        boolean inTime = now - c.lastHitTick <= CombatConfig.COMBO_TIMEOUT_TICKS;
        if (!sameTarget || !inTime) {
            c.streak = 0;
        }
        c.targetId = tid;
        c.lastHitTick = now;
        c.streak++;
        c.critThisSwing = c.streak >= CombatConfig.COMBO_HITS_FOR_CRIT;
        if (c.critThisSwing) {
            c.streak = 0; // finisher spent - keep hitting the same target to build the next one
        }
        sync(player, c);
        return c.critThisSwing;
    }

    /** Whether the swing being resolved this tick is a crit (for the arc-hit damage too). */
    public static boolean isCritThisSwing(ServerPlayer player) {
        Combo c = combos.get(player.getUUID());
        return c != null && c.lastSwingTick == player.serverLevel().getGameTime() && c.critThisSwing;
    }

    public static void clear(UUID id) {
        combos.remove(id);
    }

    /** Called every server tick - expire stale combos and tell the client. */
    public static void tick(MinecraftServer server) {
        if (combos.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        combos.forEach((id, c) -> {
            if (c.streak > 0 && now - c.lastHitTick > CombatConfig.COMBO_TIMEOUT_TICKS) {
                c.streak = 0;
                c.targetId = -1;
                c.critThisSwing = false;
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    sync(p, c);
                }
            }
        });
    }

    private static void sync(ServerPlayer player, Combo c) {
        boolean armed = c.streak >= CombatConfig.COMBO_HITS_FOR_CRIT - 1;
        CombatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ComboSyncPacket(c.streak, armed));
    }
}
