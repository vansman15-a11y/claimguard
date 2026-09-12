package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speed of Wind: a self-buff, +{@link StatFormulas#SPEED_OF_WIND_MOVE_SPEED} movement speed and
 * +{@link StatFormulas#SPEED_OF_WIND_CAST_SPEED} spell casting speed for
 * {@link StatFormulas#SPEED_OF_WIND_DURATION_TICKS}. The cast-speed half rides on
 * {@link net.robmc.rpgstats.PlayerStats#hasWindSpeedBuff()} (picked up by
 * {@link StatFormulas#castSpeedMultiplier}); this class tracks the timer and the movement
 * attribute modifier.
 */
public final class SpeedOfWindBuff {

    private static final UUID MOVE_MOD_ID = UUID.fromString("a17e5c00-0003-4a00-8000-0000000000a3");

    private static final Map<UUID, Long> until = new HashMap<>();

    private SpeedOfWindBuff() {
    }

    public static void grant(ServerPlayer player) {
        RpgManager.stats(player).setWindSpeedBuff(true);
        until.put(player.getUUID(), player.serverLevel().getGameTime() + StatFormulas.SPEED_OF_WIND_DURATION_TICKS);
        addMoveSpeed(player);
        player.displayClientMessage(Component.literal("The wind speeds you on your way!").withStyle(ChatFormatting.AQUA), true);
    }

    public static void clear(UUID id) {
        until.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (until.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        until.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                RpgManager.stats(p).setWindSpeedBuff(false);
                removeMoveSpeed(p);
                p.displayClientMessage(Component.literal("Speed of Wind fades.").withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        });
    }

    private static void addMoveSpeed(ServerPlayer player) {
        // transient, not permanent - this buff (like Battle Hymn) is cleared on logout and never
        // persisted, so the modifier shouldn't be saved to the entity's NBT either
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(MOVE_MOD_ID) == null) {
            inst.addTransientModifier(new AttributeModifier(MOVE_MOD_ID, "speed_of_wind_move",
                    StatFormulas.SPEED_OF_WIND_MOVE_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeMoveSpeed(ServerPlayer player) {
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(MOVE_MOD_ID);
        }
    }
}
