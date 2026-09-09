package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.skill.RecallManager;
import net.robmc.rpgstats.skill.RestManager;
import net.robmc.rpgstats.skill.Skill;

import java.util.function.Supplier;

/** Client -&gt; server: activate (or toggle) a general skill by right-clicking its hotbar item. */
public class ActivateSkillPacket {

    private final String skill;

    public ActivateSkillPacket(String skill) {
        this.skill = skill == null ? "" : skill;
    }

    public static void encode(ActivateSkillPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.skill, 32);
    }

    public static ActivateSkillPacket decode(FriendlyByteBuf buf) {
        return new ActivateSkillPacket(buf.readUtf(32));
    }

    public static void handle(ActivateSkillPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            Skill skill = Skill.byName(packet.skill);
            if (player == null || skill == null) {
                return;
            }
            switch (skill) {
                case REST -> RestManager.toggle(player);
                case RECALL -> RecallManager.toggle(player);
            }
        });
        context.setPacketHandled(true);
    }
}
