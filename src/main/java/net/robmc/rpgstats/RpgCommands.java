package net.robmc.rpgstats;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.magic.School;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

/**
 * {@code /stats} - print your levels and pools.
 * {@code /rpgset school|stat|skill <name|all> <level>} - op-only testing shortcut.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID)
public class RpgCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stats").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            printStats(player);
            return 1;
        }));

        event.getDispatcher().register(Commands.literal("rpgset")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("school")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> setSchool(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("spell")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setSpell(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("stat")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setStat(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("skill")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setSkill(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    // --- /rpgset ---

    private static int setSchool(ServerPlayer player, String name, int level) {
        PlayerStats s = RpgManager.stats(player);
        boolean all = name.equalsIgnoreCase("all");
        int count = 0;
        for (School school : School.values()) {
            if (all || school.name().equalsIgnoreCase(name) || school.displayName().replace(" ", "").equalsIgnoreCase(name)) {
                s.setSchoolLevel(school, level);
                count++;
            }
        }
        if (count == 0) {
            player.sendSystemMessage(Component.literal("No school matched \"" + name + "\". Try: "
                    + java.util.Arrays.toString(School.values())).withStyle(ChatFormatting.RED));
            return 0;
        }
        RpgManager.markDirty(player);
        RpgManager.syncSpellBar(player);
        player.sendSystemMessage(Component.literal("Set " + count + " school(s) to level " + level + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return count;
    }

    private static int setSpell(ServerPlayer player, String name, int level) {
        PlayerStats s = RpgManager.stats(player);
        boolean all = name.equalsIgnoreCase("all");
        int count = 0;
        for (Spell spell : Spell.values()) {
            if (all || spell.name().equalsIgnoreCase(name)
                    || spell.displayName().replaceAll("[^A-Za-z]", "").equalsIgnoreCase(name.replaceAll("[^A-Za-z]", ""))) {
                s.setSpellLevel(spell, level);
                count++;
            }
        }
        if (count == 0) {
            player.sendSystemMessage(Component.literal("No spell matched \"" + name + "\". Try: "
                    + java.util.Arrays.toString(Spell.values())).withStyle(ChatFormatting.RED));
            return 0;
        }
        RpgManager.markDirty(player);
        RpgManager.syncSpellBar(player);
        player.sendSystemMessage(Component.literal("Set " + count + " spell(s) to level " + level + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return count;
    }

    private static int setStat(ServerPlayer player, String name, int level) {
        PlayerStats s = RpgManager.stats(player);
        boolean all = name.equalsIgnoreCase("all");
        int count = 0;
        for (Stat stat : Stat.values()) {
            if (all || stat.name().equalsIgnoreCase(name)) {
                s.setStatLevel(stat, level);
                count++;
            }
        }
        if (count == 0) {
            player.sendSystemMessage(Component.literal("No stat matched \"" + name + "\". Try: "
                    + java.util.Arrays.toString(Stat.values())).withStyle(ChatFormatting.RED));
            return 0;
        }
        RpgManager.markDirty(player);
        RpgManager.applyAttributes(player);
        RpgManager.sync(player);
        player.sendSystemMessage(Component.literal("Set " + count + " stat(s) to level " + level + ".")
                .withStyle(ChatFormatting.GOLD));
        return count;
    }

    private static int setSkill(ServerPlayer player, String name, int level) {
        PlayerStats s = RpgManager.stats(player);
        boolean all = name.equalsIgnoreCase("all");
        int count = 0;
        for (Skill skill : Skill.values()) {
            if (all || skill.name().equalsIgnoreCase(name)) {
                s.setSkillLevel(skill, level);
                count++;
            }
        }
        if (count == 0) {
            player.sendSystemMessage(Component.literal("No skill matched \"" + name + "\". Try: "
                    + java.util.Arrays.toString(Skill.values())).withStyle(ChatFormatting.RED));
            return 0;
        }
        RpgManager.markDirty(player);
        RpgManager.syncSpellBar(player);
        player.sendSystemMessage(Component.literal("Set " + count + " skill(s) to level " + level + ".")
                .withStyle(ChatFormatting.GREEN));
        return count;
    }

    // --- /stats ---

    private static void printStats(ServerPlayer player) {
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

        player.sendSystemMessage(Component.literal("--- Your Magic ---").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (School school : School.values()) {
            int lvl = s.getSchoolLevel(school);
            int tiers = 0;
            for (int t = 1; t <= School.MAX_TIERS; t++) {
                if (Spell.of(school, t) != null && lvl >= school.unlockLevel(t)) {
                    tiers++;
                }
            }
            player.sendSystemMessage(Component.literal(String.format(
                    "%-18s school Lv %3d/%d   %d/%d spells unlocked",
                    school.displayName(), lvl, StatFormulas.LEVEL_CAP, tiers, school.tierCount()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (int t = 1; t <= School.MAX_TIERS; t++) {
                Spell sp = Spell.of(school, t);
                if (sp == null) {
                    continue;
                }
                int slvl = s.getSpellLevel(sp);
                boolean locked = lvl < school.unlockLevel(t);
                player.sendSystemMessage(Component.literal(String.format(
                        "   %-24s %s",
                        sp.displayName(),
                        locked ? "locked (school Lv " + school.unlockLevel(t) + ")"
                                : "Lv " + slvl + "/" + StatFormulas.LEVEL_CAP + "  " + StatFormulas.effectivenessPercent(slvl) + "%")));
            }
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
    }
}
