package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
        if (result instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target && target != getOwner()) {
            target.hurt(this.damageSources().indirectMagic(this, getOwner()), this.entityData.get(DAMAGE));
        }
        ServerLevel level = (ServerLevel) this.level();
        level.sendParticles(new DustParticleOptions(BLUE, 2.0f), getX(), getY(), getZ(), 18, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 1.4f);
        this.discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }
}
