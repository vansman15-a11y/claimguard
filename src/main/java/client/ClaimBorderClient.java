package net.robmc.claimguard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;

/**
 * Draws the outline of a claim's protected cube in the world, triggered by
 * ShowClaimBorderPacket when the owner right-clicks their core with an empty hand.
 *
 * Behaviour: right-click shows the border for {@code duration} ticks; right-clicking
 * the same core again hides it early. While it's showing, a small "Claim border"
 * status with a countdown sits in the top-left so it's clear the view is active.
 * Only one border shows at a time.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClaimBorderClient {

    // ClaimGuard green.
    private static final float R = 0.30F;
    private static final float G = 0.85F;
    private static final float B = 0.45F;
    private static final float A = 0.85F;

    private static final int STATUS_TEXT_COLOR = 0xFF55E07A;

    private static AABB border;
    private static int ticksRemaining;

    /**
     * Show the given box, or hide it if that exact box is already showing (toggle).
     * Called from ShowClaimBorderPacket on the client thread.
     */
    public static void toggle(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int durationTicks) {
        AABB requested = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        if (border != null && ticksRemaining > 0 && border.equals(requested)) {
            border = null;
            ticksRemaining = 0;
            actionBar("Claim border hidden");
        } else {
            border = requested;
            ticksRemaining = durationTicks;
            actionBar("Showing claim border");
        }
    }

    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ticksRemaining > 0 && --ticksRemaining == 0) {
            border = null;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (border == null || ticksRemaining <= 0) {
            return;
        }

        // World coordinates are relative to the camera, so shift everything by -camera.
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(poseStack, lines, border, R, G, B, A);
        poseStack.popPose();

        buffers.endBatch(RenderType.lines());
    }

    /** Top-left "Claim border  27s" status shown while the outline is active. */
    public static final IGuiOverlay STATUS_OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (border == null || ticksRemaining <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int seconds = (ticksRemaining + 19) / 20; // round up so it never shows "0s" while still visible
        String text = "Viewing claim border  -  " + seconds + "s";
        guiGraphics.drawString(font, text, 6, 6, STATUS_TEXT_COLOR, true);
    };
}
