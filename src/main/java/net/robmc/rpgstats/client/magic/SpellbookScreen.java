package net.robmc.rpgstats.client.magic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SetSpellSlotPacket;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.magic.School;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.FighterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Press U. Two expandable books: the Spellbook (every magic school, expand one
 * to see its spells) and the Skills Book (the fighter-type classes - names only
 * for now, abilities not designed yet). Your two 9-slot casting bars sit on the
 * right. Drag a spell row onto a slot to bind it; drag a bound slot off to clear it.
 * Which rows are expanded, and the scroll position, are remembered for the rest
 * of your login (see {@link SpellbookUiState}) - everything starts collapsed on
 * a fresh login and wipes again when you log out.
 */
public class SpellbookScreen extends Screen {

    private static final int PANEL_W = 392;
    private static final int PANEL_H = 248;
    private static final int ROW_H = 22;
    private static final int LIST_W = 250;

    private static final String BOOK_SPELLS = "BOOK_SPELLS";
    private static final String BOOK_SKILLS = "BOOK_SKILLS";

    private enum Kind { BOOK, HEADER, SPELL, LOCKED, EMPTY, CLASS_HEADER, CLASS_PLACEHOLDER }

    private record Row(Kind kind, String group, String name, int tier, int y, int indent) {
    }

    private final Set<String> expanded = SpellbookUiState.expanded();
    private int scroll = SpellbookUiState.scroll();

    private String draggingName;
    private int draggingSlot = -1;

    public SpellbookScreen() {
        super(Component.literal("Spellbook"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 60, panelTop() + PANEL_H - 26, 120, 20).build());
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public void removed() {
        SpellbookUiState.setScroll(scroll); // expanded() is the live shared set - already up to date
        super.removed();
    }

    private int panelLeft() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelTop() {
        return (this.height - PANEL_H) / 2;
    }

    private int listX() {
        return panelLeft() + 10;
    }

    private int listY() {
        return panelTop() + 32;
    }

    private int listH() {
        return PANEL_H - 32 - 32;
    }

    private int barX(int bar) {
        return panelLeft() + PANEL_W - 12 - (StatFormulas.BAR_COUNT - bar) * (SpellBarOverlay.SLOT + 4);
    }

    private int barY() {
        return panelTop() + 40;
    }

    // --- row layout ---

    private List<Row> rows() {
        List<Row> list = new ArrayList<>();
        int y = 0;

        list.add(new Row(Kind.BOOK, BOOK_SPELLS, "Spellbook", 0, y, 0));
        y += ROW_H;
        if (expanded.contains(BOOK_SPELLS)) {
            for (School school : School.values()) {
                list.add(new Row(Kind.HEADER, school.name(), school.displayName(), 0, y, 8));
                y += ROW_H;
                if (expanded.contains(school.name())) {
                    int lvl = ClientSpells.schoolLevel(school);
                    for (int tier = 1; tier <= school.tierCount(); tier++) {
                        Spell spell = Spell.of(school, tier);
                        if (spell == null) {
                            list.add(new Row(Kind.EMPTY, school.name(), "", tier, y, 20));
                        } else if (lvl >= school.unlockLevel(tier)) {
                            list.add(new Row(Kind.SPELL, school.name(), spell.name(), tier, y, 20));
                        } else {
                            list.add(new Row(Kind.LOCKED, school.name(), spell.name(), tier, y, 20));
                        }
                        y += ROW_H;
                    }
                }
            }
        }

        list.add(new Row(Kind.BOOK, BOOK_SKILLS, "Skills Book", 0, y, 0));
        y += ROW_H;
        if (expanded.contains(BOOK_SKILLS)) {
            for (FighterClass fc : FighterClass.values()) {
                list.add(new Row(Kind.CLASS_HEADER, fc.name(), fc.displayName(), 0, y, 8));
                y += ROW_H;
                if (expanded.contains(fc.name())) {
                    list.add(new Row(Kind.CLASS_PLACEHOLDER, fc.name(), "", 0, y, 20));
                    y += ROW_H;
                }
            }
        }
        return list;
    }

    private int unlockedCount(School school) {
        int lvl = ClientSpells.schoolLevel(school);
        int n = 0;
        for (int t = 1; t <= school.tierCount(); t++) {
            if (Spell.of(school, t) != null && lvl >= school.unlockLevel(t)) {
                n++;
            }
        }
        return n;
    }

    private int contentH() {
        List<Row> r = rows();
        return r.isEmpty() ? 0 : r.get(r.size() - 1).y + ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, contentH() - listH());
    }

