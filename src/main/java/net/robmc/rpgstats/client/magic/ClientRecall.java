package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;

/** Client mirror of the player's Recall channel, for the HUD. */
public final class ClientRecall {

    private static long endTick;
    private static int totalTicks;

    private ClientRecall() {
    }

    public static void onState(int ticksLeft) {
        if (ticksLeft <= 0) {
            endTick = 0;
            totalTicks = 0;
            return;
        }
        endTick = now() + ticksLeft;
        totalTicks = ticksLeft;
    }

    public static boolean isRecalling() {
        return totalTicks > 0 && endTick > now();
    }

    /** 0..1 channel progress. */
    public static float progress() {
        if (totalTicks <= 0) {
            return 0f;
        }
        float remaining = endTick - now() - Minecraft.getInstance().getFrameTime();
        float p = 1.0f - remaining / totalTicks;
        return Math.max(0f, Math.min(1f, p));
    }

    public static int secondsLeft() {
        return Math.max(0, (int) Math.ceil((endTick - now()) / 20.0));
    }

    private static long now() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
