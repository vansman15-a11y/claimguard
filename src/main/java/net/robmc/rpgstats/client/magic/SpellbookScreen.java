package net.robmc.rpgstats.client.magic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SetSpellSlotPacket;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.magic.Spell;

/**
 * Press U. The Weak Magic page: the four spells on the left, your 8-slot casting
 * bar on the right. Drag a spell onto a slot to bind it; drag a bound slot off
 * to clear it.
 */
public class SpellbookScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 24;

    private Spell dragging;         // dragging a spell FROM the list
    private int draggingSlot = -1;  // dragging a spell OUT of a bar slot
    private int mx, my;

    public SpellbookScreen() {
        super(Component.literal("Spellbook"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 60, panelTop() + PANEL_H - 26, 120, 20).build());
    }

    private int panelLeft() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelTop() {
        return (this.height - PANEL_H) / 2;
    }

    private int spellRowY(int i) {
        return panelTop() + 40 + i * ROW_H;
    }

    private int barX() {
        return panelLeft() + PANEL_W - 40;
    }

    private int barY() {
        return panelTop() + 30;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Spell spell : Spell.values()) {
                int y = spellRowY(spell.ordinal());
                if (mouseX >= panelLeft() + 12 && mouseX <= panelLeft() + 234 && mouseY >= y && mouseY <= y + ROW_H - 4) {
                    dragging = spell;
                    return true;
                }
            }
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0 && ClientSpells.slot(slot) != null) {
                draggingSlot = slot;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int slot = slotAt(mouseX, mouseY);
        if (dragging != null && slot >= 0) {
            send(slot, dragging.name());
        } else if (draggingSlot >= 0 && slot >= 0 && slot != draggingSlot) {
            send(slot, ClientSpells.slotName(draggingSlot));
            send(draggingSlot, "");
        } else if (draggingSlot >= 0 && slot < 0) {
            send(draggingSlot, ""); // dropped outside -> clear
        }
        dragging = null;
        draggingSlot = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void send(int slot, String spellName) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetSpellSlotPacket(slot, spellName));
        ClientSpells.setSlotLocal(slot, spellName); // optimistic
    }

    private int slotAt(double x, double y) {
        int bx = barX();
        int by = barY();
        if (x < bx || x > bx + SpellBarOverlay.SLOT) {
            return -1;
        }
        int i = (int) ((y - by) / SpellBarOverlay.SLOT);
        return (i >= 0 && i < SpellBarOverlay.SLOTS) ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mx = mouseX;
        this.my = mouseY;
        this.renderBackground(g);

        int left = panelLeft();
        int top = panelTop();
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0140F1E);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF3A5A88);
        g.drawString(this.font, "SPELLBOOK  -  Weak Magic", left + 14, top + 12, 0xFF9FC0FF, false);
        g.drawString(this.font, "drag a spell to a slot -->", left + 14, top + 24, 0xFF6A6A6A, false);

        for (Spell spell : Spell.values()) {
            int y = spellRowY(spell.ordinal());
            g.fill(left + 12, y, left + 234, y + ROW_H - 4, 0xC0202838);
            SpellIcons.draw(g, spell, left + 14, y + 1, ROW_H - 6);
            int lvl = ClientSpells.spellLevel(spell);
            String lvlTxt = "Lv " + lvl + " (" + StatFormulas.effectivenessPercent(lvl) + "%)";
            g.drawString(this.font, spell.displayName(), left + 38, y + 3, 0xFFFFFFFF, false);
            g.drawString(this.font, lvlTxt, left + 232 - this.font.width(lvlTxt), y + 13, 0xFFB9A9E3, false);
            g.drawString(this.font, describe(spell), left + 38, y + 13, 0xFF8FA0B4, false);
        }

        // the casting bar
        SpellBarOverlay.render(g, this.font, barX(), barY());
        g.drawString(this.font, "Bar", barX() - 2, barY() - 10, 0xFF9FC0FF, false);

        // drag ghost
        Spell ghost = dragging != null ? dragging
                : (draggingSlot >= 0 ? ClientSpells.slot(draggingSlot) : null);
        if (ghost != null) {
            SpellIcons.draw(g, ghost, mouseX - 8, mouseY - 8, 16);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String describe(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> "Mana -> Stamina";
            case STAMINA_TO_HEALTH -> "Stamina -> Health";
            case HEALTH_TO_MANA -> "Health -> Mana";
            case MAGIC_BOLT -> "slow orb, splash on hit";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
