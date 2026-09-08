package net.robmc.claimguard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.robmc.claimguard.block.entity.ClaimCoreBlockEntity;

/**
 * Draws the upward beam for a Claim Core so it reads as a beacon that's fully
 * powered - i.e. a beacon standing on a complete pyramid, beam going all the way
 * to the sky. The block's boxy "beacon glass + core" shape comes from the block
 * model (assets/.../models/block/claim_core.json); this class only adds the beam.
 *
 * Vanilla's BeaconRenderer.renderBeaconBeam is a public helper that just paints a
 * textured column of light - it doesn't care whether there's a real beacon there,
 * so we can reuse it directly.
 */
public class ClaimCoreRenderer implements BlockEntityRenderer<ClaimCoreBlockEntity> {

    /**
     * Beam tint as {r, g, b}, 0..1. {1,1,1} is plain white, exactly like a beacon
     * with no stained glass over it. To colour a claim's beam later, feed a
     * DyeColor's texture RGB in here instead.
     */
    private static final float[] BEAM_COLOR = { 1.0F, 1.0F, 1.0F };

    /** yOffset the beam starts at, measured from the block. 0 = right at the core. */
    private static final int BEAM_Y_OFFSET = 0;

    /** Vanilla beacon beam defaults: solid core radius, outer glow radius (in blocks). */
    private static final float BEAM_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.25F;

    /**
     * Beam height in blocks. Vanilla uses 1024 for the topmost (endless) section,
     * which is what makes a fully-powered beacon's beam appear to reach the sky.
     */
    private static final int BEAM_HEIGHT = 1024;

    public ClaimCoreRenderer(BlockEntityRendererProvider.Context context) {
        // No per-instance state needed; the Context (font, block renderer, models,
        // etc.) isn't required for a plain beam.
    }

    @Override
    public void render(ClaimCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
        BeaconRenderer.renderBeaconBeam(
                poseStack, bufferSource, BeaconRenderer.BEAM_LOCATION,
                partialTick, 1.0F, gameTime,
                BEAM_Y_OFFSET, BEAM_HEIGHT, BEAM_COLOR,
                BEAM_RADIUS, GLOW_RADIUS
        );
    }

    @Override
    public boolean shouldRenderOffScreen(ClaimCoreBlockEntity blockEntity) {
        // The beam is far taller than the block, so keep rendering it even when the
        // core block itself is outside the view frustum (same as vanilla beacons).
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
