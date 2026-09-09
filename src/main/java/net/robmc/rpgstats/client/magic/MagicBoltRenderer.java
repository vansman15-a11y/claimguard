package net.robmc.rpgstats.client.magic;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.robmc.rpgstats.magic.MagicBoltEntity;

/** No model - the entity's own blue particle trail is the visual. */
public class MagicBoltRenderer extends EntityRenderer<MagicBoltEntity> {

    public MagicBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicBoltEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
