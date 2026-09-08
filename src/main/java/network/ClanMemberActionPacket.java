package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: a management action from the clan roster's right-click menu.
 * The server re-checks the actor's rank and permissions (see ClanPermissions).
 */
public class ClanMemberActionPacket {

    public enum Action {
        PROMOTE,
        DEMOTE,
        KICK,
        BAN,
        TOGGLE_BUILD
    }

    private final UUID target;
    private final Action action;

    public ClanMemberActionPacket(UUID target, Action action) {
        this.target = target;
        this.action = action;
    }

    public static void encode(ClanMemberActionPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.target);
        buf.writeEnum(packet.action);
    }

    public static ClanMemberActionPacket decode(FriendlyByteBuf buf) {
        return new ClanMemberActionPacket(buf.readUUID(), buf.readEnum(Action.class));
    }

    public static void handle(ClanMemberActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer actor = context.getSender();
            if (actor != null) {
                ClanActions.memberAction(actor, packet.target, packet.action);
            }
        });
        context.setPacketHandled(true);
    }
}
