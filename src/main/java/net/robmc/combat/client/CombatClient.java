package net.robmc.combat.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.combat.CombatEvents;
import net.robmc.combat.CombatMod;
import net.robmc.combat.network.CombatNetwork;
import net.robmc.combat.network.SwingPacket;

/**
 * Client behaviour: force third-person while a melee weapon is out (F5 snaps
 * back), and report swings-at-air so the server can still sweep the arc.
 */
@Mod.EventBusSubscriber(modid = CombatMod.MODID, value = Dist.CLIENT)
public final class CombatClient {

    private static boolean forcing;
    private static CameraType freeChoice = CameraType.FIRST_PERSON;

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
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (CombatEvents.isMeleeWeapon(event.getItemStack())) {
            CombatNetwork.CHANNEL.sendToServer(new SwingPacket());
        }
    }
}
