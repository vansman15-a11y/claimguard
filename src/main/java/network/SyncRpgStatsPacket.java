package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the viewer's current pools and stat levels, for the RPG HUD. */
public class SyncRpgStatsPacket {

    public final float health;
    public final float maxHealth;
    public final float stamina;
    public final float maxStamina;
    public final float mana;
    public final float maxMana;
    public final int str;
    public final int vit;
    public final int dex;
    public final int qui;
    public final int intel;
    public final int wis;

    public SyncRpgStatsPacket(double health, double maxHealth, double stamina, double maxStamina,
                              double mana, double maxMana, int str, int vit, int dex, int qui, int intel, int wis) {
        this.health = (float) health;
        this.maxHealth = (float) maxHealth;
        this.stamina = (float) stamina;
        this.maxStamina = (float) maxStamina;
        this.mana = (float) mana;
        this.maxMana = (float) maxMana;
        this.str = str;
        this.vit = vit;
        this.dex = dex;
        this.qui = qui;
        this.intel = intel;
        this.wis = wis;
    }

    public static void encode(SyncRpgStatsPacket p, FriendlyByteBuf buf) {
        buf.writeFloat(p.health);
        buf.writeFloat(p.maxHealth);
        buf.writeFloat(p.stamina);
        buf.writeFloat(p.maxStamina);
        buf.writeFloat(p.mana);
        buf.writeFloat(p.maxMana);
        buf.writeVarInt(p.str);
        buf.writeVarInt(p.vit);
        buf.writeVarInt(p.dex);
        buf.writeVarInt(p.qui);
        buf.writeVarInt(p.intel);
        buf.writeVarInt(p.wis);
    }

    public static SyncRpgStatsPacket decode(FriendlyByteBuf buf) {
        return new SyncRpgStatsPacket(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SyncRpgStatsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.RpgHudOverlay.update(packet)
        ));
        context.setPacketHandled(true);
    }
}
