package net.robmc.claimguard.claim;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Defines each "level" a claim can be upgraded to.
 *
 * This is a Java enum: think of it like a fixed list of named constants,
 * except each constant here is allowed to carry its own data (radius + the
 * item cost to reach the NEXT tier). ClaimTier.LEVEL_1, LEVEL_2 etc. are the
 * only tiers that will ever exist unless you add a new line below.
 *
 * Tiers are saved by their ordinal (see Claim.save/load), so only ever ADD new
 * tiers at the end - never reorder or remove.
 */
public enum ClaimTier {

    // radius | cost to upgrade FROM this tier to the next
    LEVEL_1(8,  cost(Items.DIAMOND, 8)),
    LEVEL_2(16, cost(Items.DIAMOND, 16)),
    LEVEL_3(24, cost(Items.DIAMOND, 32)),
    LEVEL_4(32, cost(Items.DIAMOND, 48, Items.BLAZE_ROD, 1)), // first past the "starter base" size
    LEVEL_5(42, cost(Items.DIAMOND, 64, Items.BLAZE_ROD, 2)), // second
    LEVEL_6(52); // max - no cost, no further upgrade

    private final int radius;
    private final List<ItemStack> upgradeCost;

    ClaimTier(int radius, List<ItemStack> upgradeCost) {
        this.radius = radius;
        this.upgradeCost = upgradeCost;
    }

    ClaimTier(int radius) {
        this(radius, List.of());
    }

    /** Half-width of the protected cube, in blocks, in every direction from the core. */
    public int getRadius() {
        return radius;
    }

    /**
     * Every item stack (item + amount) the player must have to upgrade FROM this
     * tier to the next. Empty when this is the max tier.
     *
     * The returned stacks are shared constants - read them, never mutate them.
     */
    public List<ItemStack> getUpgradeCost() {
        return upgradeCost;
    }

    public boolean isMaxTier() {
        return upgradeCost.isEmpty();
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

    // --- helpers for the constant list above ---

    private static List<ItemStack> cost(net.minecraft.world.item.Item item, int amount) {
        return List.of(new ItemStack(item, amount));
    }

    private static List<ItemStack> cost(net.minecraft.world.item.Item itemA, int amountA,
                                        net.minecraft.world.item.Item itemB, int amountB) {
        return List.of(new ItemStack(itemA, amountA), new ItemStack(itemB, amountB));
    }
}
