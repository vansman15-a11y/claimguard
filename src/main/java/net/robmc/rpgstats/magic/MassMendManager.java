package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mass Mend: a channelled radius heal. Everyone friendly nearby is topped up a
 * little each tick, and whoever is hurt worst gets extra. It bleeds the caster's
 * mana and drops the moment they move off the spot, cast something else, are
 * interrupted, or run dry.
 */
public final class MassMendManager {

    private static final class Channel {
        final int schoolLevel;
        final Vec3 anchor;

        Channel(int schoolLevel, Vec3 anchor) {
            this.schoolLevel = schoolLevel;
            this.anchor = anchor;
        }
    }

    private static final Map<UUID, Channel> channels = new HashMap<>();

    private MassMendManager() {
    }

    public static boolean isChanneling(UUID id) {
        return channels.containsKey(id);
    }

    public static void start(ServerPlayer caster, int schoolLevel) {
        channels.put(caster.getUUID(), new Channel(schoolLevel, caster.position()));
        caster.displayClientMessage(Component.literal("You channel Mass Mend - hold your ground.").withStyle(ChatFormatting.GREEN), true);
        caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    public static void stop(ServerPlayer caster) {
        stop(caster.getUUID(), caster.getServer());
    }

    public static void stop(UUID id, MinecraftServer server) {
        if (channels.remove(id) != null && server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(Component.literal("Mass Mend ends.").withStyle(ChatFormatting.GRAY), true);
            }
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
            if (caster == null || !caster.isAlive()) {
                return true;
            }
            Channel ch = entry.getValue();
            if (caster.position().distanceToSqr(ch.anchor)
                    > StatFormulas.MASS_MEND_MOVE_TOLERANCE * StatFormulas.MASS_MEND_MOVE_TOLERANCE) {
                caster.displayClientMessage(Component.literal("You move - Mass Mend breaks.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            var s = RpgManager.stats(caster);
            if (s.getMana() < StatFormulas.MASS_MEND_MANA_PER_TICK) {
                caster.displayClientMessage(Component.literal("Out of mana - Mass Mend ends.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            s.setMana(s.getMana() - StatFormulas.MASS_MEND_MANA_PER_TICK);
            RpgManager.sync(caster);

            ServerLevel level = caster.serverLevel();
            double base = StatFormulas.massMendHealPerTick(ch.schoolLevel);

            Player worst = null;
            double worstFrac = 1.0;
            var friends = level.getEntitiesOfClass(Player.class,
                    new AABB(caster.position(), caster.position()).inflate(StatFormulas.MASS_MEND_RADIUS),
                    p -> p.isAlive() && ClanActions.areFriendly(server, caster.getUUID(), p.getUUID()));
            for (Player p : friends) {
                double frac = p.getHealth() / p.getMaxHealth();
                if (frac < worstFrac) {
                    worstFrac = frac;
                    worst = p;
                }
                p.heal((float) base);
            }
            if (worst != null && worstFrac < 0.95) {
                worst.heal((float) (StatFormulas.MASS_MEND_INJURED_BONUS * (1.0 - worstFrac)));
            }

            if (level.getGameTime() % 4 == 0) {
                level.sendParticles(ParticleTypes.HEART,
                        caster.getX(), caster.getY() + 1.0, caster.getZ(), 3,
                        StatFormulas.MASS_MEND_RADIUS * 0.4, 0.4, StatFormulas.MASS_MEND_RADIUS * 0.4, 0.0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        caster.getX(), caster.getY() + 0.4, caster.getZ(), 6,
                        StatFormulas.MASS_MEND_RADIUS * 0.5, 0.2, StatFormulas.MASS_MEND_RADIUS * 0.5, 0.0);
            }
            return false;
        });
    }
}
