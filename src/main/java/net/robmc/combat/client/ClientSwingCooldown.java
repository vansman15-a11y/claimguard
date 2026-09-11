package net.robmc.combat.client;

import net.minecraft.client.Minecraft;
import net.robmc.combat.network.SwingCooldownPacket;

/**
 * Client mirror of the melee swing's hard global cooldown (see CombatEvents.tryStartSwing).
 * rpgstats' HUD reads this to show the swing readout under Bar 1 whenever a sword swing is
 * more recent than the last spell cast - this class itself has no idea rpgstats exists.
 */
public final class ClientSwingCooldown {

    public static final int READY_FLASH_TICKS = 7;

    private static String weaponId;
    private static long cdStart = Long.MIN_VALUE;
    private static int cdDuration;

    private ClientSwingCooldown() {
    }

    public static void accept(SwingCooldownPacket p) {
        weaponId = p.weaponId.isEmpty() ? null : p.weaponId;
        cdStart = clientTick();
        cdDuration = p.durationTicks;
    }

    /** The registry id of the last weapon swung, or null. */
    public static String weaponId() {
        return weaponId;
    }

    /** The client tick the most recent swing started - compare against another action's timestamp to see which is newer. */
    public static long lastSwingTick() {
        return cdStart;
    }

    /** 0..1 of the swing cooldown elapsed (1 = ready). */
    public static float progress() {
        if (cdDuration <= 0) {
            return 1.0f;
        }
        float p = (clientTick() - cdStart + Minecraft.getInstance().getFrameTime()) / cdDuration;
        return Math.max(0f, Math.min(1f, p));
    }

    /** Seconds left on the swing cooldown (rounded up), or 0 once it's ready. */
    public static double secondsLeft() {
        if (cdDuration <= 0) {
            return 0;
        }
        long remainingTicks = cdDuration - (clientTick() - cdStart);
        return remainingTicks > 0 ? remainingTicks / 20.0 : 0;
    }

    /** 1..0 fading pulse for the first READY_FLASH_TICKS once the cooldown ends; 0 otherwise. */
    public static float readyFlash() {
        if (cdDuration <= 0) {
            return 0f;
        }
        long endTick = cdStart + cdDuration;
        float since = clientTick() + Minecraft.getInstance().getFrameTime() - endTick;
        if (since < 0f || since >= READY_FLASH_TICKS) {
            return 0f;
        }
        return 1.0f - since / READY_FLASH_TICKS;
    }

    private static long clientTick() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
