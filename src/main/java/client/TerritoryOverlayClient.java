package net.robmc.claimguard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;

public class TerritoryOverlayClient {

    private static final int FADE_IN = 10;
    private static final int FADE_OUT = 20;

    private static String currentText = null;
    private static int totalTicks = 0;
    private static int ticksRemaining = 0;

    public static void show(String text, int durationTicks) {
        currentText = text;
        totalTicks = durationTicks;
        ticksRemaining = durationTicks;
    }

    public static final IGuiOverlay OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (currentText == null || ticksRemaining <= 0) {
            return;
        }

        int elapsed = totalTicks - ticksRemaining;
        float alpha;
        if (elapsed < FADE_IN) {
            alpha = elapsed / (float) FADE_IN;
        } else if (ticksRemaining < FADE_OUT) {
            alpha = ticksRemaining / (float) FADE_OUT;
        } else {
            alpha = 1.0f;
        }
        int alpha255 = Mth.clamp((int) (alpha * 255), 0, 255);
        if (alpha255 <= 0) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int color = 0xFFFFFF | (alpha255 << 24);
        float scale = 2.0f;

        Component styled = Component.literal(currentText).withStyle(ChatFormatting.ITALIC);
        int textWidth = font.width(currentText);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(screenWidth / 2f, screenHeight / 2f - 50f, 0);
        pose.scale(scale, scale, scale);
        guiGraphics.drawString(font, styled, -textWidth / 2, 0, color, true);
        pose.popPose();
    };

    @Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class Ticker {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (ticksRemaining > 0) {
                ticksRemaining--;
            } else {
                currentText = null;
            }
        }
    }
}