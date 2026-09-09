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
import net.robmc.rpgstats.client.HudLayout;
import net.robmc.rpgstats.magic.Spell;

/** The vertical, 8-slot spell casting bar. Separate from the vanilla hotbar; movable in the J editor. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpellBarOverlay {

    public static final int SLOT = 20;
    public static final int SLOTS = 8;
    public static final int BAR_H = SLOT * SLOTS;

    public static int defaultLeft() {
        return 8;
    }

    public static int defaultTop(int screenH) {
        return (screenH - BAR_H) / 2;
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
        int x = defaultLeft() + HudLayout.offX(HudLayout.SPELL_BAR);
        int y = defaultTop(screenH) + HudLayout.offY(HudLayout.SPELL_BAR);
        render(g, mc.font, x, y);
    };

    public static void render(GuiGraphics g, Font font, int x, int y) {
        for (int i = 0; i < SLOTS; i++) {
            int sy = y + i * SLOT;
            drawSlot(g, font, x, sy, i);
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

            // spell level, small, tucked into the top-left corner
            String lvl = Integer.toString(ClientSpells.spellLevel(spell));
            g.pose().pushPose();
            g.pose().translate(x + 1.0f, y + 0.5f, 0.0f);
            g.pose().scale(0.7f, 0.7f, 1.0f);
            g.drawString(font, lvl, 0, 0, 0xFFFFE066, true);
            g.pose().popPose();
        }
        g.drawString(font, String.valueOf(index + 1), x + SLOT - 6, y + SLOT - 8, 0xFF808080, false);
    }
}
