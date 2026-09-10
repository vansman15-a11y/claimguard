package net.robmc.combat.client;

import net.minecraft.client.Minecraft;
import net.robmc.combat.network.ComboSyncPacket;

/** Client mirror of the melee combo state, for the crosshair indicator. */
public final class ClientCombo {

    private static int streak;
    private static boolean armed;
    private static long changedAtTick;

    private ClientCombo() {
    }

    public static void accept(ComboSyncPacket p) {
        streak = p.streak;
        armed = p.armed;
        changedAtTick = clientTick();
    }

    public static int streak() {
        return streak;
    }

    public static boolean armed() {
        return armed;
    }

    /** Ticks since the streak last changed - drives the flash on the finisher pip. */
    public static long sinceChange() {
        return clientTick() - changedAtTick;
    }

    private static long clientTick() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