    // --- input ---

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * 20)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int slot = slotAt(mx, my);
            if (slot >= 0 && !ClientSpells.slotName(slot).isEmpty()) {
                draggingSlot = slot;
                return true;
            }
            if (mx >= listX() && mx <= listX() + LIST_W && my >= listY() && my <= listY() + listH()) {
                int localY = (int) (my - listY()) + scroll;
                for (Row row : rows()) {
                    if (localY >= row.y && localY < row.y + ROW_H) {
                        if (row.kind == Kind.BOOK || row.kind == Kind.HEADER || row.kind == Kind.CLASS_HEADER) {
                            if (expanded.contains(row.group)) {
                                expanded.remove(row.group);
                            } else {
                                expanded.add(row.group);
                            }
                            scroll = Math.min(scroll, maxScroll());
                        } else if (row.kind == Kind.SPELL) {
                            draggingName = row.name;
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int slot = slotAt(mx, my);
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
        return super.mouseReleased(mx, my, button);
    }

    private void send(int slot, String name) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetSpellSlotPacket(slot, name));
        ClientSpells.setSlotLocal(slot, name);
    }

    private int slotAt(double mx, double my) {
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            int bx = barX(bar);
            if (mx < bx || mx > bx + SpellBarOverlay.SLOT) {
                continue;
            }
            int i = (int) ((my - barY()) / SpellBarOverlay.SLOT);
            if (i >= 0 && i < SpellBarOverlay.SLOTS) {
                return bar * SpellBarOverlay.SLOTS + i;
            }
        }
        return -1;
    }

    // --- render ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int left = panelLeft();
        int top = panelTop();

        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0140F1E);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF3A5A88);
        g.drawString(this.font, "SPELLBOOK & SKILLS", left + 14, top + 12, 0xFF9FC0FF, false);
        g.drawString(this.font, "drag a spell onto a bar slot", left + 148, top + 13, 0xFF6A6A6A, false);

        int vx = listX();
        int vy = listY();
        int vh = listH();
        g.fill(vx - 2, vy - 2, vx + LIST_W + 2, vy + vh + 2, 0x50000000);
        g.enableScissor(vx, vy, vx + LIST_W, vy + vh);
        for (Row row : rows()) {
            int ry = vy + row.y - scroll;
            if (ry + ROW_H < vy || ry > vy + vh) {
                continue;
            }
            drawRow(g, row, vx, ry);
        }
        g.disableScissor();

        // scrollbar
        int max = maxScroll();
        if (max > 0) {
            int trackH = vh;
            int thumbH = Math.max(16, (int) ((long) trackH * vh / contentH()));
            int thumbY = vy + (int) ((long) (trackH - thumbH) * scroll / max);
            g.fill(vx + LIST_W + 3, vy, vx + LIST_W + 6, vy + vh, 0x40FFFFFF);
            g.fill(vx + LIST_W + 3, thumbY, vx + LIST_W + 6, thumbY + thumbH, 0xAAB9C0D0);
        }

        // the two bars
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            SpellBarOverlay.render(g, this.font, barX(bar), barY(), SpellBarOverlay.firstSlot(bar));
            g.drawString(this.font, "Bar " + (bar + 1), barX(bar) - 1, barY() - 10, 0xFF9FC0FF, false);
        }

        // drag ghost
        String ghost = draggingName != null ? draggingName
                : (draggingSlot >= 0 ? ClientSpells.slotName(draggingSlot) : "");
        Spell gs = Spell.byName(ghost);
        if (gs != null) {
            SpellIcons.draw(g, gs, mouseX - 8, mouseY - 8, 16);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics g, Row row, int x, int y) {
        int bx = x + row.indent;
        switch (row.kind) {
            case BOOK -> {
                boolean open = expanded.contains(row.group);
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0xE01A2438);
                g.renderOutline(bx, y, LIST_W - row.indent, ROW_H - 3, 0xFF3A5A88);
                g.drawString(this.font, (open ? "▼ " : "▶ ") + row.name, bx + 6, y + 6, 0xFFFFE066, false);
            }
            case HEADER -> {
                School school = School.valueOf(row.group);
                boolean open = expanded.contains(row.group);
                int lvl = ClientSpells.schoolLevel(school);
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0xC02A3346);
                g.drawString(this.font, (open ? "[-] " : "[+] ") + row.name, bx + 6, y + 3, 0xFFDDE6F5, false);
                String tag = "Lv " + lvl + "  " + unlockedCount(school) + "/" + school.tierCount();
                g.drawString(this.font, tag, x + LIST_W - 4 - this.font.width(tag), y + 3, 0xFF8FA0B4, false);
            }
            case CLASS_HEADER -> {
                boolean open = expanded.contains(row.group);
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0xC02A2A38);
                g.drawString(this.font, (open ? "[-] " : "[+] ") + row.name, bx + 6, y + 3, 0xFFDDE6F5, false);
                String tag = "coming soon";
                g.drawString(this.font, tag, x + LIST_W - 4 - this.font.width(tag), y + 3, 0xFF6A6A6A, false);
            }
            case CLASS_PLACEHOLDER -> {
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0x50202838);
                g.drawString(this.font, "abilities not designed yet", bx + 8, y + 6, 0xFF5A5A5A, false);
            }
            case EMPTY -> {
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0x50202838);
                g.drawString(this.font, "tier " + row.tier + "  -", bx + 8, y + 6, 0xFF5A5A5A, false);
            }
            case LOCKED -> {
                Spell spell = Spell.valueOf(row.name);
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0x80181C24);
                g.drawString(this.font, "🔒 " + spell.displayName(), bx + 8, y + 3, 0xFF6A6A6A, false);
                String need = "Lv " + spell.unlockLevel();
                g.drawString(this.font, need, x + LIST_W - 4 - this.font.width(need), y + 3, 0xFF6A6A6A, false);
            }
            case SPELL -> {
                Spell spell = Spell.valueOf(row.name);
                int lvl = ClientSpells.spellLevel(spell);
                g.fill(bx, y, x + LIST_W, y + ROW_H - 3, 0xC0202838);
                SpellIcons.draw(g, spell, bx + 2, y + 1, ROW_H - 6);
                g.drawString(this.font, spell.displayName(), bx + 24, y + 3, 0xFFFFFFFF, false);
                g.drawString(this.font, describe(spell), bx + 24, y + 12, 0xFF8FA0B4, false);
                String lt = "Lv " + lvl + " (" + StatFormulas.effectivenessPercent(lvl) + "%)";
                g.drawString(this.font, lt, x + LIST_W - 4 - this.font.width(lt), y + 3, 0xFFB9A9E3, false);
            }
        }
    }

    private static String describe(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> "Mana -> Stamina";
            case STAMINA_TO_HEALTH -> "Stamina -> Health";
            case HEALTH_TO_MANA -> "Health -> Mana";
            case MAGIC_BOLT -> "slow orb, splash on hit";
            case SUNDER -> "slow bolt, makes them bleed";
            case HEAL_OTHER -> "fast bolt, heals what it hits";
            case AWAY -> "knock back / self speed buff at feet";
            case WARD -> "instant 15+ HP damage shield on you";
            case BRIGHT_LIGHT -> "blinds anyone facing the blast";
            case EMBER_DART -> "fast bolt, stacks a burn (x3)";
            case SERPENTS_PLUME -> "instant fire ray, dims their vision";
            case SUNBURST -> "fast bolt, knocks up + keeps burn";
            case PYROCLASM -> "arcing bolt, big knock-up + fall";
            case CINDER_MAELSTROM -> "lingering fire field, burns everyone in it";
            case HEARTWELL -> "big self-heal, splashes 25% to allies near you";
            case WITHER -> "bolt: -15% their cast speed, -10% their spell dmg";
            case SLUMP -> "bolt: cuts their max HP and stamina for a while";
            case PESTILENCE -> "instant disease ray, damage over 5s";
            case HEXDRAIN -> "bolt: steal mana from a player (1 min CD)";
            case LIGHTNING_STRIKE -> "aimed - a bolt from the sky onto a target";
            case SPEED_OF_WIND -> "channel: haste an ally, drains your mana";
            case CHAIN_SHOCK -> "instant ray, arcs to nearby targets for less";
            case WIND_LURE -> "yank a target up and toward you";
            case HOWLING_IMPACT -> "bolt bursts into a damaging wind gust";
            case WATER_BREATHING -> "aim: water breathing + swim speed, 2 min";
            case ICE_WALL -> "raise a 3x3 wall of ice for 15s";
            case WATER_ORB -> "ice bolt, shatters into slippery patches";
            case WATER_SPOUT -> "toggle beam, rewards steady tracking";
            case RIPTIDE -> "a wave that scoops enemies up, then bursts";
            case EARTHEN_PATH -> "mossy patch: slows & chips; ignites if burned";
            case SEISMIC_PILLAR -> "erupt the ground, fling a target's own way";
            case QUAKE_STOMP -> "leap up, then dive-bomb where you aim";
            case BARK_SKIN -> "aim: -25% melee & arrow damage, 45s";
            case FISSURE -> "tear a trench through the ground you face";
            case BONE_SPEAR -> "a bone bolt - plain damage";
            case RAISE_MINION -> "raise a skeleton to fight for you (30s)";
            case SOUL_DRAIN -> "fast bolt, steals ~25 health from a player";
            case EYE_DECAY -> "bolt: blinds their screen in blood 1.5s";
            case WRAITH_STEP -> "blink 5 blocks, blast where you passed";
            case CHANT_OF_GROWTH -> "hurry nearby crops & saplings along";
            case BATTLE_HYMN -> "channel: +10 STR/QUI/VIT to allies, 15 min";
            case WORD_OF_UNMAKING -> "erase a few blocks (drop them through)";
            case FEARCRAFT -> "3s chant, monsters flee you for 5s";
            case SILENCING_WHISPER -> "interrupt + seal a caster's school 2s";
            case ARCANE_BOLT -> "fast piercing bolt, marks for +30%";
            case MANA_RIFT -> "cracked-ground AoE, damages + disrupts casts";
            case SPELLBIND -> "instant bolt: locks their casting for 3s";
            case ARCANE_SHIFT -> "blink 7 blocks, afterimage loses hostiles";
            case ASTRAL_ANNIHILATION -> "channelled beam, ramps up, roots you";
            case THORNS -> "buff: reflect part of melee/arrow hits";
            case WOLF_FORM -> "toggle wolf: +25% speed, bite + leap";
            case DOLPHIN_FORM -> "toggle dolphin (water): breathe, +35% swim";
            case BLOOM_OF_RENEWAL -> "heal an ally over time, cleanse debuffs";
            case SWARM_OF_THE_WILD -> "nearby animals maul your target 10s";
            case DIVINE_SMITE -> "arm next melee: radiant dmg + mana back";
            case BLESSING_OF_PROTECTION -> "ally shield, bursts into an AoE heal";
            case PURIFYING_WAVE -> "holy cone: hurt foes, heal + cleanse allies";
            case SACRIFICIAL_HEAL -> "spend your HP, ally heals 1.6x back";
            case MASS_MEND -> "channel: radius heal, most on the hurt";
            case LIGHTNING_TOTEM -> "totem: buff allies, shock enemies";
            case HEX_OF_FRAILTY -> "curse: -dmg/-speed 10s, heal on its kill";
            case HEALING_TOTEM -> "totem: heals the group every 5s";
            case FIRE_SHOCK -> "burning DoT, heals you on the last tick";
            case PLAGUE -> "beam drops 3 poison frogs on the target";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
