package net.robmc.claimguard.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.PickaxeItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.clan.Clan;
import net.robmc.claimguard.clan.ClanManager;
import net.robmc.claimguard.registry.ModBlocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The moment-to-moment siege rules: pickaxe hits on a besieged beacon, keeping the
 * beacon block itself unbreakable by anything but those hits, and ending sieges
 * when the raid window closes or the attackers give up.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SiegeEvents {

    private static final int HIT_COOLDOWN_TICKS = 5;
    private static final Map<UUID, Long> lastHitTick = new HashMap<>();

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.CLAIM_CORE.get())) {
            return;
        }
        SiegeManager sieges = SiegeManager.get(player.server);
        if (!sieges.isUnderSiege(pos)) {
            return;
        }

        // A besieged beacon can only be damaged by pickaxe hits - block normal mining.
        event.setCanceled(true);

        if (!(event.getItemStack().getItem() instanceof PickaxeItem)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Hit the beacon with a pickaxe."), true);
            return;
        }
        long now = player.server.overworld().getGameTime();
        long last = lastHitTick.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now - last < HIT_COOLDOWN_TICKS) {
            return; // rate-limit so autoclickers don't finish it instantly
        }
        lastHitTick.put(player.getUUID(), now);
        SiegeActions.hitBeacon(player, pos);
    }

    /** Nothing else may break a besieged beacon block (creepers, /setblock scripts, etc.). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getState().is(ModBlocks.CLAIM_CORE.get())
                && SiegeManager.get(level.getServer()).isUnderSiege(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        long now = server.overworld().getGameTime();
        SiegeManager sieges = SiegeManager.get(server);
        sieges.pruneExpiredLockouts(now);

        ClanManager clans = ClanManager.get(server);
        for (SiegeManager.Siege siege : sieges.activeSieges()) {
            Clan defender = clans.getClanById(siege.defenderClan).orElse(null);
            if (defender == null) {
                SiegeActions.defendersWin(server, siege, "the defending clan is gone");
            } else if (!defender.isRaidWindowOpenNow()) {
                SiegeActions.defendersWin(server, siege, "the raid window closed");
            } else if (now - siege.lastActivityTick > SiegeManager.INACTIVITY_TICKS) {
                SiegeActions.defendersWin(server, siege, "the attackers gave up");
            }
        }
    }
}
