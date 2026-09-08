package net.robmc.claimguard.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.robmc.claimguard.ClaimGuard;

public class ClaimGuardNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ClaimGuard.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextPacketId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                nextPacketId++,
                TerritoryTitlePacket.class,
                TerritoryTitlePacket::encode,
                TerritoryTitlePacket::decode,
                TerritoryTitlePacket::handle
        );
    }
}