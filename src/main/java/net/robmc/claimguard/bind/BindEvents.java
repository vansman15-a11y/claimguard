package net.robmc.claimguard.bind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.OpenRespawnChoicePacket;

import java.util.Optional;

/**
 * Makes the bindstone actually do something:
 *  - on death, if the player is bound AND has a bed, offer a choice screen;
 *  - on respawn, if the player is bound and either chose "bind" or has no bed,
 *    move them to the bindstone.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BindEvents {

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BindManager manager = BindManager.get(player.server);
        if (manager.getBind(player.getUUID()).isEmpty()) {
            return;
        }
        boolean hasBed = player.getRespawnPosition() != null;
        if (!hasBed) {
            return; // no choice to make - onRespawn will send them to the bindstone
        }
        // Defer a couple of ticks so our screen replaces the death screen rather
        // than the other way around.
        player.server.tell(new TickTask(player.server.getTickCount() + 2, () -> {
            if (!player.hasDisconnected()) {
                ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenRespawnChoicePacket());
            }
        }));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BindManager manager = BindManager.get(player.server);
        Optional<BindManager.Bind> bind = manager.getBind(player.getUUID());
        if (bind.isEmpty()) {
            return;
        }

        Boolean choice = manager.takeRespawnChoice(player.getUUID());
        boolean hasBed = player.getRespawnPosition() != null;
        boolean goToBind = choice != null ? choice : !hasBed;
        if (!goToBind) {
            return;
        }

        ServerLevel level = player.server.getLevel(bind.get().dimension());
        if (level == null) {
            return;
        }
        BlockPos pos = bind.get().pos();
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }
}
