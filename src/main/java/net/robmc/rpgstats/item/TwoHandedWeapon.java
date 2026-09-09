package net.robmc.rpgstats.item;

/**
 * Marker for weapons that need both hands: they can never sit in the offhand
 * slot, and while one is in the main hand the offhand must be empty.
 * Enforced each tick in RpgEvents.
 */
public interface TwoHandedWeapon {
}
