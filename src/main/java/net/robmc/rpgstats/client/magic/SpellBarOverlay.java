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
            g.drawString(font, abbrev(spell), x + 3, y + 3, tint(spell), false);
            if (ClientSpells.isCasting() && ClientSpells.castingSpell() == spell) {
                int h = (int) (SLOT * ClientSpells.castProgress());
                g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0x8055C9FF);
            }
        }
        g.drawString(font, String.valueOf(index + 1), x + SLOT - 6, y + SLOT - 8, 0xFF808080, false);
    }

    static String abbrev(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> "M>S";
            case STAMINA_TO_HEALTH -> "S>H";
            case HEALTH_TO_MANA -> "H>M";
            case MAGIC_BOLT -> "Bolt";
        };
    }

    static int tint(Spell spell) {
        return spell.isTransfer() ? 0xFF9FE0FF : 0xFF5AA0FF;
    }
}
