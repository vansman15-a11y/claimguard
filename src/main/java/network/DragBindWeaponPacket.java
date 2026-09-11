package net.robmc.claimguard.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.item.Weapons;
import net.robmc.rpgstats.magic.Spell;

import java.util.function.Supplier;

/**
 * Client -&gt; server: dropped a weapon/staff carried on the cursor of the survival (or
 * creative) inventory screen onto one of the casting-bar slots rendered underneath it - binds
 * it as that spell's auto-equip weapon, same as holding it and right-clicking the row in the
 * Spellbook, just without needing to close the inventory first. The item is only inspected,
 * never consumed - it stays exactly where it was on the cursor.
 */
public class DragBindWeaponPacket {

    private final String spellName;
    private final ItemStack stack;

    public DragBindWeaponPacket(String spellName, ItemStack stack) {
        this.spellName = spellName;
        this.stack = stack;
    }

    public static void encode(DragBindWeaponPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.spellName, 48);
        buf.writeItem(p.stack);
    }

    public static DragBindWeaponPacket decode(FriendlyByteBuf buf) {
        return new DragBindWeaponPacket(buf.readUtf(48), buf.readItem());
    }

    public static void handle(DragBindWeaponPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.stack.isEmpty()) {
                return;
            }
            Spell spell = Spell.byName(packet.spellName);
            if (spell == null || !Weapons.isBindableWeapon(packet.stack)) {
                return;
            }
            var id = ForgeRegistries.ITEMS.getKey(packet.stack.getItem());
            if (id == null) {
                return;
            }
            RpgManager.stats(player).setWeaponPref(spell, id.toString());
            player.displayClientMessage(Component.literal(
                    spell.displayName() + " will now auto-equip " + packet.stack.getHoverName().getString() + ".")
                    .withStyle(ChatFormatting.GRAY), true);
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
