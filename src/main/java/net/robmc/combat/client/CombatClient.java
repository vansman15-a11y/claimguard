package net.robmc.combat.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.combat.CombatEvents;
import net.robmc.combat.CombatMod;
import net.robmc.combat.network.BindShieldPacket;
import net.robmc.combat.network.CombatNetwork;
import net.robmc.combat.network.ParryStatePacket;
import net.robmc.combat.network.SwingPacket;

/**
 * Client behaviour: force third-person while a melee weapon is out (F5 snaps back), report
 * swings-at-air so the server can still sweep the arc, report holding/releasing right-click for
 * parry, and bind a shield to a sword when one's dragged onto the other in the inventory.
 */
@Mod.EventBusSubscriber(modid = CombatMod.MODID, value = Dist.CLIENT)
public final class CombatClient {

    private static boolean forcing;
    private static CameraType freeChoice = CameraType.FIRST_PERSON;
    private static boolean parrying;

    private CombatClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.level == null) {
            return;
        }
        boolean melee = CombatEvents.isMeleeWeapon(mc.player.getMainHandItem());
        if (melee && !forcing) {
            freeChoice = mc.options.getCameraType();
            forcing = true;
        } else if (!melee && forcing) {
            mc.options.setCameraType(freeChoice);
            forcing = false;
        }
        if (forcing && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        boolean wantParry = melee && mc.screen == null && mc.options.keyUse.isDown();
        if (wantParry != parrying) {
            parrying = wantParry;
            CombatNetwork.CHANNEL.sendToServer(new ParryStatePacket(parrying));
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (CombatEvents.isMeleeWeapon(event.getItemStack())) {
            CombatNetwork.CHANNEL.sendToServer(new SwingPacket());
        }
    }

    /** Drag a shield onto a sword (or a sword onto a shield) in the inventory to bind them - not a real swap. */
    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        ItemStack carried = cs.getMenu().getCarried();
        if (carried.isEmpty()) {
            return;
        }
        Slot slot = slotUnderMouse(cs, event.getMouseX(), event.getMouseY());
        if (slot == null || !slot.hasItem()) {
            return;
        }
        ItemStack target = slot.getItem();

        ItemStack sword;
        ItemStack shield;
        if (carried.getItem() instanceof SwordItem && target.getItem() instanceof ShieldItem) {
            sword = carried;
            shield = target;
        } else if (carried.getItem() instanceof ShieldItem && target.getItem() instanceof SwordItem) {
            sword = target;
            shield = carried;
        } else {
            return;
        }
        var swordId = ForgeRegistries.ITEMS.getKey(sword.getItem());
        var shieldId = ForgeRegistries.ITEMS.getKey(shield.getItem());
        if (swordId == null || shieldId == null) {
            return;
        }
        CombatNetwork.CHANNEL.sendToServer(new BindShieldPacket(swordId.toString(), shieldId.toString()));
        event.setCanceled(true); // bind them, don't actually swap the two stacks
    }

    private static Slot slotUnderMouse(AbstractContainerScreen<?> cs, double mx, double my) {
        int left = cs.getGuiLeft();
        int top = cs.getGuiTop();
        for (Slot slot : cs.getMenu().slots) {
            int sx = left + slot.x;
            int sy = top + slot.y;
            if (mx >= sx - 1 && mx < sx + 17 && my >= sy - 1 && my < sy + 17) {
                return slot;
            }
        }
        return null;
    }
}
