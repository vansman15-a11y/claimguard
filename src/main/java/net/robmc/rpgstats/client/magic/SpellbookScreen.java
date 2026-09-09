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
import net.robmc.rpgstats.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * Press U. Two sections - Weak Magic (the spells) and Skills (general abilities
 * like Rest) - plus your 8-slot bar on the right. Drag a row onto a slot to bind
 * it; drag a bound slot off to clear it.
 */
public class SpellbookScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 262;
    private static final int ROW_H = 24;
    private static final int HEADER_GAP = 18;

    private record Entry(String name, String display, String desc, int level, boolean skill, int y) {
    }

    private String draggingName;   // dragging a row or a bound slot
    private int draggingSlot = -1;

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

    private int rowLeft() {
        return panelLeft() + 12;
    }

    private int rowRight() {
        return panelLeft() + 234;
    }

    private int barX() {
        return panelLeft() + PANEL_W - 40;
    }

    private int barY() {
        return panelTop() + 30;
    }

    /** All rows with their laid-out Y, in draw order. */
    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        int y = panelTop() + 40;
        for (Spell spell : Spell.values()) {
            int lvl = ClientSpells.spellLevel(spell);
            list.add(new Entry(spell.name(), spell.displayName(), spellDesc(spell), lvl, false, y));
            y += ROW_H;
        }
        y += HEADER_GAP; // "Skills" sub-header
        for (Skill skill : Skill.values()) {
            int lvl = ClientSpells.skillLevel(skill);
            list.add(new Entry(skill.name(), skill.displayName(), skill.blurb(), lvl, true, y));
            y += ROW_H;
        }
        return list;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Entry e : entries()) {
                if (mouseX >= rowLeft() && mouseX <= rowRight() && mouseY >= e.y && mouseY <= e.y + ROW_H - 4) {
                    draggingName = e.name;
                    return true;
                }
            }
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0 && !ClientSpells.slotName(slot).isEmpty()) {
                draggingSlot = slot;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int slot = slotAt(mouseX, mouseY);
        if (draggingName != null && slot >= 0) {
            send(slot, draggingName);
        } else if (draggingSlot >= 0 && slot >= 0 && slot != draggingSlot) {
            send(slot, ClientSpells.slotName(draggingSlot));
            send(draggingSlot, "");
        } else if (draggingSlot >= 0 && slot < 0) {
            send(draggingSlot, "");
        }
        draggingName = null;
        draggingSlot = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void send(int slot, String name) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetSpellSlotPacket(slot, name));
        ClientSpells.setSlotLocal(slot, name);
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
        this.renderBackground(g);

        int left = panelLeft();
        int top = panelTop();
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0140F1E);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF3A5A88);
        g.drawString(this.font, "SPELLBOOK", left + 14, top + 12, 0xFF9FC0FF, false);
        g.drawString(this.font, "drag a row to a slot -->", left + 100, top + 13, 0xFF6A6A6A, false);

        List<Entry> es = entries();
        // section headers, positioned from the first row of each section
        g.drawString(this.font, "Weak Magic", left + 14, es.get(0).y - 12, 0xFFB9A9E3, false);
        for (Entry e : es) {
            if (e.skill) {
                g.drawString(this.font, "Skills", left + 14, e.y - 12, 0xFF9BE7A0, false);
                break;
            }
        }

        for (Entry e : es) {
            g.fill(rowLeft(), e.y, rowRight(), e.y + ROW_H - 4, 0xC0202838);
            if (e.skill) {
                SkillIcons.draw(g, Skill.valueOf(e.name), rowLeft() + 2, e.y + 1);
            } else {
                SpellIcons.draw(g, Spell.valueOf(e.name), rowLeft() + 2, e.y + 1, ROW_H - 6);
            }
            String lvlTxt = "Lv " + e.level + " (" + StatFormulas.effectivenessPercent(e.level) + "%)";
            g.drawString(this.font, e.display, rowLeft() + 26, e.y + 3, 0xFFFFFFFF, false);
            g.drawString(this.font, lvlTxt, rowRight() - 2 - this.font.width(lvlTxt), e.y + 13,
                    e.skill ? 0xFF9BE7A0 : 0xFFB9A9E3, false);
            g.drawString(this.font, e.desc, rowLeft() + 26, e.y + 13, 0xFF8FA0B4, false);
        }

        SpellBarOverlay.render(g, this.font, barX(), barY());
        g.drawString(this.font, "Bar", barX() - 2, barY() - 10, 0xFF9FC0FF, false);

        // drag ghost
        String ghost = draggingName != null ? draggingName
                : (draggingSlot >= 0 ? ClientSpells.slotName(draggingSlot) : "");
        Spell gs = Spell.byName(ghost);
        Skill gk = Skill.byName(ghost);
        if (gs != null) {
            SpellIcons.draw(g, gs, mouseX - 8, mouseY - 8, 16);
        } else if (gk != null) {
            SkillIcons.draw(g, gk, mouseX - 8, mouseY - 8);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String spellDesc(Spell spell) {
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
