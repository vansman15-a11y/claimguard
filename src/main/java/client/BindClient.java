package net.robmc.claimguard.client;

import net.minecraft.client.Minecraft;

/** Client entry point for the respawn-choice packet. */
public final class BindClient {

    private BindClient() {
    }

    public static void openRespawnChoice() {
        Minecraft.getInstance().setScreen(new RespawnChoiceScreen());
    }
}
