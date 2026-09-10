package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speed of Wind: while a caster keeps the channel up, a beam links them to an
 * ally who gets faster weapon swings and a little extra move speed. The channel
 * bleeds the caster's mana every tick and drops the moment the caster is
 * interrupted, silenced, runs dry, or the target gets out of range.
 */
public final class WindChannel {

    private static final UUID ATTACK_MOD_ID = UUID.fromString("a17e5c00-0001-4a00-8000-0000000000a1");
    private static final UUID MOVE_MOD_ID = UUID.fromString("a17e5c00-0002-4a00-8000-0000000000a2");

    private static final class Channel {
        final UUID target;

        Channel(UUID target) {
            this.target = target;
        }
    }

    private static final Map<UUID, Channel> channels = new HashMap<>();

    private WindChannel() {
    }

    public static boolean isChanneling(UUID casterId) {
        return channels.containsKey(casterId);
    }

    public static void start(ServerPlayer caster, ServerPlayer target) {
        channels.put(caster.getUUID(), new Channel(target.getUUID()));
        applyBuff(target);
        caster.displayClientMessage(Component.literal("Speed of Wind flows to " + target.getGameProfile().getName() + ".")
                .withStyle(ChatFormatting.AQUA), true);
        target.displayClientMessage(Component.literal("The wind hastens you!").withStyle(ChatFormatting.AQUA), true);
    }

    /** Stop the channel a player is casting (no-op if they aren't). */
    public static void stop(ServerPlayer caster) {
        stop(caster.getUUID(), caster.getServer());
    }

    public static void stop(UUID casterId, MinecraftServer server) {
        Channel c = channels.remove(casterId);
        if (c == null || server == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(c.target);
        if (target != null) {
            clearBuff(target);
            target.displayClientMessage(Component.literal("The wind fades.").withStyle(ChatFormatting.GRAY), true);
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(casterId);
        if (caster != null) {
            caster.displayClientMessage(Component.literal("Speed of Wind ends.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static void clear(UUID id) {
        channels.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (channels.isEmpty()) {
            return;
        }
        channels.entrySet().removeIf(entry -> {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getValue().target);
            if (caster == null || target == null || !caster.isAlive() || !target.isAlive()
                    || caster.level() != target.level()
                    || caster.distanceToSqr(target) > StatFormulas.SPEED_OF_WIND_RANGE * StatFormulas.SPEED_OF_WIND_RANGE) {
                if (target != null) {
                    clearBuff(target);
                }
                return true;
            }

            var s = RpgManager.stats(caster);
            if (s.getMana() <= 0) {
                clearBuff(target);
                caster.displayClientMessage(Component.literal("Out of mana - Speed of Wind ends.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            s.setMana(s.getMana() - StatFormulas.SPEED_OF_WIND_MANA_PER_TICK);
            RpgManager.sync(caster);

            applyBuff(target); // keep it topped up in case something stripped it

            // the beam
            ServerLevel level = caster.serverLevel();
            Vec3 a = caster.getEyePosition().add(0, -0.3, 0);
            Vec3 b = target.position().add(0, target.getBbHeight() * 0.5, 0);
            int steps = (int) Math.max(3, a.distanceTo(b));
            for (int i = 0; i <= steps; i++) {
                Vec3 p = a.lerp(b, i / (double) steps);
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            return false;
        });
    }

    private static void applyBuff(LivingEntity target) {
        addMod(target, Attributes.ATTACK_SPEED, ATTACK_MOD_ID, "wind_attack", StatFormulas.SPEED_OF_WIND_ATTACK_SPEED);
        addMod(target, Attributes.MOVEMENT_SPEED, MOVE_MOD_ID, "wind_move", StatFormulas.SPEED_OF_WIND_MOVE_SPEED);
    }

    private static void clearBuff(LivingEntity target) {
        removeMod(target, Attributes.ATTACK_SPEED, ATTACK_MOD_ID);
        removeMod(target, Attributes.MOVEMENT_SPEED, MOVE_MOD_ID);
    }

    private static void addMod(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr,
                               UUID id, String name, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null && inst.getModifier(id) == null) {
            inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeMod(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr, UUID id) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }
}
