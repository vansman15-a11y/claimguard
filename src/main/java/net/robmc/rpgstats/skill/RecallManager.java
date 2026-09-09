package net.robmc.rpgstats.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.bind.BindEvents;
import net.robmc.claimguard.bind.BindManager;
import net.robmc.claimguard.combat.CombatTracker;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.RecallStatePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Recall skill: a 1-minute channel that teleports you to the bindstone your
 * respawn is set to. Any hit or entering combat breaks it. You can move during
 * the channel.
 */
public final class RecallManager {

    public static final int CHANNEL_TICKS = 1200; // 60 s

    private static final Map<UUID, Long> endTick = new HashMap<>();

    private RecallManager() {
    }

    public static boolean isRecalling(UUID id) {
        return endTick.containsKey(id);
    }

    /** Start a recall channel, or cancel one already running. */
    public static void toggle(ServerPlayer player) {
        if (endTick.remove(player.getUUID()) != null) {
            sendState(player, 0);
            msg(player, "Recall cancelled.");
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (CombatTracker.inCombat(player.getUUID(), now)) {
            msg(player, "You can't recall in combat.");
            return;
        }
        if (BindManager.get(player.server).getBind(player.getUUID()).isEmpty()) {
            msg(player, "You have no bindstone to recall to.");
            return;
        }
        endTick.put(player.getUUID(), now + CHANNEL_TICKS);
        sendState(player, CHANNEL_TICKS);
        player.displayClientMessage(
                Component.literal("Recalling to your bindstone... (1 min)").withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    public static void cancel(ServerPlayer player, String message) {
        if (endTick.remove(player.getUUID()) != null) {
            sendState(player, 0);
            if (message != null) {
                msg(player, message);
            }
        }
    }

    public static void clear(UUID id) {
        endTick.remove(id);
    }

    /** Called every player tick from RpgEvents. */
    public static void tick(ServerPlayer player) {
        Long end = endTick.get(player.getUUID());
        if (end == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();

        if (!player.isAlive() || player.hurtTime > 0 || CombatTracker.inCombat(player.getUUID(), now)) {
            cancel(player, "Recall interrupted!");
            return;
        }
        Optional<BindManager.Bind> bind = BindManager.get(player.server).getBind(player.getUUID());
        if (bind.isEmpty()) {
            cancel(player, "Your bindstone is gone.");
            return;
        }

        long left = end - now;
        if (left <= 0) {
            ServerLevel level = player.server.getLevel(bind.get().dimension());
            if (level == null) {
                cancel(player, "Recall failed - destination unavailable.");
                return;
            }
            endTick.remove(player.getUUID());
            sendState(player, 0);
            BlockPos spot = BindEvents.safeSpawnNear(level, bind.get().pos());
            player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
            msg(player, "Recalled.");
            return;
        }
        if (left % 100 == 0) { // a countdown ping every 5 s
            player.displayClientMessage(
                    Component.literal("Recall: " + (left / 20) + "s").withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void sendState(ServerPlayer player, int ticksLeft) {
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RecallStatePacket(ticksLeft));
    }

    private static void msg(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }
}
