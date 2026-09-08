package net.robmc.claimguard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.robmc.claimguard.network.OpenClaimMenuPacket;

/**
 * Tiny client-side entry point for OpenClaimMenuPacket. Kept in the client package
 * so the packet class itself never has to touch net.minecraft.client directly.
 */
public final class ClaimMenuClient {

    private ClaimMenuClient() {
    }

    public static void open(OpenClaimMenuPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;

        // If the upgrade screen is already open for this same core, keep the player
        // there and just refresh its numbers (this packet is also sent post-upgrade).
        if (current instanceof ClaimUpgradeScreen upgrade && upgrade.isFor(packet.corePos())) {
            mc.setScreen(new ClaimUpgradeScreen(packet.corePos(), packet.tierOrdinal(), packet.claimName(), packet.boundHere()));
            return;
        }
        mc.setScreen(new ClaimMenuScreen(packet.corePos(), packet.tierOrdinal(), packet.claimName(),
                packet.boundHere(), packet.canManage()));
    }
}
