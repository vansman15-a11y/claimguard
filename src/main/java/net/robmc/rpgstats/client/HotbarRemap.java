package net.robmc.rpgstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import org.lwjgl.glfw.GLFW;

/**
 * Moves the vanilla hotbar select keys to SHIFT + 1..9 so that plain 1..8 are
 * free for the spell bar. Runs once per session, and only touches a slot key
 * that's still at its vanilla default (plain digit, no modifier) - a player who
 * rebound it themselves is left alone.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public class HotbarRemap {

    private static boolean done = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        done = true;

        boolean changed = false;
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            KeyMapping k = mc.options.keyHotbarSlots[i];
            InputConstants.Key defaultDigit = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_1 + i);
            if (k.getKeyModifier() == KeyModifier.NONE && k.getKey().equals(defaultDigit)) {
                k.setKeyModifierAndCode(KeyModifier.SHIFT, defaultDigit);
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }
}
