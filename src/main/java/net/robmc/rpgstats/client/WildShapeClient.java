package net.robmc.rpgstats.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.WolfLeapPacket;
import net.robmc.rpgstats.RpgStats;

/**
 * Client behaviour for druid forms: while transformed you're locked to
 * third-person and your held item is hidden (paws/fins don't hold staves), and a
 * use-key press in Wolf Form becomes a Leap. Shifting back restores both.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class WildShapeClient {

    private static final int FORM_WOLF = 1;

    private static boolean forcing;
    private static CameraType saved = CameraType.FIRST_PERSON;

    private WildShapeClient() {
    }

    private static boolean selfTransformed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ClientWildShape.isTransformed(mc.player.getId());
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
            if (saved != CameraType.FIRST_PERSON && saved != CameraType.THIRD_PERSON_BACK
                    && saved != CameraType.THIRD_PERSON_FRONT) {
                saved = CameraType.FIRST_PERSON;
            }
            forcing = true;
        } else if (!transformed && forcing) {
            mc.options.setCameraType(saved);
            forcing = false;
        }
        if (forcing && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    /** Hide the first-person hand/held item entirely while shifted. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (selfTransformed()) {
            event.setCanceled(true);
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
