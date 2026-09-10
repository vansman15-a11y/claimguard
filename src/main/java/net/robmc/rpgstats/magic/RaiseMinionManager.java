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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SpellCooldownPacket;
import net.robmc.claimguard.clan.ClanActions;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Raise Minion: a skeleton conjured from the ground that fights for the caster for 30 s. */
public final class RaiseMinionManager {

    private static final class Minion {
        final UUID owner;
        final long dieTick;

        Minion(UUID owner, long dieTick) {
            this.owner = owner;
            this.dieTick = dieTick;
        }
    }

    private static final Map<Integer, Minion> minions = new HashMap<>();
    private static final Map<UUID, Deque<Long>> casts = new HashMap<>();

    private static final double FOLLOW_START_SQ = 6.0 * 6.0;    // starts pathing back to the owner past this
    private static final double LEASH_SQ = 24.0 * 24.0;         // yanked to the owner's side past this
    private static final double FOLLOW_SPEED = 1.35;
    private static final double RETARGET_RANGE = 20.0;

    private RaiseMinionManager() {
    }

    /** The UUID of the player a minion serves, or null if the entity isn't a minion. */
    public static UUID ownerOf(int entityId) {
        Minion m = minions.get(entityId);
        return m == null ? null : m.owner;
    }

    private static int charges(int spellLevel) {
        return spellLevel >= 75 ? 2 : 1;
    }

    /** Do you have a charge ready? */
    public static boolean canCast(ServerPlayer caster, int spellLevel) {
        long now = caster.serverLevel().getGameTime();
        Deque<Long> d = casts.computeIfAbsent(caster.getUUID(), k -> new ArrayDeque<>());
        d.removeIf(t -> now - t >= StatFormulas.RAISE_MINION_CHARGE_TICKS);
        return d.size() < charges(spellLevel);
    }

