package net.robmc.rpgstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import org.lwjgl.glfw.GLFW;

/** J toggles the HUD layout editor. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgKeybinds {

    public static final KeyMapping HUD_EDITOR = new KeyMapping(
            "key.rpgstats.hud_editor", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.rpgstats");

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(HUD_EDITOR);
    }

    @Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
    public static class Handler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (HUD_EDITOR.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new HudEditorScreen());
                } else if (mc.screen instanceof HudEditorScreen) {
                    mc.setScreen(null);
                }
            }
        }
    }
}
