package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.WildShapeSyncPacket;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Druid shapeshifting. Wolf Form is a land toggle (+25% speed, Bite + Leap);
 * Dolphin Form is a water-only toggle (+35% swim speed, breathes underwater) that
 * drops itself if you stay out of the water. Both re-cast to end. The visual model
 * swap happens client-side off {@link WildShapeSyncPacket}.
 */
public final class WildShapeManager {

    public static final int FORM_NONE = 0;
    public static final int FORM_WOLF = 1;
    public static final int FORM_DOLPHIN = 2;

    private static final UUID WOLF_SPEED_ID = UUID.fromString("d0000000-0002-4a00-8000-00000000c001");
    private static final UUID DOLPHIN_SWIM_ID = UUID.fromString("d0000000-0002-4a00-8000-00000000c002");

    private static final class Shape {
        int form;
        int spellLevel;
        long nextBiteTick;
        long nextLeapTick;
        int outOfWaterTicks;
        long nextRebroadcast;
    }

    private static final Map<UUID, Shape> shapes = new HashMap<>();

    private WildShapeManager() {
    }

    public static boolean isWolf(UUID id) {
        Shape s = shapes.get(id);
        return s != null && s.form == FORM_WOLF;
    }

    public static boolean isDolphin(UUID id) {
        Shape s = shapes.get(id);
        return s != null && s.form == FORM_DOLPHIN;
    }

    public static boolean inAnyForm(UUID id) {
        return shapes.containsKey(id);
    }

    public static int form(UUID id) {
        Shape s = shapes.get(id);
        return s == null ? FORM_NONE : s.form;
    }

    // --- entering / leaving ---

