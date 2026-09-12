package net.robmc.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parry: holding right-click with a melee weapon out (never a staff - those simply aren't
 * melee weapons, see {@link CombatEvents#isMeleeWeapon}) raises it to block. A one-handed
 * weapon with a shield in the offhand blocks harder than the weapon alone. Entirely custom -
 * vanilla's own shield-block is cancelled outright (see {@code CombatEvents.onRightClickItem})
 * so this flat percentage is the only mitigation in play, not an addition on top of it.
 * Attacking and sprinting are both locked out while parrying (see CombatEvents.tryStartSwing
 * and {@link #tick}).
 */
public final class ParryManager {

    private static final Set<UUID> parrying = new HashSet<>();

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

    /** Called every player tick from CombatEvents - can't sprint while parrying. */
    public static void tick(ServerPlayer player) {
        if (parrying.contains(player.getUUID()) && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    /** The damage reduction fraction in effect for this player right now, 0 if not parrying (or not eligible). */
    public static float damageReduction(ServerPlayer player) {
        if (!parrying.contains(player.getUUID())) {
            return 0f;
        }
        ItemStack main = player.getMainHandItem();
        if (!CombatEvents.isMeleeWeapon(main)) {
            return 0f; // no staves, no bare hands, no ranged weapons
        }
        boolean shield = player.getOffhandItem().getItem() instanceof ShieldItem;
        return (float) (shield ? CombatConfig.PARRY_SHIELD_REDUCTION : CombatConfig.PARRY_WEAPON_REDUCTION);
    }
}
