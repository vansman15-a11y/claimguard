package net.robmc.claimguard.clan;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;

/** Keeps every client's ally list and clan view fresh - on login and on a slow tick. */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClanEvents {

    private static final int RESYNC_INTERVAL = 40; // ~2 s

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClanActions.syncAllies(player);
            // send everyone (incl. the joiner) a fresh roster so the new name/tag shows up
            ClanActions.syncClanViewToAll(player.server);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            player.getServer().execute(() -> ClanActions.syncClanViewToAll(player.getServer()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.getServer().getTickCount() % RESYNC_INTERVAL == 0) {
            ClanActions.syncClanViewToAll(event.getServer());
        }
    }
}
