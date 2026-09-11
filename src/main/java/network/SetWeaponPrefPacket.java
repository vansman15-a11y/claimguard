package net.robmc.claimguard.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.item.Weapons;
import net.robmc.rpgstats.magic.Spell;

import java.util.function.Supplier;

/**
 * Client -&gt; server: right-clicked a spell in the spellbook while holding an item -
 * bind (or, empty-handed, clear) that spell's "auto-equip this weapon before
 * casting" preference.
 */
public class SetWeaponPrefPacket {

    private final String spellName;

    public SetWeaponPrefPacket(String spellName) {
        this.spellName = spellName;
    }

    public static void encode(SetWeaponPrefPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.spellName);
    }

    public static SetWeaponPrefPacket decode(FriendlyByteBuf buf) {
        return new SetWeaponPrefPacket(buf.readUtf());
    }

    public static void handle(SetWeaponPrefPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Spell spell = Spell.byName(packet.spellName);
            if (spell == null) {
                return;
            }
            PlayerStats s = RpgManager.stats(player);
            ItemStack held = player.getMainHandItem();

            if (held.isEmpty()) {
                s.setWeaponPref(spell, null);
                player.displayClientMessage(Component.literal(spell.displayName() + " no longer auto-equips a weapon.")
                        .withStyle(ChatFormatting.GRAY), true);
            } else if (!Weapons.isBindableWeapon(held)) {
                player.displayClientMessage(Component.literal("That's not a weapon - hold one, or your empty hand to clear it.")
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (id == null) {
                    return;
                }
                s.setWeaponPref(spell, id.toString());
                player.displayClientMessage(Component.literal(spell.displayName() + " will now pull out ")
                        .append(held.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY), true);
            }
            RpgManager.markDirty(player);
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
