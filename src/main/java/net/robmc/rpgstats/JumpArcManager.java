package net.robmc.rpgstats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Replaces the vanilla jump's near-instant rise-and-drop with one that climbs a
 * little higher, hangs briefly at the apex, and floats back down slower - a real
 * window to fire off a spell or a shot mid-air instead of vanilla's
 * blink-and-you-missed-it arc. Launch-speed momentum is held for the whole arc
 * instead of bleeding away to vanilla's air drag, and a second jump is available
 * mid-arc on a cooldown to extend the reach further.
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
        final long startTick;
        double launchSpeedSq;
        boolean doubleJumped = false;

        Arc(double startY, long startTick, double launchSpeedSq) {
            this.startY = startY;
            this.startTick = startTick;
            this.launchSpeedSq = launchSpeedSq;
        }
    }

    private static final Map<UUID, Arc> arcs = new HashMap<>();
    private static final Map<UUID, Long> doubleJumpCdEnd = new HashMap<>();

    private JumpArcManager() {
    }

    /** Called from RpgEvents.onJump right as a normal jump starts (after the exhaustion check). */
    public static void start(ServerPlayer player) {
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return; // those already own vertical physics - don't fight them
        }
        Vec3 dm = player.getDeltaMovement();
        long now = player.serverLevel().getGameTime();
        player.setDeltaMovement(dm.x, dm.y * StatFormulas.JUMP_HEIGHT_MULT, dm.z);
        player.hurtMarked = true; // force the boosted velocity to actually reach the client
        arcs.put(player.getUUID(), new Arc(player.getY(), now, dm.x * dm.x + dm.z * dm.z));
    }

    /**
     * A second jump mid-arc - entirely opt-in, never automatic: only fires when the player
     * presses jump again themselves while still airborne, at most once per active jump, and
     * only once more per {@link StatFormulas#DOUBLE_JUMP_COOLDOWN_TICKS} after that (being off
     * cooldown does not use it on its own - it still takes another press on the next jump).
     * Restarts a fresh, ordinary jump arc from wherever the player currently is - two regular
     * jumps stacked back to back, not one bigger jump - and adds a push along whatever direction
     * they're already moving to extend the reach.
     */
    public static void tryDoubleJump(ServerPlayer player) {
        Arc arc = arcs.get(player.getUUID());
        if (arc == null || arc.doubleJumped) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        // guards against the same press that started the jump being mistaken for the double-jump
        // press, if the client's "am I airborne yet" check lands the same tick the jump launches
        if (now - arc.startTick < StatFormulas.DOUBLE_JUMP_MIN_DELAY_TICKS) {
            return;
        }
        long cdEnd = doubleJumpCdEnd.getOrDefault(player.getUUID(), 0L);
        if (now < cdEnd) {
            return;
        }
        arc.doubleJumped = true;
        arc.phase = Phase.RISING;
        arc.fallTicks = 0;
        doubleJumpCdEnd.put(player.getUUID(), now + StatFormulas.DOUBLE_JUMP_COOLDOWN_TICKS);

        Vec3 dm = player.getDeltaMovement();
        double speedSq = dm.x * dm.x + dm.z * dm.z;
        double hx = dm.x;
        double hz = dm.z;
        if (speedSq > 1.0E-6) {
            double invLen = StatFormulas.DOUBLE_JUMP_FORWARD_BOOST / Math.sqrt(speedSq);
            hx += dm.x * invLen;
            hz += dm.z * invLen;
        }
        arc.launchSpeedSq = hx * hx + hz * hz;
        player.setDeltaMovement(hx, StatFormulas.DOUBLE_JUMP_VELOCITY, hz);
        player.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    public static void clear(UUID id) {
        arcs.remove(id);
        doubleJumpCdEnd.remove(id);
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
        double hx = dm.x;
        double hz = dm.z;
        double targetSpeedSq = arc.launchSpeedSq * StatFormulas.JUMP_MOMENTUM_PRESERVE_FRACTION;
        double speedSq = hx * hx + hz * hz;
        // vanilla air drag would otherwise bleed off a sprint jump's speed over this much longer
        // airtime - top the current horizontal speed back up to (a fraction of) launch speed,
        // in whatever direction the player's currently steering, instead of freezing it outright
        if (speedSq < targetSpeedSq && speedSq > 1.0E-6) {
            double scale = Math.sqrt(targetSpeedSq / speedSq);
            hx *= scale;
            hz *= scale;
        }

        player.setDeltaMovement(hx, y, hz);
        player.hurtMarked = true; // force the corrected velocity to actually reach the client every tick
        player.fallDistance = 0.0f; // a controlled ascent/descent, not a fall - no fall damage from it
    }
}
