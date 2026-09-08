package net.robmc.claimguard.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.claim.Claim;
import net.robmc.claimguard.claim.ClaimManager;
import net.robmc.claimguard.clan.ClanManager;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.TerritoryTitlePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Shows a "Territory of <name>" message - drawn by our own custom overlay
 * (see TerritoryOverlayClient) - whenever a player walks into someone's claimed area.
 * It fades in, stays a moment, then fades out automatically.
 *
 * How it works: every few ticks, for every online player, we check which claim (if any)
 * they're currently standing in and compare it to what they were standing in the last
 * time we checked. If it changed, we send them a packet telling their client to show
 * (or stop showing) the message. This is a "polling" approach (repeatedly checking)
 * rather than a true "on cross the border" event, because Minecraft doesn't have a
 * built-in event for "player walked into this cuboid" - polling a few times a second
 * is cheap and nobody will notice the difference.
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TerritoryEvents {

    // Remembers, per player, which claim core (if any) they were standing inside last
    // time we checked. No entry (or a null value) means "not inside any claim".
    private static final Map<UUID, BlockPos> lastClaimCore = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return; // the tick event fires twice (start and end) - we only need it once
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return; // ignore the client-side copy of this event entirely
        }

        // 20 ticks = 1 real-world second. Checking every 10 ticks (twice a second) is
        // plenty responsive for a message like this and far cheaper than every tick.
        if (player.tickCount % 10 != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        ClaimManager manager = ClaimManager.get(level);
        Optional<Claim> currentClaim = manager.getClaimAt(player.blockPosition());

        BlockPos previousCore = lastClaimCore.get(player.getUUID());
        BlockPos currentCore = currentClaim.map(Claim::getCorePos).orElse(null);

        if (Objects.equals(previousCore, currentCore)) {
            return; // still in the same place (or same "no claim") as last check - do nothing
        }

        if (currentCore != null) {
            lastClaimCore.put(player.getUUID(), currentCore);
            showTitle(player, "Territory of " + territoryName(currentClaim.get(), player.getServer()));
        } else {
            // They left a claim and aren't in a new one - show a farewell for the claim
            // they just walked out of (previousCore is never null in this branch).
            lastClaimCore.remove(player.getUUID());
            String leftName = manager.getClaimByCore(previousCore)
                    .map(claim -> territoryName(claim, player.getServer()))
                    .orElse("this territory");
            showTitle(player, "Leaving territory of " + leftName);
        }
    }

    /**
     * The name shown in territory messages: the owning clan's name if the claim
     * belongs to a clan, otherwise the founder's player name.
     */
    private static String territoryName(Claim claim, MinecraftServer server) {
        String clanName = ClanManager.get(server).clanNameOrNull(claim.getClanId());
        return clanName != null ? clanName : getOwnerName(server, claim.getOwner());
    }

    /**
     * Looks up a player's current name from their UUID. Names can change, but UUIDs
     * can't, which is why we always store the UUID (in Claim) and only look up the
     * display name at the moment we need to show it.
     *
     * Used as the fallback for {@link #territoryName} when a claim has no clan.
     */
    private static String getOwnerName(MinecraftServer server, UUID owner) {
        // If the owner is online right now, this is the simplest and most up-to-date source.
        ServerPlayer onlineOwner = server.getPlayerList().getPlayer(owner);
        if (onlineOwner != null) {
            return onlineOwner.getGameProfile().getName();
        }
        // Otherwise, fall back to the server's cache of previously-seen player profiles.
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(owner)
                    .map(com.mojang.authlib.GameProfile::getName)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    /**
     * Tells the player's client to show our custom on-screen text for a little while.
     * This no longer uses Minecraft's built-in title system - see TerritoryOverlayClient
     * for the actual drawing, which is what lets us control the size and position.
     */
    private static void showTitle(ServerPlayer player, String text) {
        // 10 fade-in + 40 stay + 20 fade-out = 70 ticks total (3.5 seconds) - matches
        // the FADE_IN/FADE_OUT constants in TerritoryOverlayClient.
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TerritoryTitlePacket(text, 70));
    }
}