package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.StatFormulas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mana Rift: a small zone of cracked, destabilized magic. Anyone inside takes
 * periodic damage and, if they're a player mid-cast, gets interrupted - the
 * rift itself is what disrupts them, not a one-off effect on entry.
 */
public final class ManaRiftManager {

    private static final class Rift {
        final UUID owner;
        final String dimension;
        final Vec3 center;
        final int spellLevel;
        final long endTick;
        long nextTick;

        Rift(UUID owner, String dimension, Vec3 center, int spellLevel, long now) {
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.spellLevel = spellLevel;
            this.endTick = now + StatFormulas.MANA_RIFT_TICKS;
            this.nextTick = now + StatFormulas.MANA_RIFT_DAMAGE_INTERVAL;
        }
    }

    private static final List<Rift> rifts = new ArrayList<>();

    private ManaRiftManager() {
    }

    public static void create(ServerPlayer caster, Vec3 centre, int spellLevel) {
        ServerLevel level = caster.serverLevel();
        rifts.add(new Rift(caster.getUUID(), level.dimension().location().toString(), centre, spellLevel, level.getGameTime()));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y + 0.1, centre.z, 40, 1.4, 0.3, 1.4, 0.02);
        level.playSound(null, net.minecraft.core.BlockPos.containing(centre), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (rifts.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        rifts.removeIf(rift -> {
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(rift.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) {
                return true;
            }
            Vec3 c = rift.center;
            double r = StatFormulas.MANA_RIFT_RADIUS;

            // cracked-glass ring, low to the ground
            for (int i = 0; i < 10; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double rad = r * (0.5 + level.random.nextDouble() * 0.5);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        c.x + Math.cos(a) * rad, c.y + 0.05, c.z + Math.sin(a) * rad, 1, 0, 0, 0, 0);
            }

            boolean damageTick = now >= rift.nextTick;
            if (damageTick) {
                rift.nextTick += StatFormulas.MANA_RIFT_DAMAGE_INTERVAL;
                ServerPlayer owner = server.getPlayerList().getPlayer(rift.owner);
                double mult = owner != null
                        ? StatFormulas.spellDamageMultiplier(net.robmc.rpgstats.RpgManager.stats(owner)) : 1.0;
                float dmg = (float) (StatFormulas.manaRiftTickDamage(rift.spellLevel) * mult);
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(c, c).inflate(r, 2.0, r), LivingEntity::isAlive)) {
                    if (le == owner) {
                        continue;
                    }
                    boolean foe = le instanceof net.minecraft.world.entity.monster.Enemy
                            || (le instanceof net.minecraft.world.entity.player.Player p && owner != null
                                && !net.robmc.claimguard.clan.ClanActions.areFriendly(server, owner.getUUID(), p.getUUID()));
                    if (!foe) {
                        continue;
                    }
                    if (le instanceof ServerPlayer sp) {
                        SpellCasting.interrupt(sp);
                    }
                    le.hurt(owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), dmg);
                    level.sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
            return now >= rift.endTick;
        });
    }

    public static void clearAll() {
        rifts.clear();
    }
}
