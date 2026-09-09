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
import net.robmc.rpgstats.StatFormulas;
import org.lwjgl.glfw.GLFW;

/**
 * Runs once per session:
 *  - moves the vanilla hotbar select keys to SHIFT + 1..9 so plain 1..9 are free
 *    for the spell bar (only touches keys still at their vanilla default);
 *  - recovers the J / U menu keys if a spell-slot rebind stole them (menu keys win).
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

        changed |= restoreMenuKey(RpgKeybinds.HUD_EDITOR, GLFW.GLFW_KEY_J);
        changed |= restoreMenuKey(RpgKeybinds.SPELLBOOK, GLFW.GLFW_KEY_U);

        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }

    /** If a menu key got unbound (a slot rebind grabbed it), put it back and clear the thief. */
    private static boolean restoreMenuKey(KeyMapping menu, int glfwCode) {
        if (menu.getKey() != InputConstants.UNKNOWN) {
            return false; // still bound - either the default or the player's own choice
        }
        InputConstants.Key want = InputConstants.Type.KEYSYM.getOrCreate(glfwCode);
        menu.setKeyModifierAndCode(KeyModifier.NONE, want);
        for (int i = 0; i < RpgKeybinds.CAST.length; i++) {
            KeyMapping cast = RpgKeybinds.CAST[i];
            if (cast.getKeyModifier() == KeyModifier.NONE && cast.getKey().equals(want)) {
                // the slot that stole the menu key goes back to its default
                int digit = GLFW.GLFW_KEY_1 + (i % StatFormulas.BAR_SLOTS);
                KeyModifier mod = i < StatFormulas.BAR_SLOTS ? KeyModifier.NONE : KeyModifier.ALT;
                cast.setKeyModifierAndCode(mod, InputConstants.Type.KEYSYM.getOrCreate(digit));
            }
        }
        return true;
    }
}
