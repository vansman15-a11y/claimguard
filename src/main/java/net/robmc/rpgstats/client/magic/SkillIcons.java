package net.robmc.rpgstats.client.magic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.robmc.rpgstats.skill.Skill;

/** Icons for general skills. Vanilla item sprites for now (16x16, fixed). */
public final class SkillIcons {

    private static final ItemStack REST = new ItemStack(Items.CAMPFIRE);
    private static final ItemStack RECALL = new ItemStack(Items.ENDER_PEARL);

    private SkillIcons() {
    }

    public static ItemStack stack(Skill skill) {
        return switch (skill) {
            case REST -> REST;
            case RECALL -> RECALL;
        };
    }

    public static void draw(GuiGraphics g, Skill skill, int x, int y) {
        g.renderItem(stack(skill), x, y);
    }
}
