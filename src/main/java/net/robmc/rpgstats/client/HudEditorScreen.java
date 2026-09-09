package net.robmc.rpgstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyModifier;
import net.robmc.rpgstats.client.magic.SpellBarOverlay;
import org.lwjgl.glfw.GLFW;

/**
 * Press J: the movable HUD elements show with a labelled outline - drag to
 * reposition. Right-click a hotbar / spell-bar SLOT to rebind its key: the next
 * key or mouse button you press becomes it (with a warning + confirm if it would
 * stomp an important control). J or Done to finish; positions save.
 */
public class HudEditorScreen extends Screen {

    private static final int HOTBAR_W = 182;
    private static final int HOTBAR_H = 22;

    // dragging an element
    private String dragging;
    private int grabX, grabY, baseX, baseY;

    // capturing a key for a slot
    private KeyMapping capturing;
    private String capturingLabel;
    private InputConstants.Key pendingKey;
    private KeyModifier pendingMod;
    private String warning;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(Component.literal("Reset positions"), b -> HudLayout.reset())
                .bounds(cx - 122, this.height - 30, 120, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 2, this.height - 30, 120, 20).build());
    }

    // --- element boxes ---

    private int[] statBarsBox() {
        int x = RpgHudOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.STAT_BARS);
        int y = RpgHudOverlay.defaultTop(height) + HudLayout.offY(HudLayout.STAT_BARS);
        return new int[]{x - 1, y - 1, RpgHudOverlay.BAR_W + 2, RpgHudOverlay.BARS_TOTAL_H + 2};
    }

    private int hotbarX() {
        return (width - HOTBAR_W) / 2 + HudLayout.offX(HudLayout.HOTBAR);
    }

    private int hotbarY() {
        return height - HOTBAR_H + HudLayout.offY(HudLayout.HOTBAR);
    }

    private int[] hotbarBox() {
        return new int[]{hotbarX(), hotbarY(), HOTBAR_W, HOTBAR_H};
    }

    private int spellBarX() {
        return SpellBarOverlay.defaultLeft() + HudLayout.offX(HudLayout.SPELL_BAR);
    }

    private int spellBarY() {
        return SpellBarOverlay.defaultTop(height) + HudLayout.offY(HudLayout.SPELL_BAR);
    }

    private int[] spellBarBox() {
        return new int[]{spellBarX(), spellBarY(), SpellBarOverlay.SLOT, SpellBarOverlay.BAR_H};
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (capturing != null) {
            applyCapture(button < 0 ? null : InputConstants.Type.MOUSE.getOrCreate(button), KeyModifier.NONE);
            return true;
        }

        if (button == 1) { // right-click a slot -> rebind
            int hs = hotbarSlotAt(mx, my);
            if (hs >= 0) {
                startCapture(minecraft.options.keyHotbarSlots[hs], "Hotbar slot " + (hs + 1));
                return true;
            }
            int ss = spellSlotAt(mx, my);
            if (ss >= 0) {
                startCapture(RpgKeybinds.CAST[ss], "Spell slot " + (ss + 1));
                return true;
            }
        }

        if (button == 0) {
            if (inside(statBarsBox(), mx, my)) {
                startDrag(HudLayout.STAT_BARS, mx, my);
                return true;
            }
            if (inside(hotbarBox(), mx, my)) {
                startDrag(HudLayout.HOTBAR, mx, my);
                return true;
            }
            if (inside(spellBarBox(), mx, my)) {
                startDrag(HudLayout.SPELL_BAR, mx, my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelCapture();
                return true;
            }
            if (isModifierKey(keyCode)) {
                return true; // wait for the real key
            }
            InputConstants.Key key = keyCode == GLFW.GLFW_KEY_UNKNOWN
                    ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                    : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            applyCapture(key, modifierFrom(modifiers));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void startDrag(String element, double mx, double my) {
        dragging = element;
        grabX = (int) mx;
        grabY = (int) my;
        baseX = HudLayout.offX(element);
        baseY = HudLayout.offY(element);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            HudLayout.set(dragging, baseX + (int) mx - grabX, baseY + (int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    // --- capture / rebind ---

    private void startCapture(KeyMapping mapping, String label) {
        capturing = mapping;
        capturingLabel = label;
        pendingKey = null;
        warning = null;
    }

    private void cancelCapture() {
        capturing = null;
        pendingKey = null;
        warning = null;
    }

    private void applyCapture(InputConstants.Key key, KeyModifier mod) {
        if (key == null) {
            return;
        }
        String clash = importantClash(key, mod);
        if (clash != null && (pendingKey == null || !key.equals(pendingKey) || mod != pendingMod)) {
            pendingKey = key;
            pendingMod = mod;
            warning = "That key is \"" + clash + "\". Press it again to confirm, Esc to cancel.";
            return;
        }

        clearOurBindingsUsing(key, mod);
        capturing.setKeyModifierAndCode(mod, key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        cancelCapture();
    }

    /** If key+mod is currently an important vanilla control, return its display name. */
    private String importantClash(InputConstants.Key key, KeyModifier mod) {
        Minecraft mc = minecraft;
        KeyMapping[] important = {
                mc.options.keyInventory, mc.options.keyDrop, mc.options.keyJump, mc.options.keyShift,
                mc.options.keySprint, mc.options.keyChat, mc.options.keyCommand, mc.options.keySwapOffhand,
                mc.options.keyPickItem, mc.options.keyPlayerList, mc.options.keyAttack, mc.options.keyUse,
                mc.options.keyScreenshot, mc.options.keyTogglePerspective
        };
        for (KeyMapping k : important) {
            if (k != capturing && k.getKey().equals(key) && k.getKeyModifier() == mod) {
                return k.getName() != null ? Component.translatable(k.getName()).getString() : "an important control";
            }
        }
        return null;
    }

    private void clearOurBindingsUsing(InputConstants.Key key, KeyModifier mod) {
        java.util.List<KeyMapping> ours = new java.util.ArrayList<>();
        java.util.Collections.addAll(ours, minecraft.options.keyHotbarSlots);
        java.util.Collections.addAll(ours, RpgKeybinds.CAST);
        ours.add(RpgKeybinds.SPELLBOOK);
        ours.add(RpgKeybinds.HUD_EDITOR);
        for (KeyMapping k : ours) {
            if (k != capturing && k.getKey().equals(key) && k.getKeyModifier() == mod) {
                k.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            }
        }
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    private static KeyModifier modifierFrom(int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            return KeyModifier.CONTROL;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            return KeyModifier.SHIFT;
        }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            return KeyModifier.ALT;
        }
        return KeyModifier.NONE;
    }

    // --- slot hit-testing ---

    private int hotbarSlotAt(double x, double y) {
        int hx = hotbarX() + 3;
        int hy = hotbarY() + 3;
        if (y < hy || y > hy + 16) {
            return -1;
        }
        int i = (int) ((x - hx) / 20);
        return (i >= 0 && i < 9) ? i : -1;
    }

    private int spellSlotAt(double x, double y) {
        int sx = spellBarX();
        int sy = spellBarY();
        if (x < sx || x > sx + SpellBarOverlay.SLOT) {
            return -1;
        }
        int i = (int) ((y - sy) / SpellBarOverlay.SLOT);
        return (i >= 0 && i < SpellBarOverlay.SLOTS) ? i : -1;
    }

    // --- render ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x60000000);
        g.drawCenteredString(this.font, "HUD Editor  -  drag boxes to move, right-click a slot to rebind its key", this.width / 2, 12, 0xFFFFFF);

        int[] bars = statBarsBox();
        RpgHudOverlay.renderBars(g, this.font, bars[0] + 1, bars[1] + 1);
        outline(g, bars, 0xFF57C97A, "Stat bars");

        // hotbar ghost with 9 slots
        int hbX = hotbarX();
        int hbY = hotbarY();
        g.fill(hbX, hbY, hbX + HOTBAR_W, hbY + HOTBAR_H, 0x40FFFFFF);
        outline(g, hotbarBox(), 0xFFFFC24B, "Hotbar  (Shift + 1-9)");
        for (int i = 0; i < 9; i++) {
            int sx = hbX + 3 + i * 20;
            g.renderOutline(sx, hbY + 3, 16, 16, 0x60FFFFFF);
            keyHint(g, minecraft.options.keyHotbarSlots[i], sx, hbY + 3, 16);
        }

        // spell bar with 8 slots
        int sbX = spellBarX();
        int sbY = spellBarY();
        SpellBarOverlay.render(g, this.font, sbX, sbY);
        outline(g, spellBarBox(), 0xFF9FC0FF, "Spell bar  (1-8)");
        for (int i = 0; i < 8; i++) {
            keyHint(g, RpgKeybinds.CAST[i], sbX, sbY + i * SpellBarOverlay.SLOT, SpellBarOverlay.SLOT);
        }

        if (capturing != null) {
            g.fill(0, this.height / 2 - 18, this.width, this.height / 2 + 18, 0xC0000000);
            g.drawCenteredString(this.font, "Rebinding " + capturingLabel + " - press a key or mouse button", this.width / 2, this.height / 2 - 12, 0xFFFFC24B);
            if (warning != null) {
                g.drawCenteredString(this.font, warning, this.width / 2, this.height / 2 + 2, 0xFFFF6060);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void keyHint(GuiGraphics g, KeyMapping k, int x, int y, int size) {
        String label = k.getKey() == InputConstants.UNKNOWN ? "-" : k.getTranslatedKeyMessage().getString();
        if (label.length() > 5) {
            label = label.substring(0, 5);
        }
        g.drawString(this.font, label, x + 1, y + size - 8, 0xFFB0B0B0, false);
    }

    private void outline(GuiGraphics g, int[] box, int colour, String label) {
        g.renderOutline(box[0], box[1], box[2], box[3], colour);
        g.drawString(this.font, label, box[0], box[1] - 10, colour, true);
    }

    private static boolean inside(int[] box, double mx, double my) {
        return mx >= box[0] && mx <= box[0] + box[2] && my >= box[1] && my <= box[1] + box[3];
    }

    @Override
    public void onClose() {
        HudLayout.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
