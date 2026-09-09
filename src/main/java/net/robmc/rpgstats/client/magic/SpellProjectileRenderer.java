package net.robmc.rpgstats.client.magic;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.robmc.rpgstats.magic.SpellProjectileEntity;

/** No model - the entity's coloured particle trail is the visual. */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectileEntity> {

    public SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellProjectileEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/white.png");
    }
}
