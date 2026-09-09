package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.registry.RpgEntities;
import org.joml.Vector3f;

/** A slow blue orb. Flies straight, bursts on impact and hits whatever it touched. */
public class MagicBoltEntity extends ThrowableProjectile {

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(MagicBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final Vector3f BLUE = new Vector3f(0.25f, 0.45f, 1.0f);

    public MagicBoltEntity(EntityType<? extends MagicBoltEntity> type, Level level) {
        super(type, level);
    }

    public MagicBoltEntity(Level level, LivingEntity owner, float damage) {
        super(RpgEntities.MAGIC_BOLT.get(), owner, level);
        this.entityData.set(DAMAGE, damage);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DAMAGE, 0.0f);
    }

    @Override
    public boolean isNoGravity() {
        return true; // ThrowableProjectile.tick() skips gravity when this is true
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.level().addParticle(new DustParticleOptions(BLUE, 1.6f),
                    getX(), getY(), getZ(), 0, 0, 0);
        }
        if (this.tickCount > 120) { // ~6s failsafe
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Entity owner = getOwner();
        float direct = this.entityData.get(DAMAGE);
        int enemiesHit = 0;
        Entity directHit = null;
        if (result instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target && target != owner) {
            directHit = target;
            target.hurt(this.damageSources().indirectMagic(this, owner), direct);
            if (isEnemy(target, owner)) {
                enemiesHit++;
            }
        }

        // Splash: nearby living things take a fraction of a direct hit - including you, if you shot your own feet.
        float splash = (float) (direct * StatFormulas.MAGIC_BOLT_SPLASH_FRACTION);
        double radius = StatFormulas.MAGIC_BOLT_SPLASH_RADIUS;
        if (splash > 0.0f) {
            AABB box = this.getBoundingBox().inflate(radius);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (le == directHit || !le.isAlive()) {
                    continue;
                }
                if (le.distanceToSqr(this) <= radius * radius) {
                    le.hurt(le == owner ? this.damageSources().magic()
                            : this.damageSources().indirectMagic(this, owner), splash);
                    if (isEnemy(le, owner)) {
                        enemiesHit++;
                    }
                }
            }
        }

        if (owner instanceof ServerPlayer caster && enemiesHit > 0) {
            net.robmc.rpgstats.RpgManager.addSpellXp(caster, Spell.MAGIC_BOLT,
                    StatFormulas.spellHitXp(enemiesHit));
        }

        level.sendParticles(new DustParticleOptions(BLUE, 2.0f), getX(), getY(), getZ(), 18, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 1.4f);
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    private static boolean isEnemy(Entity e, Entity owner) {
        return e != null && e != owner && (e instanceof Enemy || e instanceof Player);
    }
}
