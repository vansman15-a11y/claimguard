package net.robmc.claimguard.client;

import net.minecraft.client.Minecraft;

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
}
