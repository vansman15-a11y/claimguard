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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bloom of Renewal: an instant cleanse plus a short heal-over-time on an ally,
 * and a little patch of grass springs up under them for the duration.
 */
public final class BloomManager {

    private static final class Bloom {
        final UUID caster;
        final int targetId;
        final String dimension;
        final int schoolLevel;
        final long endTick;
        long nextHealTick;

        Bloom(UUID caster, int targetId, String dimension, int schoolLevel, long now) {
            this.caster = caster;
            this.targetId = targetId;
            this.dimension = dimension;
            this.schoolLevel = schoolLevel;
            this.endTick = now + StatFormulas.BLOOM_TICKS;
            this.nextHealTick = now + StatFormulas.BLOOM_HEAL_INTERVAL;
        }
    }

    private static final List<Bloom> blooms = new ArrayList<>();

    private BloomManager() {
    }

    public static void create(ServerPlayer caster, LivingEntity target, int schoolLevel) {
        ServerLevel level = caster.serverLevel();
        long now = level.getGameTime();
        cleanse(target);
        blooms.removeIf(b -> b.targetId == target.getId());
        blooms.add(new Bloom(caster.getUUID(), target.getId(), level.dimension().location().toString(), schoolLevel, now));
        growPatch(caster, target);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + 1.0, target.getZ(), 40, 0.5, 0.9, 0.5, 0.03);
        level.sendParticles(ParticleTypes.COMPOSTER,
                target.getX(), target.getY() + 0.2, target.getZ(), 30, 0.6, 0.1, 0.6, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.1f, 1.2f);
        if (target instanceof ServerPlayer sp && sp != caster) {
            sp.displayClientMessage(Component.literal("Renewing flowers bloom around you.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Strip harmful vanilla effects and every custom debuff off a target. */
    public static void cleanse(LivingEntity target) {
        List<net.minecraft.world.effect.MobEffect> bad = new ArrayList<>();
        for (MobEffectInstance mei : target.getActiveEffects()) {
            if (!mei.getEffect().isBeneficial()) {
                bad.add(mei.getEffect());
            }
        }
        bad.forEach(target::removeEffect);
        BleedManager.clear(target.getId());
        BurnManager.clear(target.getId());
        DiseaseManager.clear(target.getId());
        Afflictions.clear(target);
        if (target instanceof Player p) {
            Silence.clear(p.getUUID());
        }
    }

    private static void growPatch(ServerPlayer caster, LivingEntity target) {
        ServerLevel level = caster.serverLevel();
        int r = StatFormulas.BLOOM_PATCH_RADIUS;
        BlockPos foot = target.blockPosition();
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) {
                    continue;
                }
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos ground = foot.offset(dx, dy, dz);
                    if (level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)
                            && (level.getBlockState(ground.above()).isAir()
                                || level.getBlockState(ground.above()).canBeReplaced())) {
                        spots.add(ground.above());
                        break;
                    }
                }
            }
        }
        ConjuredBlocks.place(caster, spots, Blocks.GRASS.defaultBlockState(), StatFormulas.BLOOM_TICKS + 60);
    }

    public static void clearTarget(int entityId) {
        blooms.removeIf(b -> b.targetId == entityId);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (blooms.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        blooms.removeIf(b -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(b.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null || !(level.getEntity(b.targetId) instanceof LivingEntity target) || !target.isAlive()) {
                return true;
            }
            if (now >= b.nextHealTick) {
                b.nextHealTick += StatFormulas.BLOOM_HEAL_INTERVAL;
                float heal = (float) StatFormulas.bloomHealPerTick(b.schoolLevel);
                target.heal(heal);
                level.sendParticles(ParticleTypes.HEART,
                        target.getX(), target.getY() + target.getBbHeight() + 0.2, target.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        target.getX(), target.getY() + 0.3, target.getZ(), 2, 0.4, 0.1, 0.4, 0.0);
            }
            return now >= b.endTick;
        });
    }

    public static void clearAll() {
        blooms.clear();
    }
}
