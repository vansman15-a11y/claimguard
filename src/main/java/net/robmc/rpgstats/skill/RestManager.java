package net.robmc.rpgstats.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.combat.CombatTracker;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgData;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Rest skill: hunker down and recover pools faster. A downtime action - it
 * refuses to start in combat and drops the moment you move, jump, or take a hit.
 */
public final class RestManager {

    private static final Map<UUID, Vec3> anchors = new HashMap<>();

    private RestManager() {
    }

    public static boolean isResting(UUID id) {
        return anchors.containsKey(id);
    }

    /** Toggle Rest on/off for this player (the slot key). */
    public static void toggle(ServerPlayer player) {
        if (anchors.containsKey(player.getUUID())) {
            stop(player, "You get up.");
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (CombatTracker.inCombat(player.getUUID(), now)) {
            msg(player, "You can't rest in combat.");
            return;
        }
        if (!player.onGround() || player.isInWater()) {
            msg(player, "You need solid ground to rest.");
            return;
        }
        anchors.put(player.getUUID(), player.position());
        player.setForcedPose(Pose.CROUCHING);
        player.displayClientMessage(
                Component.literal("Resting - move to get up.").withStyle(ChatFormatting.GREEN), true);
        RpgManager.sync(player); // pushes the resting flag to the HUD
    }

    public static void stop(ServerPlayer player, String message) {
        if (anchors.remove(player.getUUID()) != null) {
            player.setForcedPose(null);
            if (message != null) {
                msg(player, message);
            }
            RpgManager.sync(player);
        }
    }

    /** For logout / death - no player entity to clear a pose on. */
    public static void clear(UUID id) {
        anchors.remove(id);
    }

    /** Called every player tick from RpgEvents. */
    public static void tick(ServerPlayer player) {
        Vec3 anchor = anchors.get(player.getUUID());
        if (anchor == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        boolean broken = player.position().distanceToSqr(anchor) > StatFormulas.REST_BREAK_DISTANCE * StatFormulas.REST_BREAK_DISTANCE
                || player.isSprinting()
                || player.hurtTime > 0
                || !player.onGround()
                || CombatTracker.inCombat(player.getUUID(), now);
        if (broken) {
            stop(player, "You get up.");
            return;
        }

        player.setForcedPose(Pose.CROUCHING); // keep it applied against anything that clears it

        if (now % StatFormulas.REST_REGEN_INTERVAL_TICKS == 0) {
            PlayerStats s = RpgManager.stats(player);
            double mult = StatFormulas.restRegenMultiplier(s.getSkillLevel(Skill.REST));

            if (player.getHealth() < player.getMaxHealth()) {
                player.heal((float) (StatFormulas.HP_REGEN_PER_TICK * mult));
            }
            s.setStamina(s.getStamina() + StatFormulas.staminaRegen(s) * mult);
            s.setMana(s.getMana() + StatFormulas.manaRegen(s) * mult);

            RpgManager.addSkillXp(player, Skill.REST, StatFormulas.XP_REST_TICK);
            RpgData.get(player.server).markDirty();
            RpgManager.sync(player);
        }
    }

    private static void msg(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }
}
