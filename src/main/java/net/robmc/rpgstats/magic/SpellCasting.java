package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.CastStatePacket;
import net.robmc.claimguard.network.SpellCooldownPacket;
import net.robmc.claimguard.network.StaffGlowPacket;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.Stat;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.item.Weapons;
import net.robmc.rpgstats.registry.RpgSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side spellcasting: start a cast, finish it after its cast time, apply the effect. */
public final class SpellCasting {

    private record Pending(Spell spell, long endTick) {
    }

    /** A transfer's gain, paid out a little each tick instead of all at once. */
    private static final class TransferGain {
        final Spell.Pool pool;
        final double perTick;
        long ticksLeft;

        TransferGain(Spell.Pool pool, double perTick, long ticksLeft) {
            this.pool = pool;
            this.perTick = perTick;
            this.ticksLeft = ticksLeft;
        }
    }

    // transfer telltale particles - lets nearby players read which transfer someone is doing
    private static final Vector3f FX_RED = new Vector3f(1.0f, 0.15f, 0.15f);
    private static final Vector3f FX_YELLOW = new Vector3f(1.0f, 0.88f, 0.2f);
    private static final Vector3f FX_BLUE = new Vector3f(0.25f, 0.45f, 1.0f);

    private static final Map<UUID, Pending> casting = new HashMap<>();
    private static final Map<UUID, Map<Spell, Long>> cooldowns = new HashMap<>();
    private static final Map<UUID, List<TransferGain>> transferGains = new HashMap<>();
    /** Players whose resource-pack staff we bumped to the glowing CustomModelData for a cast. */
    private static final java.util.Set<UUID> glowBumped = new java.util.HashSet<>();

    private SpellCasting() {
    }

    public static boolean isCasting(UUID id) {
        return casting.containsKey(id);
    }

