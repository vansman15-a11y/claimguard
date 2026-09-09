package net.robmc.rpgstats;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /stats} - print your stat levels and pools in chat. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID)
public class RpgCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stats").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PlayerStats s = RpgManager.stats(player);

            player.sendSystemMessage(Component.literal("--- Your Stats ---").withStyle(ChatFormatting.GOLD));
            for (Stat stat : Stat.values()) {
                player.sendSystemMessage(Component.literal(String.format(
                        "%-13s %3d   (%.0f / %.0f xp)  - %s",
                        stat.displayName(), s.getLevel(stat),
                        s.getXp(stat), StatFormulas.xpForNextLevel(s.getLevel(stat)), stat.trainedBy())));
            }
            player.sendSystemMessage(Component.literal(String.format(
                    "Health %.0f/%.0f   Stamina %.0f/%.0f   Mana %.0f/%.0f",
                    player.getHealth(), player.getMaxHealth(),
                    s.getStamina(), StatFormulas.maxStamina(s),
                    s.getMana(), StatFormulas.maxMana(s))).withStyle(ChatFormatting.AQUA));
            return 1;
        }));
    }
}
