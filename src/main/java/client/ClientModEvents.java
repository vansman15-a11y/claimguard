package net.robmc.claimguard.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.registry.ModBlockEntities;

@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("territory", TerritoryOverlayClient.OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Attaches the beacon-beam renderer to the Claim Core block entity.
        event.registerBlockEntityRenderer(ModBlockEntities.CLAIM_CORE.get(), ClaimCoreRenderer::new);
    }
}
