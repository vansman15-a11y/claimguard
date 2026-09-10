package net.robmc.rpgstats.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

/**
 * Draws a wolf or dolphin in place of a transformed player. Cancels the normal
 * player render entirely, so armour, held items and the nametag vanish with it.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class WildShapeRenderer {

    private static final ResourceLocation WOLF_TEX = new ResourceLocation("textures/entity/wolf/wolf.png");
    private static final ResourceLocation DOLPHIN_TEX = new ResourceLocation("textures/entity/dolphin.png");

    private static WolfModel<Wolf> wolfModel;
    private static DolphinModel<Player> dolphinModel;
    private static Wolf dummyWolf;

    private WildShapeRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        int form = ClientWildShape.form(event.getEntity().getId());
        if (form == 0) {
            return;
        }
        ensureModels();
        Wolf wolf = dummyWolf();
        if (wolfModel == null || dolphinModel == null || (form == 1 && wolf == null)) {
            return;
        }

        Player player = event.getEntity();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();

        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        float ageInTicks = player.tickCount + partial;
        float limbSwing = player.walkAnimation.position(partial);
        float limbSwingAmount = Math.min(1.0f, player.walkAnimation.speed(partial));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);

        if (form == 1) {
            wolfModel.prepareMobModel(wolf, limbSwing, limbSwingAmount, partial);
            wolfModel.setupAnim(wolf, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vc = event.getMultiBufferSource().getBuffer(wolfModel.renderType(WOLF_TEX));
            wolfModel.renderToBuffer(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            dolphinModel.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vc = event.getMultiBufferSource().getBuffer(dolphinModel.renderType(DOLPHIN_TEX));
            dolphinModel.renderToBuffer(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        pose.popPose();
        event.setCanceled(true);
    }

    private static void ensureModels() {
        if (wolfModel != null) {
            return;
        }
        var set = Minecraft.getInstance().getEntityModels();
        wolfModel = new WolfModel<>(set.bakeLayer(ModelLayers.WOLF));
        dolphinModel = new DolphinModel<>(set.bakeLayer(ModelLayers.DOLPHIN));
    }

    private static Wolf dummyWolf() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (dummyWolf == null || dummyWolf.level() != level) {
            dummyWolf = EntityType.WOLF.create(level);
        }
        return dummyWolf;
    }
}
