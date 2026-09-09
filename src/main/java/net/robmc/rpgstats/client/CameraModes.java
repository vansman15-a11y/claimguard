package net.robmc.rpgstats.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.robmc.rpgstats.item.StaffItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

/**
 * Ties the camera to what you are holding:
 * <ul>
 *   <li>melee weapon (sword / axe / trident) -&gt; forced third person</li>
 *   <li>bow or crossbow -&gt; forced first person</li>
 *   <li>empty hand (casting, and later a staff) -&gt; forced first person</li>
 *   <li>anything else -&gt; your own F5 choice, untouched</li>
 * </ul>
 * "Forced" means F5 snaps straight back on the next tick.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class CameraModes {

    private enum Mode { FREE, MELEE, FIRST }

    private static Mode prev = Mode.FREE;
    private static CameraType freeChoice = CameraType.FIRST_PERSON;

    private CameraModes() {
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

        Mode mode = modeFor(mc.player.getMainHandItem());

        if (mode != Mode.FREE && prev == Mode.FREE) {
            freeChoice = mc.options.getCameraType();   // remember the player's pick before we take over
        } else if (mode == Mode.FREE && prev != Mode.FREE) {
            mc.options.setCameraType(freeChoice);      // held item no longer forces a view - hand it back
        }
        prev = mode;

        CameraType want = switch (mode) {
            case MELEE -> CameraType.THIRD_PERSON_BACK;
            case FIRST -> CameraType.FIRST_PERSON;
            case FREE -> null;
        };
        if (want != null && mc.options.getCameraType() != want) {
            mc.options.setCameraType(want);
        }
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        Item i = stack.getItem();
        return i instanceof SwordItem || i instanceof AxeItem || i instanceof TridentItem;
    }

    /** Whether spells can be cast (and the camera locks to first person) with this in hand. */
    public static boolean isCastingHand(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof StaffItem;
    }

    private static Mode modeFor(ItemStack held) {
        if (isMeleeWeapon(held)) {
            return Mode.MELEE;
        }
        Item i = held.getItem();
        if (i instanceof BowItem || i instanceof CrossbowItem || isCastingHand(held)) {
            return Mode.FIRST;
        }
        return Mode.FREE;
    }
}