    public static void toggleWolf(ServerPlayer player, int spellLevel) {
        if (isWolf(player.getUUID())) {
            stop(player);
            return;
        }
        enter(player, FORM_WOLF, spellLevel);
        player.displayClientMessage(Component.literal("You take the shape of a wolf.").withStyle(ChatFormatting.DARK_GREEN), true);
        applyWolfSpeed(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void toggleDolphin(ServerPlayer player, int spellLevel) {
        if (isDolphin(player.getUUID())) {
            stop(player);
            return;
        }
        if (!player.isInWaterOrBubble()) {
            player.displayClientMessage(Component.literal("Dolphin Form can only be taken in water.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        enter(player, FORM_DOLPHIN, spellLevel);
        player.displayClientMessage(Component.literal("You slip into the shape of a dolphin.").withStyle(ChatFormatting.AQUA), true);
        applyDolphinSwim(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_AMBIENT_WATER, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void enter(ServerPlayer player, int form, int spellLevel) {
        // one form at a time
        removeAttributes(player);
        Shape s = new Shape();
        s.form = form;
        s.spellLevel = spellLevel;
        shapes.put(player.getUUID(), s);
        WindChannel.stop(player);
        WaterSpoutManager.stop(player);
        sync(player, form);
        burst(player);
    }

    public static void stop(ServerPlayer player) {
        if (shapes.remove(player.getUUID()) == null) {
            return;
        }
        removeAttributes(player);
        player.removeEffect(MobEffects.DOLPHINS_GRACE);
        sync(player, FORM_NONE);
        burst(player);
        player.displayClientMessage(Component.literal("Your own shape returns.").withStyle(ChatFormatting.GRAY), true);
    }

    public static void stop(UUID id, MinecraftServer server) {
        if (server == null) {
            shapes.remove(id);
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            stop(p);
        } else {
            shapes.remove(id);
        }
    }

    public static void clear(UUID id) {
        shapes.remove(id);
    }

    // --- wolf abilities ---

    /** True if the wolf-form attacker's bite is off its short internal cooldown (and refreshes it). */
    public static boolean tryBite(ServerPlayer attacker) {
        Shape s = shapes.get(attacker.getUUID());
        if (s == null || s.form != FORM_WOLF) {
            return false;
        }
        long now = attacker.serverLevel().getGameTime();
        if (now < s.nextBiteTick) {
            return false;
        }
        s.nextBiteTick = now + StatFormulas.WOLF_BITE_COOLDOWN_TICKS;
        return true;
    }

    public static int wolfSpellLevel(UUID id) {
        Shape s = shapes.get(id);
        return s == null ? 1 : s.spellLevel;
    }

    /** Leap toward the crosshair. Called from the WolfLeapPacket handler. */
    public static void leap(ServerPlayer player) {
        Shape s = shapes.get(player.getUUID());
        if (s == null || s.form != FORM_WOLF) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now < s.nextLeapTick) {
            return;
        }
        s.nextLeapTick = now + StatFormulas.WOLF_LEAP_COOLDOWN_TICKS;

        Vec3 look = player.getViewVector(1.0f);
        double power = StatFormulas.WOLF_LEAP_POWER;
        double up = Math.max(0.32, look.y * power + 0.28);
        player.setDeltaMovement(look.x * power, up, look.z * power);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.2, 0.05, 0.2, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.FOX_AGGRO, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    // --- tick ---

    public static void tick(MinecraftServer server) {
        if (shapes.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        shapes.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !p.isAlive()) {
                return true;
            }
            Shape s = entry.getValue();

            if (s.form == FORM_WOLF) {
                applyWolfSpeed(p);
                if (now % 6 == 0) {
                    p.serverLevel().sendParticles(ParticleTypes.MYCELIUM,
                            p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.2, 0.02, 0.2, 0.0);
                }
            } else if (s.form == FORM_DOLPHIN) {
                applyDolphinSwim(p);
                p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false, false));
                if (p.isInWaterOrBubble()) {
                    s.outOfWaterTicks = 0;
                    if (now % 4 == 0) {
                        p.serverLevel().sendParticles(ParticleTypes.BUBBLE,
                                p.getX(), p.getY() + 0.4, p.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
                    }
                } else if (++s.outOfWaterTicks > StatFormulas.DOLPHIN_FORM_OUT_OF_WATER_GRACE) {
                    removeAttributes(p);
                    p.removeEffect(MobEffects.DOLPHINS_GRACE);
                    sync(p, FORM_NONE);
                    burst(p);
                    p.displayClientMessage(Component.literal("Beached, you flop back into your own shape.").withStyle(ChatFormatting.GRAY), true);
                    return true;
                }
            }

            // re-announce the form now and then so clients that just started tracking pick it up
            if (now >= s.nextRebroadcast) {
                s.nextRebroadcast = now + 40;
                sync(p, s.form);
            }
            return false;
        });
    }

    // --- helpers ---

    private static void applyWolfSpeed(ServerPlayer p) {
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(WOLF_SPEED_ID) == null) {
            inst.addTransientModifier(new AttributeModifier(WOLF_SPEED_ID, "wolf_form_speed",
                    StatFormulas.WOLF_FORM_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void applyDolphinSwim(ServerPlayer p) {
        if (ForgeMod.SWIM_SPEED == null) {
            return;
        }
        AttributeInstance inst = p.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (inst != null && inst.getModifier(DOLPHIN_SWIM_ID) == null) {
            inst.addTransientModifier(new AttributeModifier(DOLPHIN_SWIM_ID, "dolphin_form_swim",
                    StatFormulas.DOLPHIN_FORM_SWIM_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeAttributes(ServerPlayer p) {
        AttributeInstance move = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null) {
            move.removeModifier(WOLF_SPEED_ID);
        }
        if (ForgeMod.SWIM_SPEED != null) {
            AttributeInstance swim = p.getAttribute(ForgeMod.SWIM_SPEED.get());
            if (swim != null) {
                swim.removeModifier(DOLPHIN_SWIM_ID);
            }
        }
    }

    private static void sync(ServerPlayer p, int form) {
        WildShapeSyncPacket packet = new WildShapeSyncPacket(p.getId(), form);
        // straight to the shifted player (drives their own camera + hand hiding)...
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        // ...and to everyone who can see them (drives the model swap)
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), packet);
    }

    private static void burst(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, at, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
