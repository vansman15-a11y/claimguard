package net.robmc.rpgstats.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.claimguard.event.ProtectionEvents;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Astral Annihilation: a channelled beam that grows more dangerous the longer
 * you hold it. Roots you in place, drains mana faster as it ramps up, and once
 * it's been running a moment starts chewing through whatever terrain it crosses.
 */
public final class AstralAnnihilationManager {

    private static final Vector3f BEAM_COLOUR = new Vector3f(0.55f, 0.35f, 0.95f);

    private static final class Channel {
        final int spellLevel;
        final long startTick;
        long nextDamageTick;
        long nextDigTick;

        Channel(int spellLevel, long now) {
            this.spellLevel = spellLevel;
            this.startTick = now;
            this.nextDamageTick = now + StatFormulas.ASTRAL_ANNIHILATION_DAMAGE_INTERVAL;
            this.nextDigTick = now + StatFormulas.ASTRAL_ANNIHILATION_DIG_START_TICKS;
        }
    }

    private static final Map<UUID, Channel> channels = new HashMap<>();

    private AstralAnnihilationManager() {
    }

    public static boolean isChanneling(UUID id) {
        return channels.containsKey(id);
    }

    public static void start(ServerPlayer caster, int spellLevel) {
        channels.put(caster.getUUID(), new Channel(spellLevel, caster.serverLevel().getGameTime()));
        caster.displayClientMessage(Component.literal("You channel Astral Annihilation...").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    public static void stop(ServerPlayer caster) {
        stop(caster.getUUID(), caster.getServer());
    }

    public static void stop(UUID id, MinecraftServer server) {
        if (channels.remove(id) != null && server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(Component.literal("Astral Annihilation ends.").withStyle(ChatFormatting.GRAY), true);
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
        long now = server.overworld().getGameTime();
        channels.entrySet().removeIf(entry -> {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster == null || !caster.isAlive()) {
                return true;
            }
            Channel ch = entry.getValue();
            var s = RpgManager.stats(caster);

            double charge = StatFormulas.astralAnnihilationCharge(now - ch.startTick);
            double manaPerTick = StatFormulas.ASTRAL_ANNIHILATION_MANA_PER_TICK_MIN
                    + (StatFormulas.ASTRAL_ANNIHILATION_MANA_PER_TICK_MAX - StatFormulas.ASTRAL_ANNIHILATION_MANA_PER_TICK_MIN) * charge;
            if (s.getMana() < manaPerTick) {
                caster.displayClientMessage(Component.literal("Out of mana - the annihilation collapses.").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            s.setMana(s.getMana() - manaPerTick);
            RpgManager.sync(caster);

            // rooted in place - horizontal movement is cancelled every tick
            Vec3 dm = caster.getDeltaMovement();
            caster.setDeltaMovement(0.0, dm.y, 0.0);
            caster.hurtMarked = true;

            ServerLevel level = caster.serverLevel();
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getViewVector(1.0f);
            double radius = StatFormulas.ASTRAL_ANNIHILATION_MIN_RADIUS
                    + (StatFormulas.ASTRAL_ANNIHILATION_MAX_RADIUS - StatFormulas.ASTRAL_ANNIHILATION_MIN_RADIUS) * charge;
            Vec3 far = eye.add(look.scale(StatFormulas.ASTRAL_ANNIHILATION_RANGE));
            BlockHitResult bhr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = bhr.getType() != HitResult.Type.MISS ? bhr.getLocation() : far;

            beamFx(level, eye, end, radius, charge);

            if (now >= ch.nextDamageTick) {
                ch.nextDamageTick += StatFormulas.ASTRAL_ANNIHILATION_DAMAGE_INTERVAL;
                float dmg = (float) (StatFormulas.astralAnnihilationTickDamage(charge, ch.spellLevel)
                        * StatFormulas.spellDamageMultiplier(s) * Afflictions.spellDamageMult(caster));
                int steps = Math.max(4, (int) (eye.distanceTo(end) * 2));
                java.util.Set<Integer> hitIds = new java.util.HashSet<>();
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = eye.lerp(end, i / (double) steps);
                    for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(p, p).inflate(radius + 0.3), LivingEntity::isAlive)) {
                        if (le == caster || !hitIds.add(le.getId())) {
                            continue;
                        }
                        boolean foe = le instanceof Enemy
                                || (le instanceof Player pl && !ClanActions.areFriendly(server, caster.getUUID(), pl.getUUID()));
                        if (!foe) {
                            continue;
                        }
                        le.hurt(level.damageSources().indirectMagic(caster, caster), dmg);
                        le.hurtMarked = true;
                    }
                }
            }

            if (now >= ch.nextDigTick) {
                ch.nextDigTick += StatFormulas.ASTRAL_ANNIHILATION_DIG_INTERVAL;
                digAt(level, caster, end, radius);
            }
            return false;
        });
    }

    private static void beamFx(ServerLevel level, Vec3 from, Vec3 to, double radius, double charge) {
        int steps = Math.max(6, (int) (from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(new DustParticleOptions(BEAM_COLOUR, 1.0f + (float) (charge * 1.5)),
                    p.x, p.y, p.z, 1, radius * 0.2, radius * 0.2, radius * 0.2, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, radius * 0.15, radius * 0.15, radius * 0.15, 0.0);
            }
        }
        level.playSound(null, BlockPos.containing(to), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f + (float) charge, 0.5f + (float) charge);
    }

    private static void digAt(ServerLevel level, ServerPlayer caster, Vec3 at, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos centre = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (p.distSqr(centre) > radius * radius) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.isAir()) {
                continue;
            }
            float hardness = state.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > StatFormulas.ASTRAL_ANNIHILATION_DIG_HARDNESS_CAP) {
                continue; // unbreakable or too tough (bedrock, obsidian-tier)
            }
            if (!ProtectionEvents.canModifyBlock(level, p, caster)) {
                continue;
            }
            level.levelEvent(2001, p, Block.getId(state));
            level.setBlockAndUpdate(p.immutable(), Blocks.AIR.defaultBlockState());
        }
    }
}
