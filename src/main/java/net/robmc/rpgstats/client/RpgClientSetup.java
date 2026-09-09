package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.client.magic.ClientSpells;
import net.robmc.rpgstats.client.magic.MagicBoltRenderer;
import net.robmc.rpgstats.registry.RpgEntities;
import net.robmc.rpgstats.registry.RpgItems;

@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RpgEntities.MAGIC_BOLT.get(), MagicBoltRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                RpgItems.STAFF.get(),
                new ResourceLocation(RpgStats.MOD_ID, "casting"),
                (stack, level, entity, seed) -> {
                    if (entity == null) {
                        return 0.0f;
                    }
                    if (entity == Minecraft.getInstance().player && ClientSpells.isCasting()) {
                        return 1.0f;
                    }
                    return ClientStaffGlow.isGlowing(entity.getId()) ? 1.0f : 0.0f;
                }));
    }
}
