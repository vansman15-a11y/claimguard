package net.robmc.rpgstats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Replaces the vanilla jump's near-instant rise-and-drop with one that climbs a
 * little higher, hangs for a split second at the apex, and floats back down
 * slower - a real window to fire off a spell or a shot mid-air instead of
 * vanilla's blink-and-you-missed-it arc.
 *
 * Fully custom gravity while an arc is active: every player tick this
 * overwrites {@code deltaMovement.y} with our own curve instead of letting
 * vanilla's gravity apply, so the three phases (rise / hang / fall) are
 * exactly what we say they are. Two safety valves hand control straight back
 * to vanilla (and let fall damage resume normally) if the player wanders off
 * a ledge mid-float instead of landing where they jumped from.
 */
public final class JumpArcManager {

    private enum Phase { RISING, HANG, FALLING }

    private static final class Arc {
        Phase phase = Phase.RISING;
        int hangTicksLeft;
        int fallTicks;
        final double startY;

        Arc(double startY) {
            this.startY = startY;
        }
    }

    private static final Map<UUID, Arc> arcs = new HashMap<>();

    private JumpArcManager() {
    }

    /** Called from RpgEvents.onJump right as a normal jump starts (after the exhaustion check). */
    public static void start(ServerPlayer player) {
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return; // those already own vertical physics - don't fight them
        }
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, dm.y * StatFormulas.JUMP_HEIGHT_MULT, dm.z);
        player.hurtMarked = true; // force the boosted velocity to actually reach the client
        arcs.put(player.getUUID(), new Arc(player.getY()));
    }

    public static void clear(UUID id) {
        arcs.remove(id);
    }

    /** Called every player tick (Phase.END) from RpgEvents.onPlayerTick. */
    public static void tick(ServerPlayer player) {
        Arc arc = arcs.get(player.getUUID());
        if (arc == null) {
            return;
        }
        if (player.onGround() || player.isInWater() || player.isInLava()
                || player.getAbilities().flying || player.isFallFlying()
                || player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) {
            arcs.remove(player.getUUID());
            return;
        }

        double y = player.getDeltaMovement().y;
        switch (arc.phase) {
            case RISING -> {
                double next = y - StatFormulas.JUMP_RISE_GRAVITY;
                if (next <= 0) {
                    arc.phase = Phase.HANG;
                    arc.hangTicksLeft = StatFormulas.JUMP_HANG_TICKS;
                    y = 0.0;
                } else {
                    y = next;
                }
            }
            case HANG -> {
                y = 0.0;
                if (--arc.hangTicksLeft <= 0) {
                    arc.phase = Phase.FALLING;
                }
            }
            case FALLING -> {
                y = Math.max(y - StatFormulas.JUMP_FALL_GRAVITY, -StatFormulas.JUMP_FALL_TERMINAL);
                arc.fallTicks++;
                // wandered off a ledge instead of landing where they jumped - hand back to vanilla (and fall damage)
                if (arc.fallTicks > StatFormulas.JUMP_FALL_MAX_TICKS
                        || player.getY() < arc.startY - StatFormulas.JUMP_MAX_DROP) {
                    arcs.remove(player.getUUID());
                    return;
                }
            }
        }

        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, y, dm.z);
        player.hurtMarked = true; // force the corrected velocity to actually reach the client every tick
        player.fallDistance = 0.0f; // a controlled ascent/descent, not a fall - no fall damage from it
    }
}
