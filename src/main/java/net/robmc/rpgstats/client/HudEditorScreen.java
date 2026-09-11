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
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SetSlotOptionsPacket;
import net.robmc.claimguard.network.SetSlotWeaponPacket;
import net.robmc.claimguard.network.SetSpellSlotPacket;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.client.magic.ClientSpells;
import net.robmc.rpgstats.client.magic.SpellBarOverlay;
import net.robmc.rpgstats.client.magic.SpellIcons;
import net.robmc.rpgstats.magic.Spell;
import org.lwjgl.glfw.GLFW;

/**
 * Press J: the movable HUD elements show with a labelled outline - drag to
 * reposition. Right-click a hotbar SLOT to rebind its key directly (the next key or
 * mouse button you press becomes it, with a warning + confirm if it would stomp an
 * important control); right-click a spell/cast bar SLOT instead opens its options
 * popup (ray-bar cycle mode, auto cast, forced weapon), which has its own "Rebind
 * Key" button for the same key-capture flow. J or Done to finish; positions save.
 */
public class HudEditorScreen extends Screen {

    private static final int HOTBAR_W = 182;
    private static final int HOTBAR_H = 22;

    // dragging an element
    private String dragging;
    private int grabX, grabY, baseX, baseY;

    // right-click a bar's grip -> a small opacity/orientation popup for that bar
    private Integer barMenuOpen;
    private boolean draggingOpacitySlider;

    private static final int MENU_W = 140;
    private static final int MENU_H = 58;

    // shift-right-click a spell slot -> its multi-bind ("ray bar") options popup
    private Integer slotMenuOpen;

    private static final int SLOT_MENU_W = 150;
    private static final int SLOT_MENU_H = 118;

    // dragging a spell/skill binding out of a bar slot
    private int draggingSpellFrom = -1;

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

    private int[] statusBox() {
        int x = RpgHudOverlay.statusDefaultLeft(width) + HudLayout.offX(HudLayout.STATUS);
        int y = RpgHudOverlay.statusDefaultTop(height) + HudLayout.offY(HudLayout.STATUS);
        return new int[]{x, y - 1, RpgHudOverlay.STATUS_W, RpgHudOverlay.STATUS_H};
    }

