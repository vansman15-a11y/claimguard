package net.robmc.combat.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.robmc.combat.CombatMod;

public final class CombatNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CombatMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private CombatNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, ComboSyncPacket.class,
                ComboSyncPacket::encode, ComboSyncPacket::decode, ComboSyncPacket::handle);
        CHANNEL.registerMessage(nextId++, SwingPacket.class,
                SwingPacket::encode, SwingPacket::decode, SwingPacket::handle);
    }
}
