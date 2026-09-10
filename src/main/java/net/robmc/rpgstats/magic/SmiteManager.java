package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Divine Smite: for a few seconds the caster's next melee hit lands with bonus
 * radiant damage and refunds some mana. The bonus is applied in
 * {@code RpgEvents.onLivingHurt}; this class holds the armed state.
 */
public final class SmiteManager {

    private static final class Armed {
        final int spellLevel;
        final long endTick;

        Armed(int spellLevel, long endTick) {
            this.spellLevel = spellLevel;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, Armed> armed = new HashMap<>();

    private SmiteManager() {
    }

    public static void arm(ServerPlayer player, int spellLevel) {
        long now = player.serverLevel().getGameTime();
        armed.put(player.getUUID(), new Armed(spellLevel, now + StatFormulas.DIVINE_SMITE_TICKS));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, StatFormulas.DIVINE_SMITE_TICKS, 0, false, false, true));
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.6f);
        player.displayClientMessage(Component.literal("Your weapon takes on a radiant light.").withStyle(ChatFormatting.YELLOW), true);
    }

    public static boolean isArmed(UUID id, long now) {
        Armed a = armed.get(id);
        return a != null && now < a.endTick;
    }

    /** Consume the arm and return the raw radiant bonus, refunding mana. 0 if not armed. */
    public static float consume(ServerPlayer attacker) {
        Armed a = armed.remove(attacker.getUUID());
        if (a == null || attacker.serverLevel().getGameTime() >= a.endTick) {
            return 0.0f;
        }
        var s = RpgManager.stats(attacker);
        s.setMana(s.getMana() + StatFormulas.DIVINE_SMITE_MANA_REFUND); // setMana clamps to max
        RpgManager.sync(attacker);
        attacker.removeEffect(MobEffects.GLOWING);
        ServerLevel level = attacker.serverLevel();
        level.playSound(null, attacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.7f);
        return (float) StatFormulas.divineSmiteBonus(a.spellLevel);
    }

    public static void clear(UUID id) {
        armed.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (armed.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        armed.entrySet().removeIf(e -> {
            if (now < e.getValue().endTick) {
                return false;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                p.removeEffect(MobEffects.GLOWING);
                p.displayClientMessage(Component.literal("The radiance fades from your weapon.").withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        });
    }
}
