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
        CHANNEL.registerMessage(
                nextPacketId++,
                ShowClaimBorderPacket.class,
                ShowClaimBorderPacket::encode,
                ShowClaimBorderPacket::decode,
                ShowClaimBorderPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClaimMenuPacket.class,
                OpenClaimMenuPacket::encode,
                OpenClaimMenuPacket::decode,
                OpenClaimMenuPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClaimActionPacket.class,
                ClaimActionPacket::encode,
                ClaimActionPacket::decode,
                ClaimActionPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenCreateClanScreenPacket.class,
                OpenCreateClanScreenPacket::encode,
                OpenCreateClanScreenPacket::decode,
                OpenCreateClanScreenPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                CreateClanPacket.class,
                CreateClanPacket::encode,
                CreateClanPacket::decode,
                CreateClanPacket::handle
        );
    }
}