    private int[] castBarBox() {
        int x = net.robmc.rpgstats.client.magic.CastBarOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.CAST_BAR);
        int y = net.robmc.rpgstats.client.magic.CastBarOverlay.defaultTop(height) + HudLayout.offY(HudLayout.CAST_BAR);
        return new int[]{x - 1, y - 12, net.robmc.rpgstats.client.magic.CastBarOverlay.W + 2,
                net.robmc.rpgstats.client.magic.CastBarOverlay.H + 14};
    }

    private int[] potionIconsBox() {
        int x = RpgHudOverlay.potionIconsDefaultLeft(width) + HudLayout.offX(HudLayout.POTION_ICONS);
        int y = RpgHudOverlay.potionIconsDefaultTop(height) + HudLayout.offY(HudLayout.POTION_ICONS);
        return new int[]{x, y, RpgHudOverlay.POTION_ICONS_W, RpgHudOverlay.POTION_ICONS_H};
    }

    private int[] targetFrameBox() {
        int x = TargetFrameOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.TARGET_FRAME);
        int y = TargetFrameOverlay.defaultTop(height) + HudLayout.offY(HudLayout.TARGET_FRAME);
        return new int[]{x - 2, y - 2, TargetFrameOverlay.W + 4, TargetFrameOverlay.H + 4};
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

    private int spellBarX(int bar) {
        return SpellBarOverlay.defaultLeft(bar) + HudLayout.offX(SpellBarOverlay.layoutKey(bar));
    }

    private int spellBarY(int bar) {
        return SpellBarOverlay.defaultTop(height) + HudLayout.offY(SpellBarOverlay.layoutKey(bar));
    }

    private int[] spellBarBox(int bar) {
        String key = SpellBarOverlay.layoutKey(bar);
        return new int[]{spellBarX(bar), spellBarY(bar), SpellBarOverlay.width(key), SpellBarOverlay.height(key)};
    }

    // --- per-bar opacity/orientation popup ---

    private int[] barMenuBox(int bar) {
        int[] b = spellBarBox(bar);
        int x = Math.min(b[0] + b[2] + 6, width - MENU_W - 4);
        int y = Math.max(4, Math.min(b[1], height - MENU_H - 4));
        return new int[]{x, y, MENU_W, MENU_H};
    }

    private int[] opacitySliderBox(int bar) {
        int[] m = barMenuBox(bar);
        return new int[]{m[0] + 6, m[1] + 22, MENU_W - 12, 6};
    }

    private int[] orientationButtonBox(int bar) {
        int[] m = barMenuBox(bar);
        return new int[]{m[0] + 6, m[1] + 38, MENU_W - 12, 14};
    }

    /** The "Bar N" label strip above the bar - generous so the whole label grabs (drag) or opens its menu (right-click). */
    private boolean onGrip(int bar, double mx, double my) {
        int[] b = spellBarBox(bar);
        return mx >= b[0] - 2 && mx <= b[0] + 40 && my >= b[1] - 13 && my <= b[1];
    }

    private void setOpacityFromMouse(int bar, double mx) {
        int[] s = opacitySliderBox(bar);
        float t = (float) ((mx - s[0]) / s[2]);
        HudLayout.setOpacity(SpellBarOverlay.layoutKey(bar), Math.max(0.1f, Math.min(1.0f, t)));
    }

    // --- per-slot multi-bind ("ray bar") options popup ---

    private int[] slotPos(int slot) {
        int bar = slot / SpellBarOverlay.SLOTS;
        int i = slot % SpellBarOverlay.SLOTS;
        String key = SpellBarOverlay.layoutKey(bar);
        boolean horiz = HudLayout.isHorizontal(key);
        int sx = spellBarX(bar);
        int sy = spellBarY(bar);
        int x = horiz ? sx + i * SpellBarOverlay.SLOT : sx;
        int y = horiz ? sy : sy + i * SpellBarOverlay.SLOT;
        return new int[]{x, y};
    }

    private int[] slotMenuBox(int slot) {
        int[] p = slotPos(slot);
        int x = Math.min(p[0] + SpellBarOverlay.SLOT + 6, width - SLOT_MENU_W - 4);
        int y = Math.max(4, Math.min(p[1], height - SLOT_MENU_H - 4));
        return new int[]{x, y, SLOT_MENU_W, SLOT_MENU_H};
    }

    private int[] cycleModeButtonBox(int slot) {
        int[] m = slotMenuBox(slot);
        return new int[]{m[0] + 6, m[1] + 28, SLOT_MENU_W - 12, 14};
    }

    private int[] autoCastButtonBox(int slot) {
        int[] m = slotMenuBox(slot);
        return new int[]{m[0] + 6, m[1] + 46, SLOT_MENU_W - 12, 14};
    }

    private int[] forceWeaponButtonBox(int slot) {
        int[] m = slotMenuBox(slot);
        return new int[]{m[0] + 6, m[1] + 64, SLOT_MENU_W - 12, 14};
    }

    private int[] setWeaponButtonBox(int slot) {
        int[] m = slotMenuBox(slot);
        return new int[]{m[0] + 6, m[1] + 82, SLOT_MENU_W - 12, 14};
    }

    private int[] rebindButtonBox(int slot) {
        int[] m = slotMenuBox(slot);
        return new int[]{m[0] + 6, m[1] + 100, SLOT_MENU_W - 12, 14};
    }

    private void toggleCycleMode(int slot) {
        int next = ClientSpells.slotCycleMode(slot) == 0 ? 1 : 0;
        sendSlotOptions(slot, next, ClientSpells.slotAutoCast(slot), ClientSpells.slotForceWeapon(slot));
    }

    private void toggleAutoCast(int slot) {
        sendSlotOptions(slot, ClientSpells.slotCycleMode(slot), !ClientSpells.slotAutoCast(slot), ClientSpells.slotForceWeapon(slot));
    }

    private void toggleForceWeapon(int slot) {
        sendSlotOptions(slot, ClientSpells.slotCycleMode(slot), ClientSpells.slotAutoCast(slot), !ClientSpells.slotForceWeapon(slot));
    }

    private void sendSlotOptions(int slot, int cycleMode, boolean autoCast, boolean forceWeapon) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetSlotOptionsPacket(slot, cycleMode, autoCast, forceWeapon));
    }

    // --- input ---

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (capturing != null) {
            applyCapture(button < 0 ? null : InputConstants.Type.MOUSE.getOrCreate(button), KeyModifier.NONE);
            return true;
        }

        if (barMenuOpen != null) {
            int bar = barMenuOpen;
            if (button == 0 && inside(opacitySliderBox(bar), mx, my)) {
                setOpacityFromMouse(bar, mx);
                draggingOpacitySlider = true;
                return true;
            }
            if (button == 0 && inside(orientationButtonBox(bar), mx, my)) {
                String key = SpellBarOverlay.layoutKey(bar);
                HudLayout.setHorizontal(key, !HudLayout.isHorizontal(key));
                return true;
            }
            if (inside(barMenuBox(bar), mx, my)) {
                return true; // clicked inside the popup but not on a control - just absorb it
            }
            barMenuOpen = null; // clicked elsewhere - close it, and let this click still do its own thing below
        }

        if (slotMenuOpen != null) {
            int slot = slotMenuOpen;
            if (button == 0 && inside(cycleModeButtonBox(slot), mx, my)) {
                toggleCycleMode(slot);
                return true;
            }
            if (button == 0 && inside(autoCastButtonBox(slot), mx, my)) {
                toggleAutoCast(slot);
                return true;
            }
            if (button == 0 && inside(forceWeaponButtonBox(slot), mx, my)) {
                toggleForceWeapon(slot);
                return true;
            }
            if (button == 0 && inside(setWeaponButtonBox(slot), mx, my)) {
                ClaimGuardNetwork.CHANNEL.sendToServer(new SetSlotWeaponPacket(slot));
                return true;
            }
            if (button == 0 && inside(rebindButtonBox(slot), mx, my)) {
                slotMenuOpen = null;
                startCapture(RpgKeybinds.CAST[slot],
                        "Bar " + (slot / SpellBarOverlay.SLOTS + 1) + " slot " + (slot % SpellBarOverlay.SLOTS + 1));
                return true;
            }
            if (inside(slotMenuBox(slot), mx, my)) {
                return true; // clicked inside the popup but not on a control - just absorb it
            }
            slotMenuOpen = null; // clicked elsewhere - close it, and let this click still do its own thing below
        }

        if (button == 1) { // right-click a slot -> its options popup, or a bar's grip -> its settings popup
            for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
                if (onGrip(bar, mx, my)) {
                    barMenuOpen = bar;
                    slotMenuOpen = null;
                    return true;
                }
            }
            int hs = hotbarSlotAt(mx, my);
            if (hs >= 0) {
                startCapture(minecraft.options.keyHotbarSlots[hs], "Hotbar slot " + (hs + 1));
                return true;
            }
            int ss = spellSlotAt(mx, my);
            if (ss >= 0) {
                slotMenuOpen = ss;
                barMenuOpen = null;
                return true;
            }
        }

        if (button == 0) {
            // a filled spell slot -> drag that binding to another slot
            int ss = spellSlotAt(mx, my);
            if (ss >= 0 && !ClientSpells.slotName(ss).isEmpty()) {
                draggingSpellFrom = ss;
                return true;
            }
            if (inside(statBarsBox(), mx, my)) {
                startDrag(HudLayout.STAT_BARS, mx, my);
                return true;
            }
            if (inside(statusBox(), mx, my)) {
                startDrag(HudLayout.STATUS, mx, my);
                return true;
            }
            if (inside(castBarBox(), mx, my)) {
                startDrag(HudLayout.CAST_BAR, mx, my);
                return true;
            }
            if (inside(targetFrameBox(), mx, my)) {
                startDrag(HudLayout.TARGET_FRAME, mx, my);
                return true;
            }
            if (inside(potionIconsBox(), mx, my)) {
                startDrag(HudLayout.POTION_ICONS, mx, my);
                return true;
            }
            if (inside(hotbarBox(), mx, my)) {
                startDrag(HudLayout.HOTBAR, mx, my);
                return true;
            }
            for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
                if (onGrip(bar, mx, my) || inside(spellBarBox(bar), mx, my)) {
                    startDrag(SpellBarOverlay.layoutKey(bar), mx, my);
                    return true;
                }
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
        if (draggingOpacitySlider && barMenuOpen != null) {
            setOpacityFromMouse(barMenuOpen, mx);
            return true;
        }
        if (draggingSpellFrom >= 0) {
            return true; // handled on release
        }
        if (dragging != null) {
            HudLayout.set(dragging, baseX + (int) mx - grabX, baseY + (int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingOpacitySlider = false;
        if (draggingSpellFrom >= 0) {
            int target = spellSlotAt(mx, my);
            if (target >= 0 && target != draggingSpellFrom) {
                String moving = ClientSpells.slotName(draggingSpellFrom);
                String swapped = ClientSpells.slotName(target);
                sendSlot(target, moving);
                sendSlot(draggingSpellFrom, swapped); // swap (swapped may be empty)
            }
            draggingSpellFrom = -1;
        }
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    private void sendSlot(int slot, String name) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetSpellSlotPacket(slot, name));
        ClientSpells.setSlotLocal(slot, name);
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
                mc.options.keyScreenshot, mc.options.keyTogglePerspective,
                RpgKeybinds.HUD_EDITOR, RpgKeybinds.SPELLBOOK
        };
        for (KeyMapping k : important) {
            if (k != capturing && k.getKey().equals(key) && k.getKeyModifier() == mod) {
                return k.getName() != null ? Component.translatable(k.getName()).getString() : "an important control";
            }
        }
        return null;
    }

    private void clearOurBindingsUsing(InputConstants.Key key, KeyModifier mod) {
        // Not the menu keys (J/U) - those are protected by importantClash and self-heal on restart.
        java.util.List<KeyMapping> ours = new java.util.ArrayList<>();
        java.util.Collections.addAll(ours, minecraft.options.keyHotbarSlots);
        java.util.Collections.addAll(ours, RpgKeybinds.CAST);
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
        return SpellBarOverlay.slotAt(x, y, height);
    }

    // --- render ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x60000000);
        g.drawCenteredString(this.font, "HUD Editor  -  drag a label to move it, right-click a label for opacity/orientation, right-click a slot for its options", this.width / 2, 12, 0xFFFFFF);

        int[] bars = statBarsBox();
        RpgHudOverlay.renderBars(g, this.font, bars[0] + 1, bars[1] + 1);
        outline(g, bars, 0xFF57C97A, "Stat bars");

        int[] status = statusBox();
        g.fill(status[0], status[1], status[0] + status[2], status[1] + status[3], 0x60000000);
        g.drawCenteredString(this.font, "EXHAUSTED", status[0] + status[2] / 2, status[1] + 2, 0xFFFF7777);
        outline(g, status, 0xFFFF7777, "Status");

        int cbX = net.robmc.rpgstats.client.magic.CastBarOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.CAST_BAR);
        int cbY = net.robmc.rpgstats.client.magic.CastBarOverlay.defaultTop(height) + HudLayout.offY(HudLayout.CAST_BAR);
        net.robmc.rpgstats.client.magic.CastBarOverlay.render(g, this.font, cbX, cbY, null, 0.6f, false);
        outline(g, castBarBox(), 0xFFEAD37A, "Cast bar");

        int tfX = TargetFrameOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.TARGET_FRAME);
        int tfY = TargetFrameOverlay.defaultTop(height) + HudLayout.offY(HudLayout.TARGET_FRAME);
        int tfBarY = tfY + TargetFrameOverlay.STRIP_H;
        g.fill(tfX - 1, tfY, tfX + TargetFrameOverlay.W + 1, tfBarY, 0xC8000000);
        g.drawString(this.font, "[RNG] Target", tfX + 2, tfY + 2, 0xFFE1533E, true);
        g.fill(tfX, tfBarY, tfX + TargetFrameOverlay.W, tfBarY + TargetFrameOverlay.BAR_H, 0xC0301010);
        g.fill(tfX, tfBarY, tfX + TargetFrameOverlay.W * 6 / 10, tfBarY + TargetFrameOverlay.BAR_H, 0xFFC0392B);
        outline(g, targetFrameBox(), 0xFFE1533E, "Target frame  (drag me)");

        int[] potions = potionIconsBox();
        g.fill(potions[0], potions[1], potions[0] + potions[2], potions[1] + potions[3], 0x40FFFFFF);
        g.renderOutline(potions[0] + potions[2] - 18, potions[1] + 2, 18, 18, 0x6055DD55);
        outline(g, potions, 0xFF55DD55, "Status effects  (Nourished shows here)");

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

        // the two casting bars
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            String key = SpellBarOverlay.layoutKey(bar);
            boolean horiz = HudLayout.isHorizontal(key);
            int sbX = spellBarX(bar);
            int sbY = spellBarY(bar);
            SpellBarOverlay.render(g, this.font, sbX, sbY, SpellBarOverlay.firstSlot(bar), key);
            // draggable label / grip strip above the bar
            g.fill(sbX - 2, sbY - 13, sbX + 40, sbY - 1, 0xC03A5A88);
            g.renderOutline(sbX - 2, sbY - 13, 42, 12, 0xFF9FC0FF);
            g.drawString(this.font, "Bar " + (bar + 1), sbX + 1, sbY - 10, 0xFFDDE6F5, true);
            g.renderOutline(sbX, sbY, SpellBarOverlay.width(key), SpellBarOverlay.height(key), 0xFF9FC0FF);
            for (int i = 0; i < SpellBarOverlay.SLOTS; i++) {
                int kx = horiz ? sbX + i * SpellBarOverlay.SLOT : sbX;
                int ky = horiz ? sbY : sbY + i * SpellBarOverlay.SLOT;
                keyHint(g, RpgKeybinds.CAST[bar * SpellBarOverlay.SLOTS + i], kx, ky, SpellBarOverlay.SLOT);
            }
            if (barMenuOpen != null && barMenuOpen == bar) {
                renderBarMenu(g, bar);
            }
        }
        if (slotMenuOpen != null) {
            renderSlotMenu(g, slotMenuOpen);
        }

        // spell being dragged between slots
        if (draggingSpellFrom >= 0) {
            Spell sp = Spell.byName(ClientSpells.slotName(draggingSpellFrom));
            if (sp != null) {
                SpellIcons.draw(g, sp, mouseX - 8, mouseY - 8, 16);
            }
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

    private void renderBarMenu(GuiGraphics g, int bar) {
        String key = SpellBarOverlay.layoutKey(bar);
        int[] m = barMenuBox(bar);
        g.fill(m[0], m[1], m[0] + m[2], m[1] + m[3], 0xF0141824);
        g.renderOutline(m[0], m[1], m[2], m[3], 0xFF9FC0FF);
        g.drawString(this.font, "Bar " + (bar + 1) + " settings", m[0] + 6, m[1] + 4, 0xFFDDE6F5, false);

        int[] slider = opacitySliderBox(bar);
        float opacity = HudLayout.opacity(key);
        g.drawString(this.font, "Opacity  " + Math.round(opacity * 100) + "%", m[0] + 6, m[1] + 14, 0xFFB0B0B0, false);
        g.fill(slider[0], slider[1], slider[0] + slider[2], slider[1] + slider[3], 0xFF202838);
        g.renderOutline(slider[0], slider[1], slider[2], slider[3], 0xFF5A6B85);
        int handleX = slider[0] + (int) (slider[2] * opacity) - 1;
        g.fill(handleX, slider[1] - 1, handleX + 2, slider[1] + slider[3] + 1, 0xFFFFE066);

        int[] btn = orientationButtonBox(bar);
        boolean horiz = HudLayout.isHorizontal(key);
        g.fill(btn[0], btn[1], btn[0] + btn[2], btn[1] + btn[3], 0xFF2A3346);
        g.renderOutline(btn[0], btn[1], btn[2], btn[3], 0xFF9FC0FF);
        String label = horiz ? "Horizontal" : "Vertical";
        g.drawString(this.font, label, btn[0] + (btn[2] - this.font.width(label)) / 2, btn[1] + 3, 0xFFFFFFFF, false);
    }

    private void renderSlotMenu(GuiGraphics g, int slot) {
        int[] m = slotMenuBox(slot);
        g.fill(m[0], m[1], m[0] + m[2], m[1] + m[3], 0xF0141824);
        g.renderOutline(m[0], m[1], m[2], m[3], 0xFF9FC0FF);
        g.drawString(this.font, "Slot " + (slot % SpellBarOverlay.SLOTS + 1) + " options", m[0] + 6, m[1] + 4, 0xFFDDE6F5, false);

        int count = ClientSpells.slotBindCount(slot);
        String listTxt = count <= 1 ? "1 spell bound" : count + " spells stacked (ray bar)";
        g.drawString(this.font, listTxt, m[0] + 6, m[1] + 15, 0xFF8FA0B4, false);

        int[] cycleBtn = cycleModeButtonBox(slot);
        boolean firstAvail = ClientSpells.slotCycleMode(slot) == 1;
        g.fill(cycleBtn[0], cycleBtn[1], cycleBtn[0] + cycleBtn[2], cycleBtn[1] + cycleBtn[3], 0xFF2A3346);
        g.renderOutline(cycleBtn[0], cycleBtn[1], cycleBtn[2], cycleBtn[3], 0xFF9FC0FF);
        g.drawString(this.font, firstAvail ? "Mode: First Available" : "Mode: Cycle",
                cycleBtn[0] + 3, cycleBtn[1] + 3, 0xFFFFFFFF, false);

        int[] autoBtn = autoCastButtonBox(slot);
        boolean autoCast = ClientSpells.slotAutoCast(slot);
        g.fill(autoBtn[0], autoBtn[1], autoBtn[0] + autoBtn[2], autoBtn[1] + autoBtn[3], 0xFF2A3346);
        g.renderOutline(autoBtn[0], autoBtn[1], autoBtn[2], autoBtn[3], 0xFF9FC0FF);
        g.drawString(this.font, "Auto Cast: " + (autoCast ? "On" : "Off"),
                autoBtn[0] + 3, autoBtn[1] + 3, autoCast ? 0xFF9BE38A : 0xFFB0B0B0, false);

        int[] fwBtn = forceWeaponButtonBox(slot);
        boolean forceWeapon = ClientSpells.slotForceWeapon(slot);
        g.fill(fwBtn[0], fwBtn[1], fwBtn[0] + fwBtn[2], fwBtn[1] + fwBtn[3], 0xFF2A3346);
        g.renderOutline(fwBtn[0], fwBtn[1], fwBtn[2], fwBtn[3], 0xFF9FC0FF);
        g.drawString(this.font, "Force Weapon: " + (forceWeapon ? "On" : "Off"),
                fwBtn[0] + 3, fwBtn[1] + 3, forceWeapon ? 0xFF9BE38A : 0xFFB0B0B0, false);

        int[] setBtn = setWeaponButtonBox(slot);
        g.fill(setBtn[0], setBtn[1], setBtn[0] + setBtn[2], setBtn[1] + setBtn[3], 0xFF2A3346);
        g.renderOutline(setBtn[0], setBtn[1], setBtn[2], setBtn[3], 0xFF9FC0FF);
        g.drawString(this.font, "Set from held item", setBtn[0] + 3, setBtn[1] + 3, 0xFFFFFFFF, false);
        String weaponId = ClientSpells.slotWeaponId(slot);
        if (weaponId != null) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    net.minecraft.resources.ResourceLocation.tryParse(weaponId));
            if (item != null) {
                g.pose().pushPose();
                g.pose().translate(setBtn[0] + setBtn[2] - 13, setBtn[1] - 1, 100);
                g.pose().scale(0.7f, 0.7f, 1.0f);
                g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
                g.pose().popPose();
            }
        }

        int[] rebindBtn = rebindButtonBox(slot);
        g.fill(rebindBtn[0], rebindBtn[1], rebindBtn[0] + rebindBtn[2], rebindBtn[1] + rebindBtn[3], 0xFF2A3346);
        g.renderOutline(rebindBtn[0], rebindBtn[1], rebindBtn[2], rebindBtn[3], 0xFF9FC0FF);
        String keyLabel = RpgKeybinds.CAST[slot].getKey() == InputConstants.UNKNOWN
                ? "-" : RpgKeybinds.CAST[slot].getTranslatedKeyMessage().getString();
        g.drawString(this.font, "Rebind Key (" + keyLabel + ")", rebindBtn[0] + 3, rebindBtn[1] + 3, 0xFFFFFFFF, false);
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
