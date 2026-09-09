package net.robmc.rpgstats.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * A fast, light blade. Weak per hit but swings quickly, and unlike the polearm
 * and staff it can be held in the offhand (dual-wield).
 */
public class DaggerItem extends SwordItem {

    public DaggerItem(Properties properties) {
        // iron tier, small damage bonus, quick recovery
        super(Tiers.IRON, 1, -1.6f, properties);
    }
}
