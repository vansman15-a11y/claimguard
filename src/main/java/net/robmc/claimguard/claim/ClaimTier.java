package net.robmc.claimguard.claim;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

/**
 * Defines each "level" a claim can be upgraded to.
 *
 * This is a Java enum: think of it like a fixed list of named constants,
 * except each constant here is allowed to carry its own data (radius, cost, item).
 * ClaimTier.LEVEL_1, ClaimTier.LEVEL_2 etc. are the only tiers that will ever exist
 * unless you add a new line below.
 */
public enum ClaimTier {

    // name        radius  upgradeCost  upgradeItem
    LEVEL_1(8, 8, Items.DIAMOND),
    LEVEL_2(16, 16, Items.DIAMOND),
    LEVEL_3(24, 32, Items.DIAMOND),
    LEVEL_4(32, 0, null); // 0 cost / null item = this is the max tier, no further upgrade

    private final int radius;
    private final int upgradeCost;
    private final Item upgradeItem;

    ClaimTier(int radius, int upgradeCost, Item upgradeItem) {
        this.radius = radius;
        this.upgradeCost = upgradeCost;
        this.upgradeItem = upgradeItem;
    }

    /** Half-width of the protected cube, in blocks, in every direction from the core. */
    public int getRadius() {
        return radius;
    }

    /** How many of {@link #getUpgradeItem()} are needed to reach the NEXT tier. */
    public int getUpgradeCost() {
        return upgradeCost;
    }

    /** The item consumed to upgrade FROM this tier to the next one. Null if this is the max tier. */
    public Item getUpgradeItem() {
        return upgradeItem;
    }

    public boolean isMaxTier() {
        return this == LEVEL_4;
    }

    /** Returns the next tier up, or null if this is already the max tier. */
    public ClaimTier next() {
        int nextIndex = this.ordinal() + 1;
        ClaimTier[] all = values();
        if (nextIndex >= all.length) {
            return null;
        }
        return all[nextIndex];
    }

    public static ClaimTier byIndex(int index) {
        ClaimTier[] all = values();
        if (index < 0 || index >= all.length) {
            return LEVEL_1;
        }
        return all[index];
    }
}
