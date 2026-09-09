package net.robmc.claimguard.bind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        BlockPos spot = safeSpawnNear(level, bind.get().pos());
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    /**
     * A standing spot a few blocks from the beacon (never on top of it). Tries a
     * ring of offsets around the core for two-air-over-solid-ground; falls back to
     * just beside the core if nothing clean is found.
     */
    public static BlockPos safeSpawnNear(ServerLevel level, BlockPos core) {
        int[][] offsets = {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 2}, {-2, -2}, {2, -2}, {-2, 2},
                {3, 0}, {-3, 0}, {0, 3}, {0, -3}
        };
        for (int[] o : offsets) {
            BlockPos column = core.offset(o[0], 0, o[1]);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = column.above(dy);
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                        && level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()) {
                    return feet;
                }
            }
        }
        return core.offset(2, 1, 0); // fallback: beside the beacon, not on it
    }
}
