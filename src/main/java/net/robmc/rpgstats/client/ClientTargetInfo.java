package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.robmc.claimguard.network.TargetInfoPacket;

import java.util.List;

/**
 * Client mirror of {@link TargetInfoPacket}: the debuffs on the entity the
 * player is looking at. Countdowns are extrapolated from the client clock so
 * they tick down smoothly between the ~5-tick server updates.
 */
public final class ClientTargetInfo {

    private static int entityId = -1;
    private static long syncedAtTick;
    private static List<TargetInfoPacket.Debuff> debuffs = List.of();

    private ClientTargetInfo() {
    }

    public static void accept(TargetInfoPacket p) {
        entityId = p.entityId;
        debuffs = p.debuffs;
        syncedAtTick = clientTick();
    }

    /** The debuffs for {@code id}, or an empty list if that isn't the entity the server last told us about. */
    public static List<TargetInfoPacket.Debuff> forEntity(int id) {
        return id == entityId ? debuffs : List.of();
    }

    /** Seconds left to show for a debuff, counting down from the last sync. */
    public static int secondsLeft(TargetInfoPacket.Debuff d) {
        long elapsed = clientTick() - syncedAtTick;
        int ticks = (int) (d.ticksLeft - elapsed);
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    private static long clientTick() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
