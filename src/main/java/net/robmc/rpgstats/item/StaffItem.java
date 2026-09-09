package net.robmc.rpgstats.item;

import net.minecraft.world.item.Item;

/**
 * A caster's focus. Two-handed (can't go in the offhand, forces the offhand
 * empty). Poor as a melee weapon, but it's the only way to cast anything beyond
 * the Weak Magic transfers - offensive spells need a staff in hand.
 */
public class StaffItem extends Item implements TwoHandedWeapon {

    public StaffItem(Properties properties) {
        super(properties);
    }
}
