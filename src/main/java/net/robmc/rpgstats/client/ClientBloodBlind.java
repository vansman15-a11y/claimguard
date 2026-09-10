package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

/** Eye Decay: the screen goes near-black with a pulsing red blood veil, fading out. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientBloodBlind {

    private static long endTick;
    private static int totalTicks;

    private ClientBloodBlind() {
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
        event.registerAbove(VanillaGuiOverlay.HELMET.id(), "rpg_blood_blind", OVERLAY);
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
        float t = Math.min(1.0f, remaining / totalTicks);       // 1 -> 0
        int black = (int) (t * 235) & 0xFF;
        g.fill(0, 0, screenW, screenH, (black << 24) | 0x00000000);

        // a red vignette on top, heavier at the edges
        int red = (int) (t * 210) & 0xFF;
        int edge = (red << 24) | 0x00A80000;
        int band = Math.max(20, screenH / 5);
        g.fillGradient(0, 0, screenW, band, edge, 0x00A80000);
        g.fillGradient(0, screenH - band, screenW, screenH, 0x00A80000, edge);
        g.fillGradient(0, 0, band, screenH, edge, 0x00A80000);
        g.fillGradient(screenW - band, 0, screenW, screenH, 0x00A80000, edge);
    };
}