    public static void startCast(ServerPlayer player, int slot) {
        PlayerStats s = RpgManager.stats(player);
        Spell spell = Spell.byName(slot >= 0 && slot < StatFormulas.TOTAL_BAR_SLOTS ? s.getSpellBar()[slot] : "");
        if (spell == null) {
            return;
        }
        if (s.getSchoolLevel(spell.school()) < spell.unlockLevel()) {
            player.displayClientMessage(Component.literal(spell.displayName() + " needs "
                    + spell.school().displayName() + " level " + spell.unlockLevel() + ".")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Transfers can be cast bare-handed or with a staff. Everything else needs a staff.
        ItemStack hand = player.getMainHandItem();
        boolean staff = Weapons.isStaff(hand);
        if (spell.isTransfer()) {
            if (!staff && !hand.isEmpty()) {
                player.displayClientMessage(
                        Component.literal("Transfers need a free hand or a staff.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        } else if (!staff) {
            player.displayClientMessage(
                    Component.literal("That spell can only be cast with a staff.").withStyle(ChatFormatting.GRAY), true);
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
        if (staff) {
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new StaffGlowPacket(player.getId(), castTicks + 8));
            // resource-pack staves glow by swapping CustomModelData 71003 -> 71004
            if (Weapons.customModelData(hand) == Weapons.CMD_STAFF) {
                Weapons.setCustomModelData(hand, Weapons.CMD_STAFF_GLOW);
                glowBumped.add(player.getUUID());
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Put a resource-pack staff's CustomModelData back once its cast is over. Called each tick. */
    private static void restoreGlowCmd(MinecraftServer server) {
        if (glowBumped.isEmpty()) {
            return;
        }
        glowBumped.removeIf(id -> {
            if (casting.containsKey(id)) {
                return false; // still casting
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                ItemStack held = player.getMainHandItem();
                if (Weapons.customModelData(held) == Weapons.CMD_STAFF_GLOW) {
                    Weapons.setCustomModelData(held, Weapons.CMD_STAFF);
                }
            }
            return true;
        });
    }

    public static void interrupt(ServerPlayer player) {
        if (casting.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Spell interrupted!").withStyle(ChatFormatting.RED), true);
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CastStatePacket("", 0));
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        restoreGlowCmd(server);

        if (!casting.isEmpty()) {
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

        if (!transferGains.isEmpty()) {
            transferGains.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || !player.isAlive()) {
                    return true;
                }
                PlayerStats s = RpgManager.stats(player);
                List<TransferGain> list = entry.getValue();
                list.removeIf(g -> {
                    gain(player, s, g.pool, g.perTick); // pool clamps; overflow is discarded
                    return --g.ticksLeft <= 0;
                });
                RpgManager.sync(player);
                return list.isEmpty();
            });
        }
    }

    private static void complete(ServerPlayer player, Spell spell) {
        PlayerStats s = RpgManager.stats(player);
        int lvl = s.getSpellLevel(spell);

        switch (spell.kind()) {
            case TRANSFER -> {
                // Only the source pool has to have something in it - a full target pool is
                // fine, the overflow is wasted. How much moves and the return rate scale
                // with the school level.
                double input = Math.min(StatFormulas.transferAmount(lvl), available(player, s, spell.costPool()));
                if (input <= 0) {
                    player.displayClientMessage(Component.literal("Nothing to transfer.").withStyle(ChatFormatting.GRAY), true);
                } else {
                    spend(player, s, spell.costPool(), input);
                    double total = input * StatFormulas.transferRatio(lvl);
                    int ticks = StatFormulas.TRANSFER_DURATION_TICKS;
                    transferGains.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                            .add(new TransferGain(spell.gainPool(), total / ticks, ticks));
                    spawnTransferFx(player, spell);
                }
            }
            case MAGIC_BOLT -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castMagicBolt(player, s, lvl);
            }
            default -> { // every Adept projectile
                spend(player, s, spell.costPool(), spell.flatCost());
                SpellProjectiles.launch(player, spell, lvl);
            }
        }

        RpgManager.addXp(player, Stat.INTELLIGENCE, StatFormulas.XP_CAST_SPELL);
        RpgManager.addSpellXp(player, spell, spell.isTransfer()
                ? StatFormulas.XP_CAST_TRANSFER : StatFormulas.XP_CAST_SPELL);
        cooldowns.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(spell, player.serverLevel().getGameTime() + spell.cooldownTicks());
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SpellCooldownPacket(spell.name(), spell.cooldownTicks()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                RpgSounds.forSpell(spell), SoundSource.PLAYERS, 1.0f, 1.0f);
        RpgManager.sync(player);
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CastStatePacket("", 0));
    }

    /** A small two-tone puff at the feet and head so nearby players can tell which transfer this is. */
    private static void spawnTransferFx(ServerPlayer player, Spell spell) {
        Vector3f feet;
        Vector3f head;
        switch (spell) {
            case STAMINA_TO_HEALTH -> { feet = FX_YELLOW; head = FX_RED; }
            case MANA_TO_STAMINA -> { feet = FX_BLUE; head = FX_YELLOW; }
            case HEALTH_TO_MANA -> { feet = FX_RED; head = FX_BLUE; }
            default -> { return; }
        }
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double z = player.getZ();
        level.sendParticles(new DustParticleOptions(feet, 0.8f),
                x, player.getY() + 0.15, z, 4, 0.22, 0.1, 0.22, 0.0);
        level.sendParticles(new DustParticleOptions(head, 0.8f),
                x, player.getEyeY() + 0.15, z, 4, 0.22, 0.1, 0.22, 0.0);
    }

    private static void castMagicBolt(ServerPlayer player, PlayerStats s, int spellLevel) {
        // Raw damage; RpgEvents scales it up when the target is a player, same as melee.
        float damage = (float) StatFormulas.magicBoltDamage(spellLevel, s);
        float speed = (float) StatFormulas.magicBoltSpeed(spellLevel); // slow at low level, front-loaded gains

        ServerLevel level = player.serverLevel();
        MagicBoltEntity bolt = new MagicBoltEntity(level, player, damage);
        bolt.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        Vec3 dir = player.getViewVector(1.0f);
        bolt.setDeltaMovement(dir.scale(speed));
        bolt.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
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
