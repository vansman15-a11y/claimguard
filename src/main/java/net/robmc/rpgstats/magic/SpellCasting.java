package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.CastStatePacket;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.Stat;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side spellcasting: start a cast, finish it after its cast time, apply the effect. */
public final class SpellCasting {

    private record Pending(Spell spell, long endTick) {
    }

    private static final Map<UUID, Pending> casting = new HashMap<>();
    private static final Map<UUID, Map<Spell, Long>> cooldowns = new HashMap<>();

    private SpellCasting() {
    }

    public static boolean isCasting(UUID id) {
        return casting.containsKey(id);
    }

    public static void startCast(ServerPlayer player, int slot) {
        PlayerStats s = RpgManager.stats(player);
        Spell spell = Spell.byName(slot >= 0 && slot < 8 ? s.getSpellBar()[slot] : "");
        if (spell == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();

        if (casting.containsKey(player.getUUID())) {
            return; // already casting
        }
        long cdEnd = cooldowns.getOrDefault(player.getUUID(), Map.of()).getOrDefault(spell, 0L);
        if (now < cdEnd) {
            player.displayClientMessage(Component.literal(spell.displayName() + " is on cooldown.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!canAfford(player, s, spell)) {
            player.displayClientMessage(Component.literal("Not enough " + spell.costPool().name().toLowerCase() + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int castTicks = (int) Math.max(2, spell.castTicks() * StatFormulas.castSpeedMultiplier(s));
        casting.put(player.getUUID(), new Pending(spell, now + castTicks));
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CastStatePacket(spell.name(), castTicks));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    public static void interrupt(ServerPlayer player) {
        if (casting.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Spell interrupted!").withStyle(ChatFormatting.RED), true);
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CastStatePacket("", 0));
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (casting.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        casting.entrySet().removeIf(entry -> {
            if (now < entry.getValue().endTick()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) {
                complete(player, entry.getValue().spell());
            }
            return true;
        });
    }

    private static void complete(ServerPlayer player, Spell spell) {
        PlayerStats s = RpgManager.stats(player);

        if (spell.isTransfer()) {
            double input = Math.min(StatFormulas.TRANSFER_AMOUNT, available(player, s, spell.costPool()));
            double headroom = headroom(player, s, spell.gainPool());
            input = Math.min(input, headroom / 2.0);
            if (input <= 0) {
                player.displayClientMessage(Component.literal("Nothing to transfer.").withStyle(ChatFormatting.GRAY), true);
            } else {
                spend(player, s, spell.costPool(), input);
                gain(player, s, spell.gainPool(), input * 2.0);
            }
        } else { // Magic Bolt
            spend(player, s, spell.costPool(), spell.flatCost());
            castMagicBolt(player, s);
        }

        RpgManager.addXp(player, Stat.INTELLIGENCE, StatFormulas.XP_CAST_SPELL);
        s.addWeakMagicXp(StatFormulas.XP_CAST_SPELL * 0.5);
        cooldowns.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(spell, player.serverLevel().getGameTime() + spell.cooldownTicks());
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
        RpgManager.sync(player);
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CastStatePacket("", 0));
    }

    private static void castMagicBolt(ServerPlayer player, PlayerStats s) {
        // Raw damage; RpgEvents scales it up when the target is a player, same as melee.
        float damage = (float) ((StatFormulas.MAGIC_BOLT_BASE_DAMAGE + 0.6 * s.getWeakMagicLevel())
                * StatFormulas.spellDamageMultiplier(s));

        ServerLevel level = player.serverLevel();
        MagicBoltEntity bolt = new MagicBoltEntity(level, player, damage);
        bolt.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        Vec3 dir = player.getViewVector(1.0f);
        bolt.setDeltaMovement(dir.scale(0.55)); // slow travel
        bolt.shoot(dir.x, dir.y, dir.z, 0.55f, 0.0f);
        level.addFreshEntity(bolt);
    }

    // --- pool helpers ---

    private static boolean canAfford(ServerPlayer player, PlayerStats s, Spell spell) {
        if (spell.isTransfer()) {
            return available(player, s, spell.costPool()) > 0;
        }
        return available(player, s, spell.costPool()) >= spell.flatCost();
    }

    private static double available(ServerPlayer player, PlayerStats s, Spell.Pool pool) {
        return switch (pool) {
            case HEALTH -> player.getHealth() - 1.0; // never self-kill
            case STAMINA -> s.getStamina();
            case MANA -> s.getMana();
        };
    }

    private static double headroom(ServerPlayer player, PlayerStats s, Spell.Pool pool) {
        return switch (pool) {
            case HEALTH -> player.getMaxHealth() - player.getHealth();
            case STAMINA -> StatFormulas.maxStamina(s) - s.getStamina();
            case MANA -> StatFormulas.maxMana(s) - s.getMana();
        };
    }

    private static void spend(ServerPlayer player, PlayerStats s, Spell.Pool pool, double amount) {
        switch (pool) {
            case HEALTH -> player.setHealth((float) Math.max(1.0, player.getHealth() - amount));
            case STAMINA -> s.setStamina(s.getStamina() - amount);
            case MANA -> s.setMana(s.getMana() - amount);
        }
    }

    private static void gain(ServerPlayer player, PlayerStats s, Spell.Pool pool, double amount) {
        switch (pool) {
            case HEALTH -> player.heal((float) amount);
            case STAMINA -> s.setStamina(s.getStamina() + amount);
            case MANA -> s.setMana(s.getMana() + amount);
        }
    }
}