    private static int aliveCount(UUID owner) {
        int n = 0;
        for (Minion m : minions.values()) {
            if (m.owner.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public static boolean atCap(ServerPlayer caster, int spellLevel) {
        return aliveCount(caster.getUUID()) >= StatFormulas.raiseMinionCap(spellLevel);
    }

    /** A minion can't be raised inside a safe zone / newbie-protected area. */
    public static boolean blockedHere(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        Vec3 look = caster.getViewVector(1.0f);
        BlockPos spot = BlockPos.containing(caster.position().add(look.x * 2.0, 0, look.z * 2.0));
        return net.robmc.claimguard.event.ProtectionEvents.inSafeZone(level, caster.blockPosition())
                || net.robmc.claimguard.event.ProtectionEvents.inSafeZone(level, spot);
    }

    public static void raise(ServerPlayer caster, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        if (blockedHere(caster)) {
            caster.displayClientMessage(Component.literal("The dead won't rise in a warded place.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 spot = caster.position().add(look.x * 2.0, 0, look.z * 2.0);
        BlockPos ground = groundAt(level, spot.x, caster.getY() + 1, spot.z);
        Vec3 at = ground != null ? Vec3.atBottomCenterOf(ground.above()) : caster.position();

        Skeleton sk = EntityType.SKELETON.create(level);
        if (sk == null) {
            return;
        }
        sk.moveTo(at.x, at.y, at.z, caster.getYRot(), 0);
        sk.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.MOB_SUMMONED, null, null);
        sk.setPersistenceRequired();
        sk.addEffect(new MobEffectInstance(MobEffects.GLOWING, StatFormulas.RAISE_MINION_LIFETIME, 0, false, false, false));
        // a skull "helmet" stops it burning in daylight; fire resistance covers lava and fire spells
        sk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
        sk.setDropChance(EquipmentSlot.HEAD, 0.0f);
        sk.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, StatFormulas.RAISE_MINION_LIFETIME, 0, false, false, false));
        sk.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA, 8.0f);
        level.addFreshEntity(sk);

        minions.put(sk.getId(), new Minion(caster.getUUID(), level.getGameTime() + StatFormulas.RAISE_MINION_LIFETIME));
        Deque<Long> d = casts.computeIfAbsent(caster.getUUID(), k -> new ArrayDeque<>());
        d.addLast(level.getGameTime());

        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.2, at.z, 40, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 16, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.1f, 0.6f);

        sendChargeCooldown(caster, spellLevel);
        caster.displayClientMessage(Component.literal("A skeleton claws its way up to serve you.")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    private static void sendChargeCooldown(ServerPlayer caster, int spellLevel) {
        long now = caster.serverLevel().getGameTime();
        Deque<Long> d = casts.get(caster.getUUID());
        int left = charges(spellLevel) - (d == null ? 0 : d.size());
        int cd = 0;
        if (left <= 0 && d != null && !d.isEmpty()) {
            cd = (int) Math.max(1, StatFormulas.RAISE_MINION_CHARGE_TICKS - (now - d.peekFirst()));
        }
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> caster),
                new SpellCooldownPacket(Spell.RAISE_MINION.name(), cd));
    }

    public static void clear(UUID id) {
        casts.remove(id);
        minions.values().removeIf(m -> m.owner.equals(id));
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (minions.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        minions.entrySet().removeIf(entry -> {
            Entity ent = null;
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                Entity e = sl.getEntity(entry.getKey());
                if (e != null) {
                    ent = e;
                    level = sl;
                    break;
                }
            }
            if (!(ent instanceof Skeleton sk) || !sk.isAlive()) {
                return true;
            }
            if (now >= entry.getValue().dieTick) {
                ((ServerLevel) sk.level()).sendParticles(ParticleTypes.SOUL, sk.getX(), sk.getY() + 0.5, sk.getZ(),
                        30, 0.3, 0.5, 0.3, 0.03);
                sk.discard();
                return true;
            }

            // never burns in the sun
            if (sk.isOnFire()) {
                sk.clearFire();
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner);
            if (owner == null || level == null) {
                return false;
            }

            // follow the owner across dimensions - just yank it along
            if (owner.level() != sk.level()) {
                sk.teleportTo(owner.serverLevel(), owner.getX(), owner.getY(), owner.getZ(), java.util.Set.of(), owner.getYRot(), 0);
                return false;
            }

            if (sk.getLastHurtByMob() == owner) {
                sk.setLastHurtByMob(null);
            }
            LivingEntity t = sk.getTarget();
            if (t == owner || (t instanceof Player tp && ClanActions.areFriendly(server, owner.getUUID(), tp.getUUID()))) {
                sk.setTarget(null);
                t = null;
            }
            boolean hasTarget = t != null && t.isAlive();
            if (!hasTarget) {
                LivingEntity best = null;
                double bestSq = RETARGET_RANGE * RETARGET_RANGE;
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, sk.getBoundingBox().inflate(RETARGET_RANGE))) {
                    if (le == sk || le == owner || !le.isAlive()) {
                        continue;
                    }
                    boolean hostile = le instanceof Enemy
                            || (le instanceof Player p && !ClanActions.areFriendly(server, owner.getUUID(), p.getUUID()));
                    if (!hostile) {
                        continue;
                    }
                    double dsq = sk.distanceToSqr(le);
                    if (dsq < bestSq) {
                        bestSq = dsq;
                        best = le;
                    }
                }
                if (best != null) {
                    sk.setTarget(best);
                    hasTarget = true;
                }
            }

            // stick close to the owner when there's nothing to kill
            double ownerSq = sk.distanceToSqr(owner);
            if (ownerSq > LEASH_SQ) {
                Vec3 back = owner.position().add((sk.getRandom().nextDouble() - 0.5) * 2, 0, (sk.getRandom().nextDouble() - 0.5) * 2);
                sk.teleportTo(back.x, back.y, back.z);
                sk.getNavigation().stop();
            } else if (!hasTarget && ownerSq > FOLLOW_START_SQ) {
                sk.getNavigation().moveTo(owner, FOLLOW_SPEED);
            }
            return false;
        });
    }

    private static BlockPos groundAt(ServerLevel level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y + 1, z);
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                return p;
            }
            p = p.below();
        }
        return null;
    }

    public static void clearAll() {
        minions.clear();
        casts.clear();
    }
}
