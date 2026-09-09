package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Which players' staves are lit up right now (they're casting), keyed by entity id. */
public final class ClientStaffGlow {

    private static final Map<Integer, Long> glowUntil = new HashMap<>();

    private ClientStaffGlow() {
    }

    public static void set(int entityId, int ticks) {
        long now = now();
        if (ticks <= 0) {
            glowUntil.remove(entityId);
        } else {
            glowUntil.put(entityId, now + ticks);
        }
    }

    public static boolean isGlowing(int entityId) {
        Long until = glowUntil.get(entityId);
        return until != null && now() < until;
    }

    private static long now() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }
}
