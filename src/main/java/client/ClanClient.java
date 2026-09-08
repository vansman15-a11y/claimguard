package net.robmc.claimguard.client;

import net.minecraft.client.Minecraft;
import net.robmc.claimguard.network.OpenClanRosterPacket;

/**
 * Client-side entry points for the clan packets, kept in the client package so the
 * packet classes never touch net.minecraft.client directly.
 */
public final class ClanClient {

    private ClanClient() {
    }

    public static void openCreateScreen() {
        Minecraft.getInstance().setScreen(new CreateClanScreen());
    }

    public static void openRoster(OpenClanRosterPacket data) {
        Minecraft.getInstance().setScreen(new ClanRosterScreen(data));
    }
}
