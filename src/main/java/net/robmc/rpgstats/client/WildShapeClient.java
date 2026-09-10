package net.robmc.rpgstats.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.WolfLeapPacket;
import net.robmc.rpgstats.RpgStats;

/**
 * Client behaviour for druid forms: force third-person while transformed (so you
 * can see your wolf/dolphin), and turn a use-key press in Wolf Form into a Leap.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class WildShapeClient {

    private static final int FORM_WOLF = 1;

    private static boolean forcing;
    private static CameraType saved = CameraType.FIRST_PERSON;

    private WildShapeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        boolean transformed = ClientWildShape.isTransformed(mc.player.getId());
        if (transformed && !forcing) {
            saved = mc.options.getCameraType();
            forcing = true;
        } else if (!transformed && forcing) {
            mc.options.setCameraType(saved);
            forcing = false;
        }
        if (forcing && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (ClientWildShape.form(event.getEntity().getId()) == FORM_WOLF) {
            ClaimGuardNetwork.CHANNEL.sendToServer(new WolfLeapPacket());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ClientWildShape.form(event.getEntity().getId()) == FORM_WOLF) {
            event.setCanceled(true);
            ClaimGuardNetwork.CHANNEL.sendToServer(new WolfLeapPacket());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ClientWildShape.form(event.getEntity().getId()) == FORM_WOLF) {
            event.setCanceled(true);
            ClaimGuardNetwork.CHANNEL.sendToServer(new WolfLeapPacket());
        }
    }
}
