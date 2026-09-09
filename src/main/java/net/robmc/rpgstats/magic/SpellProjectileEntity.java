package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ScreenFlashPacket;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.registry.RpgEntities;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/** One entity for every Adept-school projectile; behaviour switches on the spell it carries. */
public class SpellProjectileEntity extends ThrowableProjectile {

    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SPELL_ID =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.INT);

    public SpellProjectileEntity(EntityType<? extends SpellProjectileEntity> type, Level level) {
        super(type, level);
    }

    public SpellProjectileEntity(Level level, LivingEntity owner, Spell spell, float damage) {
        super(RpgEntities.SPELL_PROJECTILE.get(), owner, level);
        this.entityData.set(SPELL_ID, spell.ordinal());
        this.entityData.set(DAMAGE, damage);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DAMAGE, 0.0f);
        this.entityData.define(SPELL_ID, Spell.SUNDER.ordinal());
    }

    public Spell spell() {
        int i = this.entityData.get(SPELL_ID);
        Spell[] all = Spell.values();
        return i >= 0 && i < all.length ? all[i] : Spell.SUNDER;
    }

    private Vector3f colour() {
        return switch (spell()) {
            case HEAL_OTHER -> new Vector3f(1.0f, 0.35f, 0.4f);
            case AWAY -> new Vector3f(0.55f, 0.85f, 1.0f);
            case SCATTER -> new Vector3f(0.7f, 1.0f, 0.6f);
            case BRIGHT_LIGHT -> new Vector3f(1.0f, 1.0f, 0.85f);
            default -> new Vector3f(1.0f, 0.2f, 0.15f); // Sunder red
        };
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.level().addParticle(new DustParticleOptions(colour(), 1.5f), getX(), getY(), getZ(), 0, 0, 0);
        }
        if (this.tickCount > 120) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Spell spell = spell();
        float damage = this.entityData.get(DAMAGE);
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        LivingEntity hit = null;
        if (result instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target && target != owner) {
            hit = target;
        }
        Vec3 at = result.getLocation();
        Vec3 travel = getDeltaMovement().lengthSqr() > 1.0e-4 ? getDeltaMovement().normalize() : getViewVector(1.0f);

        switch (spell) {
            case SUNDER -> {
                if (hit != null) {
                    hit.hurt(damageSources().indirectMagic(this, owner), (float) StatFormulas.SUNDER_IMPACT_DAMAGE);
                    int schoolLvl = casterSchoolLevel(spell);
                    BleedManager.start(hit, owner, (float) StatFormulas.sunderBleedPerTick(schoolLvl),
                            StatFormulas.SUNDER_BLEED_TICKS);
                }
                level.sendParticles(new DustParticleOptions(colour(), 1.6f), at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.02);
            }
            case HEAL_OTHER -> {
                if (hit != null) {
                    hit.heal((float) StatFormulas.healOtherAmount(casterSchoolLevel(spell)));
                    pulseRing(level, hit, new Vector3f(1.0f, 0.3f, 0.35f));
                    level.playSound(null, hit.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
                }
            }
            case AWAY -> {
                if (hit != null) {
                    hit.hurt(damageSources().indirectMagic(this, owner), (float) StatFormulas.AWAY_IMPACT_DAMAGE);
                    Vec3 push = new Vec3(travel.x, 0.0, travel.z).normalize().scale(StatFormulas.AWAY_KNOCKBACK);
                    hit.push(push.x, 0.4, push.z);
                    hit.hurtMarked = true;
                }
                maybeSelfBuff(level, owner, at);
            }
            case SCATTER -> {
                if (hit != null) {
                    hit.hurt(damageSources().indirectMagic(this, owner), (float) StatFormulas.SCATTER_IMPACT_DAMAGE);
                    hit.push(0, StatFormulas.SCATTER_LAUNCH, 0);
                    hit.hurtMarked = true;
                } else if (owner != null && at.distanceToSqr(owner.position()) <= StatFormulas.SCATTER_SELF_RANGE * StatFormulas.SCATTER_SELF_RANGE) {
                    owner.push(0, StatFormulas.SCATTER_LAUNCH, 0);
                    owner.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.2, 0.1, 0.2, 0.05);
            }
            case BRIGHT_LIGHT -> {
                double r = StatFormulas.BRIGHT_LIGHT_RADIUS;
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.25);
                level.playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
                for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r))) {
                    if (le == owner || !le.isAlive()) {
                        continue;
                    }
                    Vec3 toBlast = at.subtract(le.getEyePosition()).normalize();
                    if (le.getViewVector(1.0f).dot(toBlast) > StatFormulas.BRIGHT_LIGHT_FACING_DOT) {
                        le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, StatFormulas.BRIGHT_LIGHT_BLIND_TICKS, 0, false, false, true));
                        if (le instanceof ServerPlayer sp) {
                            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                                    new ScreenFlashPacket(StatFormulas.BRIGHT_LIGHT_FLASH_TICKS));
                        }
                    }
                }
            }
            default -> {
            }
        }
        this.discard();
    }

    private int casterSchoolLevel(Spell spell) {
        if (getOwner() instanceof ServerPlayer sp) {
            return net.robmc.rpgstats.RpgManager.stats(sp).getSchoolLevel(spell.school());
        }
        return 1;
    }

    private void maybeSelfBuff(ServerLevel level, LivingEntity owner, Vec3 impact) {
        if (owner == null || impact.distanceToSqr(owner.position()) > StatFormulas.AWAY_SELF_RANGE * StatFormulas.AWAY_SELF_RANGE) {
            return;
        }
        int t = StatFormulas.AWAY_SELF_BUFF_TICKS;
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 1, false, true, true));
        owner.addEffect(new MobEffectInstance(MobEffects.JUMP, t, 1, false, true, true));
        level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.9f, 1.0f), 1.0f),
                owner.getX(), owner.getY() + 0.1, owner.getZ(), 16, 0.35, 0.05, 0.35, 0.02);
    }

    private static void pulseRing(ServerLevel level, LivingEntity target, Vector3f colour) {
        DustParticleOptions dust = new DustParticleOptions(colour, 1.3f);
        double y = target.getY() + target.getBbHeight() * 0.5;
        for (int i = 0; i < 16; i++) {
            double a = i * (Math.PI * 2 / 16);
            level.sendParticles(dust, target.getX() + Math.cos(a) * 0.9, y, target.getZ() + Math.sin(a) * 0.9,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
