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

    /**
     * A cast in progress. It charges until {@code endTick}, then fires - unless the
     * key is still held, in which case it holds at full ({@code charged}) so you can
     * aim, and fires when you release ({@code released}) or the max hold runs out.
     * Letting go early doesn't cancel it; the cast just finishes on its own.
     */
    private static final class Pending {
        final Spell spell;
        final long endTick;
        boolean charged;
        boolean released;
        long chargedAt;

        Pending(Spell spell, long endTick) {
            this.spell = spell;
            this.endTick = endTick;
        }
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
        // Speed of Wind is a toggle: press it again to end the channel
        if (spell == Spell.SPEED_OF_WIND && WindChannel.isChanneling(player.getUUID())) {
            WindChannel.stop(player);
            return;
        }
        // Water Spout is a toggle too, and casting anything else drops it
        if (WaterSpoutManager.isActive(player.getUUID())) {
            WaterSpoutManager.stop(player);
            if (spell == Spell.WATER_SPOUT) {
                return; // that press just turned it off
            }
        }
        if (s.getSchoolLevel(spell.school()) < spell.unlockLevel()) {
            player.displayClientMessage(Component.literal(spell.displayName() + " needs "
                    + spell.school().displayName() + " level " + spell.unlockLevel() + ".")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Silence.blocks(player, spell.school())) {
            player.displayClientMessage(Component.literal(spell.school().displayName() + " is silenced!")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
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

        int castTicks = (int) Math.max(2, spell.castTicks()
                * StatFormulas.castSpeedMultiplier(s) * Afflictions.castTimeMult(player));
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
        WindChannel.stop(player); // a hit / silence also drops a Speed of Wind channel
        WaterSpoutManager.stop(player);
        if (casting.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Spell interrupted!").withStyle(ChatFormatting.RED), true);
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CastStatePacket("", 0));
        }
    }

    /** The key was released: fire a fully-charged cast now, or just let a still-charging one finish. */
    public static void releaseCast(ServerPlayer player, int slot) {
        Pending p = casting.get(player.getUUID());
        if (p == null) {
            return;
        }
        if (p.charged) {
            casting.remove(player.getUUID());
            complete(player, p.spell);
        } else {
            p.released = true; // finish charging and fire on its own - no cancel
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        restoreGlowCmd(server);

        if (!casting.isEmpty()) {
            casting.entrySet().removeIf(entry -> {
                Pending p = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (!p.charged) {
                    if (now < p.endTick) {
                        return false; // still charging
                    }
                    if (p.released) {
                        // key was let go while charging - fire as soon as it's done
                        if (player != null && player.isAlive()) {
                            complete(player, p.spell);
                        }
                        return true;
                    }
                    p.charged = true;
                    p.chargedAt = now;
                    if (player != null) {
                        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new CastStatePacket(p.spell.name(), -1)); // -1 = charged, hold the bar full
                    }
                    return false;
                }
                // charged - wait for the release, but fire on its own after the max hold
                if (player != null && player.isAlive() && now - p.chargedAt >= StatFormulas.CHARGED_MAX_HOLD_TICKS) {
                    complete(player, p.spell);
                    return true;
                }
                return player == null; // drop a stale entry for someone who left
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
            case WARD -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castWard(player, lvl);
            }
            case SERPENTS_PLUME -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castSerpentsPlume(player, s, lvl);
            }
            case CINDER_MAELSTROM -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castCinderMaelstrom(player, lvl);
            }
            case HEARTWELL -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castHeartwell(player, lvl);
            }
            case PESTILENCE -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castPestilence(player, s, lvl);
            }
            case CHANT_OF_GROWTH -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castChantOfGrowth(player);
            }
            case BATTLE_HYMN -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castBattleHymn(player);
            }
            case WORD_OF_UNMAKING -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castWordOfUnmaking(player);
            }
            case FEARCRAFT -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castFearcraft(player);
            }
            case LIGHTNING_STRIKE -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castLightningStrike(player, lvl);
            }
            case SPEED_OF_WIND -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castSpeedOfWind(player);
            }
            case CHAIN_SHOCK -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castChainShock(player, s, lvl);
            }
            case WIND_LURE -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castWindLure(player);
            }
            case WATER_BREATHING -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castWaterBreathing(player);
            }
            case ICE_WALL -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castIceWall(player);
            }
            case WATER_SPOUT -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                WaterSpoutManager.start(player, lvl);
            }
            case RIPTIDE -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                RiptideWave.launch(player, lvl);
            }
            case SILENCING_WHISPER -> {
                spend(player, s, spell.costPool(), spell.flatCost());
                castSilencingWhisper(player);
            }
            default -> { // every projectile spell (Adept + Fire + Chaos bolts)
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
        float damage = (float) (StatFormulas.magicBoltDamage(spellLevel, s) * Afflictions.spellDamageMult(player));
        float speed = (float) StatFormulas.magicBoltSpeed(spellLevel); // slow at low level, front-loaded gains

        ServerLevel level = player.serverLevel();
        MagicBoltEntity bolt = new MagicBoltEntity(level, player, damage);
        bolt.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        Vec3 dir = player.getViewVector(1.0f);
        bolt.setDeltaMovement(dir.scale(speed));
        bolt.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
        level.addFreshEntity(bolt);
    }

    /** Ward: instant self-cast damage shield via vanilla Absorption. */
    private static void castWard(ServerPlayer player, int spellLevel) {
        int amp = StatFormulas.wardAbsorptionAmplifier(spellLevel);
        player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.ABSORPTION,
                StatFormulas.WARD_DURATION_TICKS, amp, false, false, true));
        player.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.55f, 0.8f, 1.0f), 1.2f),
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.45, 0.7, 0.45, 0.02);
    }

    /** Serpent's Plume: an instant hitscan fire ray. Dims the vision of a player it lands on. */
    private static void castSerpentsPlume(ServerPlayer player, PlayerStats s, int spellLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.SERPENTS_PLUME_RANGE));

        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? block.getLocation() : far;

        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, player, eye, end,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player && e.isAlive() && !e.isSpectator());

        Vec3 impact = hit != null ? hit.getLocation() : end;
        // a line of embers along the ray
        int steps = (int) Math.max(4, eye.distanceTo(impact) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = eye.lerp(impact, i / (double) steps);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        FireMelt.meltAround(level, impact, 1.5); // Serpent's Plume melts the ice / snow it hits

        if (hit != null && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target) {
            float dmg = (float) (StatFormulas.serpentsPlumeDamage(spellLevel)
                    * StatFormulas.spellDamageMultiplier(s) * Afflictions.spellDamageMult(player));
            target.hurt(player.damageSources().indirectMagic(player, player), dmg);
            FireMark.mark(target);
            if (target instanceof net.minecraft.world.entity.player.Player) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DARKNESS,
                        StatFormulas.SERPENTS_PLUME_DARKNESS_TICKS, 0, false, false, true));
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 12, 0.2, 0.3, 0.2, 0.02);
            RpgManager.addSpellXp(player, Spell.SERPENTS_PLUME, StatFormulas.spellHitXp(1));
        }
    }

    /** Cinder Maelstrom: drop a lingering AOE fire field where you're looking. */
    private static void castCinderMaelstrom(ServerPlayer player, int spellLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.CINDER_FIELD_PLACE_RANGE));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 center = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? block.getLocation() : far;

        FireFieldManager.spawn(player, center, spellLevel);
        FireMelt.meltAround(level, center, StatFormulas.CINDER_FIELD_RADIUS);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                center.x, center.y + 0.2, center.z, 3, 1.0, 0.2, 1.0, 0.0);
        level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.6f);
    }

    /** Heartwell: heal yourself, and splash a slice of that heal to allies standing in the purple aura. */
    private static void castHeartwell(ServerPlayer player, int spellLevel) {
        ServerLevel level = player.serverLevel();
        double heal = StatFormulas.heartwellSelfHeal(spellLevel);
        player.heal((float) heal);

        double splash = heal * StatFormulas.HEARTWELL_ALLY_FRACTION;
        double r = StatFormulas.HEARTWELL_AURA_RADIUS;
        for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(r))) {
            if (ally == player || ally.distanceToSqr(player) > r * r || !ally.isAlive()) {
                continue;
            }
            if (net.robmc.claimguard.clan.ClanActions.areFriendly(player.server, player.getUUID(), ally.getUUID())) {
                ally.heal((float) splash);
                RpgManager.sync(ally);
            }
        }

        level.sendParticles(new DustParticleOptions(new Vector3f(0.62f, 0.22f, 0.88f), 1.5f),
                player.getX(), player.getY() + 1.0, player.getZ(), 44, r * 0.5, 0.9, r * 0.5, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(), 18, r * 0.45, 0.8, r * 0.45, 0.0);
    }

    /** Pestilence: an instant hitscan ray that diseases whatever it lands on for damage over 5 s. */
    private static void castPestilence(ServerPlayer player, PlayerStats s, int spellLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.PESTILENCE_RANGE));

        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? block.getLocation() : far;

        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, player, eye, end,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player && e.isAlive() && !e.isSpectator());

        Vec3 impact = hit != null ? hit.getLocation() : end;
        int steps = (int) Math.max(4, eye.distanceTo(impact) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = eye.lerp(impact, i / (double) steps);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.COMPOSTER, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 1.0f, 0.7f);

        if (hit != null && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target) {
            float perTick = (float) (StatFormulas.pestilenceDotPerTick(spellLevel) * Afflictions.spellDamageMult(player));
            DiseaseManager.start(target, perTick, StatFormulas.PESTILENCE_DOT_TICKS);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE,
                    hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 16, 0.25, 0.35, 0.25, 0.03);
            RpgManager.addSpellXp(player, Spell.PESTILENCE, StatFormulas.spellHitXp(1));
        }
    }

    /** Chant of Growth: give nearby crops and saplings a shove along. */
    private static void castChantOfGrowth(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.util.RandomSource rand = level.random;
        net.minecraft.core.BlockPos origin = player.blockPosition();
        int r = (int) StatFormulas.GROWTH_RADIUS;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                origin.offset(-r, -3, -r), origin.offset(r, 3, r))) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
                    && b.isValidBonemealTarget(level, pos, state, false)
                    && rand.nextDouble() < StatFormulas.GROWTH_TICK_CHANCE) {
                net.minecraft.core.BlockPos p = pos.immutable();
                if (b.isBonemealSuccess(level, rand, p, state)) {
                    b.performBonemeal(level, rand, p, level.getBlockState(p));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.COMPOSTER,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, r * 0.4, 1.0, r * 0.4, 0.0);
    }

    /** Battle Hymn: empower yourself and nearby friendlies with +stats for 15 minutes. */
    private static void castBattleHymn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.robmc.rpgstats.magic.HymnBuff.grant(player);
        double rad = StatFormulas.BATTLE_HYMN_RADIUS;
        for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(rad))) {
            if (ally == player || !ally.isAlive() || ally.distanceToSqr(player) > rad * rad) {
                continue;
            }
            if (net.robmc.claimguard.clan.ClanActions.areFriendly(player.server, player.getUUID(), ally.getUUID())) {
                net.robmc.rpgstats.magic.HymnBuff.grant(ally);
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                player.getX(), player.getY() + 1.3, player.getZ(), 26, rad * 0.4, 0.8, rad * 0.4, 1.0);
    }

    /** Word of Unmaking: erase a small cluster of unclaimed blocks - drop someone through the floor. */
    private static void castWordOfUnmaking(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.UNMAKING_RANGE));
        net.minecraft.world.phys.BlockHitResult bhr = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            player.displayClientMessage(Component.literal("Nothing to unmake.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        net.minecraft.core.BlockPos hit = bhr.getBlockPos();
        int spread = StatFormulas.UNMAKING_SPREAD;
        int removed = 0;
        for (int dx = -spread; dx <= spread; dx++) {
            for (int dz = -spread; dz <= spread; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > spread) {
                    continue;
                }
                for (int dy = 0; dy < StatFormulas.UNMAKING_DEPTH; dy++) {
                    if (unmakeBlock(level, player, hit.offset(dx, -dy, dz))) {
                        removed++;
                    }
                }
            }
        }
        if (removed == 0) {
            player.displayClientMessage(Component.literal("That's protected.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static boolean unmakeBlock(ServerLevel level, ServerPlayer player, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(level, pos) < 0) {
            return false; // air, block entities (chests etc.), or unbreakable (bedrock)
        }
        if (!net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, pos, player)) {
            return false; // claim / admin-zone protected
        }
        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(st)); // vanilla break fx
        level.removeBlock(pos, false);
        return true;
    }

    /** Fearcraft: send every monster near the caster running for a few seconds. */
    private static void castFearcraft(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double r = StatFormulas.FEARCRAFT_RADIUS;
        int hit = 0;
        for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(r))) {
            if (!(mob instanceof net.minecraft.world.entity.monster.Enemy) || !mob.isAlive()) {
                continue;
            }
            if (mob.distanceToSqr(player) > r * r) {
                continue;
            }
            net.robmc.rpgstats.magic.FearManager.frighten(mob, player, StatFormulas.FEARCRAFT_DURATION_TICKS);
            hit++;
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, r * 0.5, 0.9, r * 0.5, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8f, 1.4f);
        player.displayClientMessage(Component.literal(
                hit == 0 ? "Fearcraft finds no prey." : "Fearcraft scatters " + hit + " " + (hit == 1 ? "monster" : "monsters") + "!")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** Generic aimed raycast: the first living thing under the crosshair within {@code range}, LoS-blocked. */
    private static net.minecraft.world.entity.LivingEntity aimedTarget(ServerPlayer player, double range,
                                                                       java.util.function.Predicate<net.minecraft.world.entity.Entity> extra) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getViewVector(1.0f).scale(range));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? block.getLocation() : far;
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, player, eye, end, new net.minecraft.world.phys.AABB(eye, end).inflate(1.5),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player && e.isAlive()
                        && !e.isSpectator() && extra.test(e));
        return hit != null && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
    }

    /** Lightning Strike: call a bolt down onto the aimed target. */
    private static void castLightningStrike(ServerPlayer player, int spellLevel) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.LivingEntity target = aimedTarget(player, StatFormulas.LIGHTNING_STRIKE_RANGE, e -> true);
        if (target == null) {
            player.displayClientMessage(Component.literal("No target in sight.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true); // we do our own (scaled) damage
            bolt.setCause(player);
            level.addFreshEntity(bolt);
        }
        float dmg = (float) (StatFormulas.lightningStrikeDamage(spellLevel) * StatFormulas.spellDamageMultiplier(RpgManager.stats(player))
                * Afflictions.spellDamageMult(player));
        target.hurt(player.damageSources().indirectMagic(player, player), dmg);
        target.setSecondsOnFire(StatFormulas.LIGHTNING_STRIKE_FIRE_SECONDS);
        if (isEnemyOf(player, target)) {
            RpgManager.addSpellXp(player, Spell.LIGHTNING_STRIKE, StatFormulas.spellHitXp(1));
        }
    }

    /** Speed of Wind: open a channel that hastens an ally while it drains your mana. */
    private static void castSpeedOfWind(ServerPlayer player) {
        net.minecraft.world.entity.LivingEntity target = aimedTarget(player, StatFormulas.SPEED_OF_WIND_RANGE,
                e -> e instanceof ServerPlayer);
        if (!(target instanceof ServerPlayer ally)) {
            player.displayClientMessage(Component.literal("Aim at a player to channel Speed of Wind.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (ally != player && !net.robmc.claimguard.clan.ClanActions.areFriendly(player.server, player.getUUID(), ally.getUUID())) {
            player.displayClientMessage(Component.literal("Speed of Wind only flows to allies.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        WindChannel.start(player, ally);
    }

    /** Chain Shock: an instant bolt that arcs from the first target to nearby ones for less each jump. */
    private static void castChainShock(ServerPlayer player, PlayerStats s, int spellLevel) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.LivingEntity first = aimedTarget(player, StatFormulas.CHAIN_SHOCK_RANGE, e -> true);
        if (first == null) {
            player.displayClientMessage(Component.literal("No target in sight.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        float primary = (float) (StatFormulas.chainShockDamage(spellLevel)
                * StatFormulas.spellDamageMultiplier(s) * Afflictions.spellDamageMult(player));

        java.util.List<net.minecraft.world.entity.LivingEntity> hitChain = new java.util.ArrayList<>();
        hitChain.add(first);
        first.hurt(player.damageSources().indirectMagic(player, player), primary);
        arc(level, player.getEyePosition(), first.position().add(0, first.getBbHeight() * 0.5, 0));

        net.minecraft.world.entity.LivingEntity from = first;
        for (int jump = 0; jump < StatFormulas.CHAIN_SHOCK_MAX_JUMPS; jump++) {
            net.minecraft.world.entity.LivingEntity next = null;
            double best = Double.MAX_VALUE;
            double r = StatFormulas.CHAIN_SHOCK_JUMP_RANGE;
            for (net.minecraft.world.entity.LivingEntity le : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    from.getBoundingBox().inflate(r))) {
                if (le == player || !le.isAlive() || hitChain.contains(le)) {
                    continue;
                }
                if (!(le instanceof net.minecraft.world.entity.monster.Enemy || le instanceof net.minecraft.world.entity.player.Player)) {
                    continue;
                }
                double d = le.distanceToSqr(from);
                if (d < best && d <= r * r) {
                    best = d;
                    next = le;
                }
            }
            if (next == null) {
                break;
            }
            float chained = (float) (primary * StatFormulas.CHAIN_SHOCK_FALLOFF[jump]);
            next.hurt(player.damageSources().indirectMagic(player, player), chained);
            arc(level, from.position().add(0, from.getBbHeight() * 0.5, 0),
                    next.position().add(0, next.getBbHeight() * 0.5, 0));
            hitChain.add(next);
            from = next;
        }

        int enemies = 0;
        for (net.minecraft.world.entity.LivingEntity le : hitChain) {
            if (isEnemyOf(player, le)) {
                enemies++;
            }
        }
        if (enemies > 0) {
            RpgManager.addSpellXp(player, Spell.CHAIN_SHOCK, StatFormulas.spellHitXp(enemies));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7f, 1.6f);
    }

    private static void arc(ServerLevel level, Vec3 a, Vec3 b) {
        int steps = (int) Math.max(3, a.distanceTo(b) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            double j = 0.15;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    p.x + (level.random.nextDouble() - 0.5) * j, p.y + (level.random.nextDouble() - 0.5) * j,
                    p.z + (level.random.nextDouble() - 0.5) * j, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Wind Lure: yank the aimed target up and toward the caster - allies or enemies, airborne or not. */
    private static void castWindLure(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.LivingEntity target = aimedTarget(player, StatFormulas.WIND_LURE_RANGE, e -> true);
        if (target == null) {
            player.displayClientMessage(Component.literal("No target in sight.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 toCaster = player.position().subtract(target.position());
        Vec3 flat = new Vec3(toCaster.x, 0, toCaster.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = player.getViewVector(1.0f).scale(-1);
        }
        Vec3 pull = flat.normalize().scale(StatFormulas.WIND_LURE_PULL);
        target.setDeltaMovement(pull.x, StatFormulas.WIND_LURE_LIFT, pull.z);
        target.hurtMarked = true;
        target.hasImpulse = true;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** True if the mod treats {@code target} as an enemy of {@code caster} for XP purposes. */
    private static boolean isEnemyOf(ServerPlayer caster, net.minecraft.world.entity.LivingEntity target) {
        if (target == caster) {
            return false;
        }
        if (target instanceof ServerPlayer other) {
            return !net.robmc.claimguard.clan.ClanActions.areFriendly(caster.server, caster.getUUID(), other.getUUID());
        }
        return target instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** Water Breathing: aim at an ally to buff them, at nothing to buff yourself. */
    private static void castWaterBreathing(ServerPlayer player) {
        net.minecraft.world.entity.LivingEntity aimed = aimedTarget(player, StatFormulas.WATER_BREATHING_RANGE,
                e -> e instanceof ServerPlayer);
        net.minecraft.world.entity.LivingEntity who = aimed != null ? aimed : player;
        WaterBuff.grant(who);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                who.getX(), who.getY() + 1.0, who.getZ(), 26, 0.4, 0.7, 0.4, 0.02);
        if (who != player && who instanceof ServerPlayer sp) {
            player.displayClientMessage(Component.literal("Water Breathing granted to " + sp.getGameProfile().getName() + ".")
                    .withStyle(ChatFormatting.AQUA), true);
            sp.displayClientMessage(Component.literal("You can breathe underwater.").withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Ice Wall: raise a 3x3 pane of ice where you're looking. */
    private static void castIceWall(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getViewVector(1.0f).scale(StatFormulas.ICE_WALL_RANGE));
        net.minecraft.world.phys.BlockHitResult bhr = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 aim = bhr.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? bhr.getLocation() : far;
        if (!IceWallManager.raise(player, aim)) {
            player.displayClientMessage(Component.literal("No room for an ice wall.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Silencing Whisper: interrupt an enemy's cast and seal that school (or all schools) for 2 s. */
    private static void castSilencingWhisper(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(StatFormulas.SILENCE_RANGE));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(
                eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? block.getLocation() : far;

        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, player, eye, end,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
                e -> e instanceof ServerPlayer && e != player && e.isAlive() && !e.isSpectator());

        Vec3 impact = hit != null ? hit.getLocation() : end;
        int steps = (int) Math.max(4, eye.distanceTo(impact) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = eye.lerp(impact, i / (double) steps);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1.0f, 0.5f);

        if (hit != null && hit.getEntity() instanceof ServerPlayer victim) {
            Pending pending = casting.get(victim.getUUID());
            School lockSchool = pending != null ? pending.spell.school() : null;
            interrupt(victim);
            if (lockSchool != null) {
                Silence.apply(victim, lockSchool, StatFormulas.SILENCE_LOCK_TICKS);
                victim.displayClientMessage(Component.literal("Silenced - " + lockSchool.displayName() + " sealed!")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            } else {
                Silence.applyAll(victim, StatFormulas.SILENCE_LOCK_TICKS);
                victim.displayClientMessage(Component.literal("Silenced!").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            RpgManager.addSpellXp(player, Spell.SILENCING_WHISPER, StatFormulas.spellHitXp(1));
        }
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
