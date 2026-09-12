package net.robmc.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Watches for the player switching their hotbar selection onto a sword that has a shield bound
 * to it (see {@link ShieldBindings}), and pulls that shield into the offhand from wherever it
 * sits in the inventory - so selecting the sword is the only hotkey needed for the pair. Also
 * the reverse: bows and crossbows are two-handed, so switching to one strips any shield out of
 * the offhand back into the inventory.
 */
public final class ShieldSwapManager {

    private static final Map<UUID, Integer> lastSelected = new HashMap<>();

    private ShieldSwapManager() {
    }

    public static void clear(UUID id) {
        lastSelected.remove(id);
    }

    /** Called every player tick from CombatEvents. */
    public static void tick(ServerPlayer player) {
        var inv = player.getInventory();
        int sel = inv.selected;
        Integer prev = lastSelected.put(player.getUUID(), sel);
        if (prev != null && prev == sel) {
            return; // hotbar selection hasn't changed since last tick
        }
        ItemStack held = inv.items.get(sel);
        if (held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem) {
            unequipShield(player);
            return;
        }
        if (!(held.getItem() instanceof SwordItem)) {
            return;
        }
        ResourceLocation swordId = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (swordId == null) {
            return;
        }
        String shieldIdStr = ShieldBindings.shieldFor(player, swordId.toString());
        if (shieldIdStr == null) {
            return;
        }
        ResourceLocation shieldId = ResourceLocation.tryParse(shieldIdStr);
        if (shieldId == null) {
            return;
        }
        Item wanted = ForgeRegistries.ITEMS.getValue(shieldId);
        if (wanted == null || wanted == Items.AIR) {
            return;
        }
        if (player.getOffhandItem().is(wanted)) {
            return; // already equipped
        }
        for (int i = 0; i < inv.items.size(); i++) { // hotbar + main inventory only - never armor
            ItemStack stack = inv.items.get(i);
            if (stack.is(wanted)) {
                ItemStack previousOffhand = player.getOffhandItem();
                inv.setItem(i, previousOffhand);
                player.setItemInHand(InteractionHand.OFF_HAND, stack);
                return;
            }
        }
    }

    /** A bow/crossbow needs both hands - put whatever shield is in the offhand back in the inventory. */
    private static void unequipShield(ServerPlayer player) {
        ItemStack shield = player.getOffhandItem();
        if (!(shield.getItem() instanceof ShieldItem)) {
            return;
        }
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(shield)) {
            player.drop(shield, false);
        }
    }
}
