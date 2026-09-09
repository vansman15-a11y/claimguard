package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

/** Full-screen white wash from Bright Light, fading out over its duration. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientFlash {

    private static long endTick;
    private static int totalTicks;

    private ClientFlash() {
    }

    public static void trigger(int ticks) {
        if (ticks <= 0) {
            return;
        }
        totalTicks = ticks;
        endTick = now() + ticks;
    }

    private static long now() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HELMET.id(), "rpg_flash", OVERLAY);
    }

    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenW, screenH) -> {
        if (totalTicks <= 0) {
            return;
        }
        float remaining = endTick - now() - partialTick;
        if (remaining <= 0) {
            totalTicks = 0;
            return;
        }
        float alpha = Math.min(1.0f, remaining / totalTicks);
        int a = (int) (alpha * 255) & 0xFF;
        g.fill(0, 0, screenW, screenH, (a << 24) | 0x00FFFFFF);
    };
}
