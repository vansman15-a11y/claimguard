package net.robmc.claimguard.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.claim.ClaimTier;
import net.robmc.claimguard.network.ClaimActionPacket;
import net.robmc.claimguard.network.ClaimGuardNetwork;

/**
 * The "Clan Beacon" menu shown when the owner right-clicks their core.
 * Buttons: show the border, open the upgrade table, or remove the claim.
 */
public class ClaimMenuScreen extends Screen {

    private static final int PANEL_W = 230;
    private static final int PANEL_H = 150;
    private static final int PANEL_BG = 0xD0100C1A;
    private static final int PANEL_BORDER = 0xFF57C97A;

    private final BlockPos corePos;
    private final ClaimTier tier;
    private final String claimName;

    public ClaimMenuScreen(BlockPos corePos, int tierOrdinal, String claimName) {
        super(Component.literal("Clan Beacon"));
        this.corePos = corePos;
        this.tier = ClaimTier.byIndex(tierOrdinal);
        this.claimName = claimName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int btnLeft = cx - PANEL_W / 2 + 15;
        int btnW = PANEL_W - 30;
        int y = top + 66;

        addRenderableWidget(Button.builder(Component.literal("Show border (30s)"), b -> {
            ClaimGuardNetwork.CHANNEL.sendToServer(new ClaimActionPacket(corePos, ClaimActionPacket.Action.SHOW_BORDER));
            onClose();
        }).bounds(btnLeft, y, btnW, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Upgrade"), b ->
                minecraft.setScreen(new ClaimUpgradeScreen(corePos, tier.ordinal(), claimName))
        ).bounds(btnLeft, y, btnW / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> confirmRemove())
                .bounds(btnLeft + btnW / 2 + 2, y, btnW / 2 - 2, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(btnLeft, y, btnW, 20).build());
    }

    private void confirmRemove() {
        BooleanConsumer onChoice = confirmed -> {
            if (confirmed) {
                ClaimGuardNetwork.CHANNEL.sendToServer(new ClaimActionPacket(corePos, ClaimActionPacket.Action.REMOVE));
                onClose();
            } else {
                minecraft.setScreen(this);
            }
        };
        minecraft.setScreen(new ConfirmScreen(
                onChoice,
                Component.literal("Remove this claim?"),
                Component.literal("This breaks the Claim Core and unprotects the area. The core drops so you can re-place it."),
                Component.literal("Remove"),
                CommonComponents.GUI_CANCEL
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_BG);
        g.renderOutline(left, top, PANEL_W, PANEL_H, PANEL_BORDER);

        g.drawCenteredString(this.font, "CLAN BEACON", cx, top + 10, 0xFFFFFF);
        g.drawCenteredString(this.font, claimName + "  •  Level " + (tier.ordinal() + 1), cx, top + 24, 0xA9C7D6);
        g.drawCenteredString(this.font, "Radius " + tier.getRadius() + "  -  " + tier.describeFootprint() + " footprint", cx, top + 40, 0xFFFFFF);
        g.drawCenteredString(this.font, "Protected bedrock to sky", cx, top + 50, 0x8FA0AA);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
