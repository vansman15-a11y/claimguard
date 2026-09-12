package net.robmc.combat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parry: holding right-click with a melee weapon out (never a staff - those simply aren't
 * melee weapons, see {@link CombatEvents#isMeleeWeapon}) raises it to block. A one-handed
 * weapon with a shield in the offhand blocks harder than the weapon alone. Entirely custom -
 * vanilla's own shield-block is cancelled outright (see {@code CombatEvents.onRightClickItem})
 * so this flat percentage is the only mitigation in play, not an addition on top of it. Only
 * covers a frontal cone (see {@link CombatConfig#PARRY_CONE_HALF_ANGLE_DEG}) - getting hit from
 * behind still does full damage. Attacking and sprinting are both locked out while parrying
 * (see CombatEvents.tryStartSwing and {@link #tick}). There's no shield-raise animation (that
 * needs a custom player-model layer, out of scope here), so a small particle glint stands in
 * for it in front of the player while they're parrying.
 */
public final class ParryManager {

    private static final Set<UUID> parrying = new HashSet<>();
    private static final Vector3f BLOCK_FX_COLOUR = new Vector3f(0.75f, 0.8f, 0.9f);

    private ParryManager() {
    }

    public static boolean isParrying(UUID id) {
        return parrying.contains(id);
    }

    public static void setParrying(ServerPlayer player, boolean active) {
        if (active) {
            parrying.add(player.getUUID());
        } else {
            parrying.remove(player.getUUID());
        }
    }

    public static void clear(UUID id) {
        parrying.remove(id);
    }

    /** Called every player tick from CombatEvents - can't sprint while parrying, and shows the block fx. */
    public static void tick(ServerPlayer player) {
        if (!parrying.contains(player.getUUID())) {
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        if (player.tickCount % 4 == 0) {
            spawnBlockFx(player);
        }
    }

    /**
     * The damage reduction fraction in effect for this player right now, 0 if not parrying, not
     * eligible, or the attacker isn't within the frontal block cone. {@code attacker} may be null
     * for non-entity damage sources, which parry never covers.
     */
    public static float damageReduction(ServerPlayer defender, LivingEntity attacker) {
        if (!parrying.contains(defender.getUUID())) {
            return 0f;
        }
        ItemStack main = defender.getMainHandItem();
        if (!CombatEvents.isMeleeWeapon(main)) {
            return 0f; // no staves, no bare hands, no ranged weapons
        }
        if (attacker == null || !inFrontalCone(defender, attacker)) {
            return 0f; // hit from behind/outside the cone - the block doesn't cover it
        }
        boolean shield = defender.getOffhandItem().getItem() instanceof ShieldItem;
        return (float) (shield ? CombatConfig.PARRY_SHIELD_REDUCTION : CombatConfig.PARRY_WEAPON_REDUCTION);
    }

    private static boolean inFrontalCone(ServerPlayer defender, LivingEntity attacker) {
        Vec3 look = defender.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0, look.z);
        Vec3 toAttacker = attacker.position().subtract(defender.position());
        Vec3 towardFlat = new Vec3(toAttacker.x, 0, toAttacker.z);
        if (lookFlat.lengthSqr() < 1.0e-6 || towardFlat.lengthSqr() < 1.0e-6) {
            return true; // degenerate (attacker essentially on top of the defender) - don't punish it
        }
        double cos = lookFlat.normalize().dot(towardFlat.normalize());
        return cos >= Math.cos(Math.toRadians(CombatConfig.PARRY_CONE_HALF_ANGLE_DEG));
    }

    /** A small glinting grid of particles in front of the chest, standing in for a shield-raise animation. */
    private static void spawnBlockFx(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 centre = player.position().add(flat.scale(0.8)).add(0, player.getBbHeight() * 0.55, 0);
        for (int col = -1; col <= 1; col++) {
            for (int row = 0; row <= 1; row++) {
                Vec3 p = centre.add(right.scale(col * 0.22)).add(0, row * 0.28 - 0.14, 0);
                level.sendParticles(new DustParticleOptions(BLOCK_FX_COLOUR, 1.0f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }
}
