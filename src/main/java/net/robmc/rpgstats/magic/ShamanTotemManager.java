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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lightning and Healing totems: a 1x2 pillar of blocks anyone can knock down.
 * While it stands it pulses an aura - a buff + enemy shock for the lightning
 * totem, a periodic group heal for the healing one.
 */
public final class ShamanTotemManager {

    public enum Kind { LIGHTNING, HEALING }

    private static final class Totem {
        final UUID owner;
        final Kind kind;
        final int spellLevel;
        final String dimension;
        final BlockPos base;   // lower block; upper is base.above()
        final BlockState lower;
        final BlockState upper;
        final long endTick;
        long nextPulse;

        Totem(UUID owner, Kind kind, int spellLevel, String dim, BlockPos base,
              BlockState lower, BlockState upper, long now) {
            this.owner = owner;
            this.kind = kind;
            this.spellLevel = spellLevel;
            this.dimension = dim;
            this.base = base;
            this.lower = lower;
            this.upper = upper;
            this.endTick = now + (kind == Kind.HEALING ? StatFormulas.HEALING_TOTEM_LIFETIME : StatFormulas.TOTEM_LIFETIME);
            this.nextPulse = now + (kind == Kind.HEALING ? StatFormulas.HEALING_TOTEM_PULSE : StatFormulas.LIGHTNING_TOTEM_PULSE);
        }
    }

    private static final List<Totem> totems = new ArrayList<>();

    private ShamanTotemManager() {
    }

    /** Try to raise a totem a couple of blocks in front of the caster. */
    public static void raise(ServerPlayer caster, Kind kind, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        Vec3 look = caster.getViewVector(1.0f);
        BlockPos target = BlockPos.containing(caster.position().add(look.x * 1.6, 0, look.z * 1.6));

        BlockPos base = null;
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos foot = target.offset(0, dy, 0);
            BlockPos below = foot.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                    && replaceable(level.getBlockState(foot)) && replaceable(level.getBlockState(foot.above()))) {
                base = foot;
                break;
            }
        }
        if (base == null || !net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, base, caster)
                || !net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, base.above(), caster)) {
            caster.displayClientMessage(Component.literal("No room to plant the totem here.").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        BlockState lower = kind == Kind.LIGHTNING ? Blocks.WAXED_COPPER_BLOCK.defaultBlockState()
                : Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState upper = kind == Kind.LIGHTNING ? Blocks.LIGHTNING_ROD.defaultBlockState()
                : Blocks.FLOWERING_AZALEA.defaultBlockState();
        level.setBlockAndUpdate(base, lower);
        level.setBlockAndUpdate(base.above(), upper);
        totems.add(new Totem(caster.getUUID(), kind, spellLevel, level.dimension().location().toString(),
                base.immutable(), lower, upper, level.getGameTime()));

        level.sendParticles(kind == Kind.LIGHTNING ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.HAPPY_VILLAGER,
                base.getX() + 0.5, base.getY() + 1.2, base.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, base, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 1.2f);
        caster.displayClientMessage(Component.literal((kind == Kind.LIGHTNING ? "A lightning" : "A healing") + " totem rises.")
                .withStyle(kind == Kind.LIGHTNING ? ChatFormatting.AQUA : ChatFormatting.GREEN), true);
    }

    private static boolean replaceable(BlockState st) {
        return st.isAir() || st.canBeReplaced();
    }

    public static void clearOwner(UUID id) {
        totems.removeIf(t -> t.owner.equals(id));
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (totems.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        totems.removeIf(totem -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(totem.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            boolean intact = level.getBlockState(totem.base).is(totem.lower.getBlock())
                    && level.getBlockState(totem.base.above()).is(totem.upper.getBlock());
            if (!intact || now >= totem.endTick) {
                takeDown(level, totem);
                return true;
            }

            Vec3 centre = Vec3.atCenterOf(totem.base.above());
            if (now % 6 == 0) {
                level.sendParticles(totem.kind == Kind.LIGHTNING ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.COMPOSTER,
                        centre.x, centre.y, centre.z, 4, 0.3, 0.3, 0.3, 0.02);
            }
            if (now < totem.nextPulse) {
                return false;
            }
            totem.nextPulse += (totem.kind == Kind.HEALING ? StatFormulas.HEALING_TOTEM_PULSE : StatFormulas.LIGHTNING_TOTEM_PULSE);
            pulse(server, level, totem, centre);
            return false;
        });
    }

    private static void pulse(MinecraftServer server, ServerLevel level, Totem totem, Vec3 centre) {
        AABB box = new AABB(centre, centre).inflate(StatFormulas.TOTEM_AURA_RADIUS);
        if (totem.kind == Kind.HEALING) {
            float heal = (float) StatFormulas.healingTotemHeal(totem.spellLevel);
            for (Player p : level.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && ClanActions.areFriendly(server, totem.owner, pl.getUUID()))) {
                p.heal(heal);
                level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.2, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, centre.x, centre.y, centre.z, 16,
                    StatFormulas.TOTEM_AURA_RADIUS * 0.4, 0.6, StatFormulas.TOTEM_AURA_RADIUS * 0.4, 0.0);
        } else {
            float dmg = (float) StatFormulas.lightningTotemDamage(totem.spellLevel);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                boolean friend = le instanceof Player p && ClanActions.areFriendly(server, totem.owner, p.getUUID());
                boolean foe = le instanceof Enemy
                        || (le instanceof Player p2 && !ClanActions.areFriendly(server, totem.owner, p2.getUUID()));
                if (friend) {
                    le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, StatFormulas.LIGHTNING_TOTEM_BUFF_TICKS, 0, false, false, true));
                    le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, StatFormulas.LIGHTNING_TOTEM_BUFF_TICKS, 0, false, false, true));
                } else if (foe) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(totem.owner);
                    le.hurt(owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), dmg);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 10, 0.2, 0.3, 0.2, 0.1);
                }
            }
            level.playSound(null, BlockPos.containing(centre), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35f, 1.7f);
        }
    }

    private static void takeDown(ServerLevel level, Totem totem) {
        for (BlockPos p : new BlockPos[]{totem.base, totem.base.above()}) {
            BlockState here = level.getBlockState(p);
            if (here.is(totem.lower.getBlock()) || here.is(totem.upper.getBlock())) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                level.levelEvent(2001, p, Block.getId(here));
            }
        }
    }
}
