package net.robmc.claimguard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;

/**
 * Draws the outline of a claim's protected cube in the world for a few seconds,
 * triggered by ShowClaimBorderPacket when the owner right-clicks their core with
 * an empty hand.
 *
 * One box at a time - asking again just replaces it and resets the timer.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClaimBorderClient {

    // ClaimGuard green.
    private static final float R = 0.30F;
    private static final float G = 0.85F;
    private static final float B = 0.45F;
    private static final float A = 0.85F;

    private static AABB border;
    private static int ticksRemaining;

    public static void show(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int durationTicks) {
        border = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        ticksRemaining = durationTicks;
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
}
