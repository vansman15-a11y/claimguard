package net.robmc.claimguard.client;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side ally list (synced from the server). Members of a clan our clan
 * considers an ally render with a green name tag.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AllyClient {

    private static final Set<UUID> ALLIES = new HashSet<>();

    private AllyClient() {
    }

    public static void setAllies(List<UUID> ids) {
        ALLIES.clear();
        ALLIES.addAll(ids);
    }

    public static boolean isAlly(UUID id) {
        return ALLIES.contains(id);
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player && ALLIES.contains(player.getUUID())) {
            event.setContent(event.getContent().copy().withStyle(ChatFormatting.GREEN));
        }
    }
}
