package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.claim.ClaimTier;
import net.robmc.claimguard.network.ClaimActionPacket;
import net.robmc.claimguard.network.ClaimGuardNetwork;

/**
 * The upgrade table: every tier step, its item cost, and the size it grants, with
 * the next available upgrade highlighted. "Confirm Upgrade" asks the server to
 * spend the items; the server sends a fresh OpenClaimMenuPacket back so this screen
 * refreshes in place (see ClaimMenuClient).
 */
public class ClaimUpgradeScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 190;
    private static final int PANEL_BG = 0xD0100C1A;
    private static final int PANEL_BORDER = 0xFF57C97A;
    private static final int ROW_H = 16;

    private final BlockPos corePos;
    private final ClaimTier current;
    private final String claimName;
    private final boolean boundHere;

    public ClaimUpgradeScreen(BlockPos corePos, int tierOrdinal, String claimName, boolean boundHere) {
        super(Component.literal("Beacon Upgrades"));
        this.corePos = corePos;
        this.current = ClaimTier.byIndex(tierOrdinal);
        this.claimName = claimName;
        this.boundHere = boundHere;
    }

    /** Used by ClaimMenuClient to decide whether a refresh packet belongs to this screen. */
    public boolean isFor(BlockPos pos) {
        return this.corePos.equals(pos);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int btnW = PANEL_W - 30;
        int y = top + PANEL_H - 26;

        boolean canUpgrade = !current.isMaxTier();
        Button confirm = Button.builder(
                Component.literal(canUpgrade ? "Confirm Upgrade" : "Max level reached"),
                b -> ClaimGuardNetwork.CHANNEL.sendToServer(new ClaimActionPacket(corePos, ClaimActionPacket.Action.UPGRADE))
        ).bounds(cx - btnW / 2, y, btnW / 2 - 2, 20).build();
        confirm.active = canUpgrade;
        addRenderableWidget(confirm);

        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                minecraft.setScreen(new ClaimMenuScreen(corePos, current.ordinal(), claimName, boundHere))
        ).bounds(cx + 2, y, btnW / 2 - 2, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_BG);
        g.renderOutline(left, top, PANEL_W, PANEL_H, PANEL_BORDER);

        g.drawCenteredString(this.font, "BEACON UPGRADES", cx, top + 10, 0xFFFFFF);
        g.drawCenteredString(this.font, claimName + "  -  Current: Level " + (current.ordinal() + 1), cx, top + 24, 0xA9C7D6);

        ClaimTier[] tiers = ClaimTier.values();
        int rowY = top + 44;
        int labelX = left + 14;
        int costX = left + 66;
        int sizeX = left + 194;
        int tagX = left + PANEL_W - 42;

        for (int i = 0; i < tiers.length - 1; i++) {
            ClaimTier from = tiers[i];
            ClaimTier to = tiers[i + 1];

            boolean done = i < current.ordinal();
            boolean next = i == current.ordinal();
            int color = done ? 0x5A6B74 : 0xFFFFFF;

            if (next) {
                g.fill(left + 8, rowY - 3, left + PANEL_W - 8, rowY + ROW_H - 5, 0x3357C97A);
            }

            g.drawString(this.font, "L" + (i + 1) + " to " + (i + 2), labelX, rowY, color, false);
            g.drawString(this.font, from.describeUpgradeCost(), costX, rowY, color, false);
            g.drawString(this.font, from.getRadius() + " to " + to.getRadius() + " (" + to.describeFootprint() + ")", sizeX, rowY, color, false);

            if (done) {
                g.drawString(this.font, "done", tagX, rowY, 0x5A6B74, false);
            } else if (next) {
                g.drawString(this.font, "NEXT", tagX, rowY, 0xFFE066, false);
            }
            rowY += ROW_H;
        }

        if (current.isMaxTier()) {
            g.drawCenteredString(this.font, "This claim is at the maximum size.", cx, rowY + 4, 0xFFE066);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
