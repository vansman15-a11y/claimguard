package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.client.HudLayout;
import net.robmc.rpgstats.client.RpgHudOverlay;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.Skill;

/** Two vertical 9-slot casting bars on the left. Separate from the vanilla hotbar; movable in the J editor. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpellBarOverlay {

    public static final int SLOT = 20;
    public static final int SLOTS = StatFormulas.BAR_SLOTS;   // per bar
    public static final int BAR_H = SLOT * SLOTS;

    /** Anchor for a bar: bar 0 sits at the edge, bar 1 just to its right. */
    public static int defaultLeft(int bar) {
        return 8 + bar * (SLOT + 3);
    }

    public static String layoutKey(int bar) {
        return bar == 0 ? HudLayout.SPELL_BAR : HudLayout.SPELL_BAR_2;
    }

    public static int defaultTop(int screenH) {
        return (screenH - BAR_H) / 2;
    }

    /** First global slot index of a bar. */
    public static int firstSlot(int bar) {
        return bar * SLOTS;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_spell_bar", BAR);
    }

    private static final IGuiOverlay BAR = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            int x = defaultLeft(bar) + HudLayout.offX(layoutKey(bar));
            int y = defaultTop(screenH) + HudLayout.offY(layoutKey(bar));
            render(g, mc.font, x, y, firstSlot(bar));
        }

        if (ClientRecall.isRecalling()) {
            g.drawCenteredString(mc.font, "Recalling  " + ClientRecall.secondsLeft() + "s",
                    screenW / 2, screenH / 2 + 24, 0xFF66D9FF);
        }
    };

    /** Render one bar's SLOTS slots starting from global slot {@code firstSlot}. */
    public static void render(GuiGraphics g, Font font, int x, int y, int firstSlot) {
        for (int i = 0; i < SLOTS; i++) {
            drawSlot(g, font, x, y + i * SLOT, firstSlot + i);
        }
    }

    static void drawSlot(GuiGraphics g, Font font, int x, int y, int index) {
        g.fill(x, y, x + SLOT, y + SLOT, 0xC0101010);
        g.renderOutline(x, y, SLOT, SLOT, 0xFF3A3A3A);

        Spell spell = ClientSpells.slot(index);
        if (spell != null) {
            SpellIcons.draw(g, spell, x + 2, y + 2, SLOT - 4);

            // On cooldown: the slot sits "lit" just after the cast and fades back to normal as it recharges.
            float cdp = ClientSpells.cooldownProgress(spell);
            if (cdp < 1.0f) {
                int a = (int) (0x8C * (1.0f - cdp));
                g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (a << 24) | 0x00FFE2A6);
            }

            // Off cooldown: a very short, subtle flash to say "ready".
            float flash = ClientSpells.readyFlash(spell);
            if (flash > 0.0f) {
                g.fill(x, y, x + SLOT, y + SLOT, ((int) (0x3C * flash) << 24) | 0x00FFFFFF);
                g.renderOutline(x, y, SLOT, SLOT, ((int) (0xC0 * flash) << 24) | 0x00FFFFFF);
            }

            // Casting now: a fill that rises from the bottom.
            if (ClientSpells.isCasting() && ClientSpells.castingSpell() == spell) {
                int h = (int) (SLOT * ClientSpells.castProgress());
                g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0x8055C9FF);
            }

            cornerLevel(g, font, x, y, ClientSpells.spellLevel(spell), 0xFFFFE066);
        } else {
            Skill skill = ClientSpells.skillAt(index);
            if (skill != null) {
                SkillIcons.draw(g, skill, x + 2, y + 2);
                if (skill == Skill.REST && RpgHudOverlay.resting) {
                    g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x4033DD33);
                    g.renderOutline(x, y, SLOT, SLOT, 0xFF66FF66);
                }
                if (skill == Skill.RECALL && ClientRecall.isRecalling()) {
                    int h = (int) (SLOT * ClientRecall.progress());
                    g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0x8033C0FF);
                    g.renderOutline(x, y, SLOT, SLOT, 0xFF55D0FF);
                }
                cornerLevel(g, font, x, y, ClientSpells.skillLevel(skill), 0xFF9BE7A0);
            }
        }
        g.drawString(font, String.valueOf(index % SLOTS + 1), x + SLOT - 6, y + SLOT - 8, 0xFF808080, false);
    }

    /** The small level number tucked into a slot's top-left corner. */
    private static void cornerLevel(GuiGraphics g, Font font, int x, int y, int level, int color) {
        g.pose().pushPose();
        g.pose().translate(x + 1.0f, y + 0.5f, 0.0f);
        g.pose().scale(0.7f, 0.7f, 1.0f);
        g.drawString(font, Integer.toString(level), 0, 0, color, true);
        g.pose().popPose();
    }
}
