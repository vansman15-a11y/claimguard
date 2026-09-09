package net.robmc.rpgstats.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.robmc.rpgstats.client.magic.ClientRecall;
import net.robmc.rpgstats.item.SkillItem;
import net.robmc.rpgstats.skill.Skill;

/** Client-side reaction to right-clicking a skill item. */
public final class SkillItemUse {

    private SkillItemUse() {
    }

    public static void activate(Skill skill) {
        if (skill == Skill.RECALL && !ClientRecall.isRecalling()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            SkillItem.sendActivate(Skill.RECALL);
                        }
                        mc.setScreen(null);
                    },
                    Component.literal("Recall to Bindstone"),
                    Component.literal("Channel for 1 minute, then teleport to your bindstone. Taking a hit cancels it.")));
            return;
        }
        SkillItem.sendActivate(skill);
    }
}
