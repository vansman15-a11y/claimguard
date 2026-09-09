package net.robmc.rpgstats;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

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
                int lvl = s.getLevel(stat);
                player.sendSystemMessage(Component.literal(String.format(
                        "%-13s %3d/%d  %3d%% power  (%.0f / %.0f xp)  - %s",
                        stat.displayName(), lvl, StatFormulas.LEVEL_CAP,
                        StatFormulas.effectivenessPercent(lvl),
                        s.getXp(stat), StatFormulas.xpForNextLevel(lvl), stat.trainedBy())));
            }

            player.sendSystemMessage(Component.literal("--- Your Spells ---").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (Spell spell : Spell.values()) {
                int lvl = s.getSpellLevel(spell);
                player.sendSystemMessage(Component.literal(String.format(
                        "%-26s %3d/%d  %3d%% power  (%.0f / %.0f xp)",
                        spell.displayName(), lvl, StatFormulas.LEVEL_CAP,
                        StatFormulas.effectivenessPercent(lvl),
                        s.getSpellXp(spell), StatFormulas.xpForNextLevel(lvl))));
            }

            player.sendSystemMessage(Component.literal("--- Your Skills ---").withStyle(ChatFormatting.GREEN));
            for (Skill skill : Skill.values()) {
                int lvl = s.getSkillLevel(skill);
                player.sendSystemMessage(Component.literal(String.format(
                        "%-12s %3d/%d  %3d%% power  (%.0f / %.0f xp)",
                        skill.displayName(), lvl, StatFormulas.LEVEL_CAP,
                        StatFormulas.effectivenessPercent(lvl),
                        s.getSkillXp(skill), StatFormulas.xpForNextLevel(lvl))));
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
