package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.DragBindWeaponPacket;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.client.magic.ClientSpells;
import net.robmc.rpgstats.client.magic.SpellBarOverlay;
import net.robmc.rpgstats.item.Weapons;
import net.robmc.rpgstats.magic.Spell;

/**
 * Our casting bars render underneath any open screen (that's why they're visible, faded, behind
 * the survival/creative inventory) but the vanilla inventory has no idea they're there - dropping
 * an item on top of one just reads as "dropped outside the inventory" and throws it on the ground.
 * This intercepts that drop: if the cursor is carrying a bindable weapon and releases over one of
 * the bar's slots, it binds the weapon to whatever spell that slot shows instead of dropping it.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public class InventoryWeaponDrag {

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        ItemStack carried = cs.getMenu().getCarried();
        if (carried.isEmpty() || !Weapons.isBindableWeapon(carried)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int slot = SpellBarOverlay.slotAt(event.getMouseX(), event.getMouseY(), mc.getWindow().getGuiScaledHeight());
        if (slot < 0) {
            return;
        }
        Spell spell = ClientSpells.activeSlot(slot);
        if (spell == null) {
            return;
        }
        ClaimGuardNetwork.CHANNEL.sendToServer(new DragBindWeaponPacket(spell.name(), carried.copy()));
        event.setCanceled(true); // don't let vanilla throw the carried item on the ground
    }
}
