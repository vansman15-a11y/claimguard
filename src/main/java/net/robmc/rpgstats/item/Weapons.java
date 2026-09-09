package net.robmc.rpgstats.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Recognises the three custom weapons whether they are our own registered items
 * ({@code rpgstats:dagger} / {@code polearm} / {@code staff}) or the resource
 * pack's CustomModelData convention on a vanilla item (an iron sword tagged
 * 71001/71002, a stick or carrot-on-a-stick tagged 71003/71004).
 */
public final class Weapons {

    public static final int CMD_DAGGER = 71001;
    public static final int CMD_POLEARM = 71002;
    public static final int CMD_STAFF = 71003;
    public static final int CMD_STAFF_GLOW = 71004;

    public enum Kind { NONE, DAGGER, POLEARM, STAFF }

    private Weapons() {
    }

    public static Kind kind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Kind.NONE;
        }
        Item item = stack.getItem();
        if (item instanceof DaggerItem) {
            return Kind.DAGGER;
        }
        if (item instanceof PolearmItem) {
            return Kind.POLEARM;
        }
        if (item instanceof StaffItem) {
            return Kind.STAFF;
        }
        switch (customModelData(stack)) {
            case CMD_DAGGER:
                return item == Items.IRON_SWORD ? Kind.DAGGER : Kind.NONE;
            case CMD_POLEARM:
                return item == Items.IRON_SWORD ? Kind.POLEARM : Kind.NONE;
            case CMD_STAFF:
            case CMD_STAFF_GLOW:
                return (item == Items.STICK || item == Items.CARROT_ON_A_STICK) ? Kind.STAFF : Kind.NONE;
            default:
                return Kind.NONE;
        }
    }

    public static boolean isStaff(ItemStack stack) {
        return kind(stack) == Kind.STAFF;
    }

    public static boolean isTwoHanded(ItemStack stack) {
        Kind k = kind(stack);
        return k == Kind.POLEARM || k == Kind.STAFF;
    }

    /** The CustomModelData int on a stack, or -1. */
    public static int customModelData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("CustomModelData", Tag.TAG_INT)
                ? tag.getInt("CustomModelData") : -1;
    }

    public static void setCustomModelData(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt("CustomModelData", value);
    }
}
