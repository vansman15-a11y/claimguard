package net.robmc.rpgstats.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Earthen Path: a mossy patch on the ground. Anyone standing on it is slowed 15%
 * and chipped for a little damage each second. Touch it with a fire spell and it
 * combusts - for a few seconds it deals bonus damage and slams 3 burn stacks
 * onto anyone in it, then it's spent.
 */
public final class EarthenPathManager {

    private static final class Path {
        final UUID owner;
        final String dimension;
        final Vec3 center;
        final int spellLevel;
        final List<BlockPos> carpet = new ArrayList<>();
        long endTick;
        long nextDamageTick;
        boolean ignited;

        Path(UUID owner, String dimension, Vec3 center, int spellLevel, long now) {
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.spellLevel = spellLevel;
            this.endTick = now + StatFormulas.EARTHEN_PATH_TICKS;
            this.nextDamageTick = now + StatFormulas.EARTHEN_PATH_DAMAGE_INTERVAL;
        }
    }

    private static final List<Path> paths = new ArrayList<>();

    private EarthenPathManager() {
    }

    public static void create(ServerPlayer caster, Vec3 centre, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        Path p = new Path(caster.getUUID(), level.dimension().location().toString(), centre, spellLevel, level.getGameTime());
        int r = (int) Math.ceil(StatFormulas.EARTHEN_PATH_RADIUS);
        double r2 = StatFormulas.EARTHEN_PATH_RADIUS * StatFormulas.EARTHEN_PATH_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r2) {
                    continue;
                }
                BlockPos ground = surface(level, centre.x + dx, centre.y, centre.z + dz);
                if (ground == null) {
                    continue;
                }
                BlockPos on = ground.above();
                if (level.getBlockState(on).isAir()
                        && net.robmc.claimguard.event.ProtectionEvents.canModifyBlock(level, on, caster)) {
                    level.setBlockAndUpdate(on, Blocks.MOSS_CARPET.defaultBlockState());
                    p.carpet.add(on.immutable());
                }
            }
        }
        paths.add(p);
        level.playSound(null, BlockPos.containing(centre), SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.1f, 0.8f);
    }

    /** A fire spell touched down near {@code at} - light any un-lit path it overlaps. */
    public static void tryIgnite(ServerLevel level, Vec3 at) {
        String dim = level.dimension().location().toString();
        for (Path p : paths) {
            if (p.ignited || !p.dimension.equals(dim)) {
                continue;
            }
            double dx = at.x - p.center.x;
            double dz = at.z - p.center.z;
            double reach = StatFormulas.EARTHEN_PATH_RADIUS + 1.5;
            if (dx * dx + dz * dz <= reach * reach && Math.abs(at.y - p.center.y) < 3.0) {
                p.ignited = true;
                p.endTick = level.getGameTime() + StatFormulas.EARTHEN_PATH_COMBUST_TICKS;
                p.nextDamageTick = level.getGameTime(); // combust hits immediately
                level.playSound(null, BlockPos.containing(p.center), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4f, 0.9f);
            }
        }
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (paths.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        paths.removeIf(p -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(p.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            if (now >= p.endTick) {
                for (BlockPos c : p.carpet) {
                    if (level.getBlockState(c).is(Blocks.MOSS_CARPET)) {
                        level.setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());
                        if (p.ignited) {
                            level.levelEvent(2001, c, Block.getId(Blocks.MOSS_CARPET.defaultBlockState()));
                        }
                    }
                }
                return true;
            }

            double r = StatFormulas.EARTHEN_PATH_RADIUS;
            if (p.ignited) {
                level.sendParticles(ParticleTypes.FLAME, p.center.x, p.center.y + 0.2, p.center.z,
                        (int) (r * 8), r * 0.6, 0.15, r * 0.6, 0.02);
            } else {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.center.x, p.center.y + 0.3, p.center.z,
                        (int) (r * 4), r * 0.6, 0.2, r * 0.6, 0.0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.center.x, p.center.y + 0.15, p.center.z,
                        (int) r, r * 0.6, 0.05, r * 0.6, 0.0);
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(p.owner);
            double r2 = r * r;
            List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(p.center.x - r, p.center.y - 1.5, p.center.z - r,
                            p.center.x + r, p.center.y + 2.5, p.center.z + r),
                    e -> e.isAlive() && sq(e.getX() - p.center.x) + sq(e.getZ() - p.center.z) <= r2);

            for (LivingEntity le : inside) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false, true));
            }

            while (now >= p.nextDamageTick) {
                p.nextDamageTick += StatFormulas.EARTHEN_PATH_DAMAGE_INTERVAL;
                float chip = (float) StatFormulas.earthenPathChipDamage(p.spellLevel);
                for (LivingEntity le : inside) {
                    if (p.ignited) {
                        le.hurt(le.damageSources().onFire(), chip + (float) StatFormulas.EARTHEN_PATH_COMBUST_BONUS);
                        for (int i = 0; i < StatFormulas.FIRE_BURN_MAX_STACKS; i++) {
                            BurnManager.apply(le, StatFormulas.fireBurnPerStack(p.spellLevel));
                        }
                    } else {
                        le.hurt(le.damageSources().magic(), chip);
                    }
                }
                if (owner != null) {
                    RpgManager.stats(owner); // keep the reference warm; XP handled elsewhere
                }
            }
            return false;
        });
    }

    private static double sq(double v) {
        return v * v;
    }

    private static BlockPos surface(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y + 2, z);
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
            pos = pos.below();
        }
        return null;
    }

    public static void clearAll() {
        paths.clear();
    }
}
