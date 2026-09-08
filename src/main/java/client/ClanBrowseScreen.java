package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.clan.ClanRank;
import net.robmc.claimguard.clan.ClanRelation;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.OpenClanBrowsePacket;
import net.robmc.claimguard.network.SetClanRelationPacket;
import net.robmc.claimguard.network.StartSiegePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The clan directory. Each row shows a clan, its raid window and whether it's
 * open / under siege, and (for the viewer's clan) the relation. Right-click a
 * clan (leader/officer) to set Ally / Enemy / Neutral, and Siege if it's an
 * enemy whose window is open.
 */
public class ClanBrowseScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 220;
    private static final int ROW_H = 16;
    private static final int LIST_TOP = 34;
    private static final int CTX_W = 110;

    private final boolean canManage;
    private final List<OpenClanBrowsePacket.ClanRow> clans;

    private int ctxX;
    private int ctxY;
    private List<CtxItem> ctxItems;

    private record CtxItem(String label, Runnable action) {
    }

    public ClanBrowseScreen(OpenClanBrowsePacket data) {
        super(Component.literal("Clans"));
        ClanRank rank = ClanRank.byIndex(data.viewerRankOrdinal());
        this.canManage = rank == ClanRank.LEADER || rank == ClanRank.OFFICER;
        this.clans = data.clans();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(cx - 60, top + PANEL_H - 24, 120, 20).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ctxItems != null) {
            int idx = ctxItemAt(mx, my);
            if (idx >= 0) {
                Runnable action = ctxItems.get(idx).action();
                ctxItems = null;
                action.run();
            } else {
                ctxItems = null;
            }
            return true;
        }
        if (button == 1 && canManage) {
            OpenClanBrowsePacket.ClanRow row = rowAt(my);
            if (row != null) {
                openContextMenu(row, (int) mx, (int) my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private OpenClanBrowsePacket.ClanRow rowAt(double my) {
        int top = (this.height - PANEL_H) / 2 + LIST_TOP;
        int i = (int) ((my - top) / ROW_H);
        return (i >= 0 && i < clans.size()) ? clans.get(i) : null;
    }

    private void openContextMenu(OpenClanBrowsePacket.ClanRow row, int x, int y) {
        List<CtxItem> items = new ArrayList<>();
        ClanRelation rel = ClanRelation.byIndexOrNull(row.relationOrdinal());

        if (rel != ClanRelation.ALLY) {
            items.add(new CtxItem("Set Ally", () -> setRelation(row.clanId(), ClanRelation.ALLY.ordinal())));
        }
        if (rel != ClanRelation.ENEMY) {
            items.add(new CtxItem("Set Enemy", () -> setRelation(row.clanId(), ClanRelation.ENEMY.ordinal())));
        }
        if (rel != null) {
            items.add(new CtxItem("Set Neutral", () -> setRelation(row.clanId(), -1)));
        }
        if (rel == ClanRelation.ENEMY) {
            items.add(new CtxItem("Siege", () ->
                    ClaimGuardNetwork.CHANNEL.sendToServer(new StartSiegePacket(row.clanId()))));
        }

        this.ctxItems = items;
        this.ctxX = x;
        this.ctxY = y;
    }

    private void setRelation(UUID clanId, int relOrdinal) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new SetClanRelationPacket(clanId, relOrdinal));
        // server re-sends the browse packet
    }

    private int ctxItemAt(double mx, double my) {
        if (ctxItems == null || mx < ctxX || mx > ctxX + CTX_W) {
            return -1;
        }
        int i = (int) ((my - ctxY) / 12);
        return (i >= 0 && i < ctxItems.size()) ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);
        g.drawCenteredString(this.font, "CLANS", cx, top + 8, 0xFFFFFF);
        if (canManage) {
            g.drawCenteredString(this.font, "right-click a clan to ally / enemy / siege", cx, top + 20, 0x8FA0AA);
        }

        int y = top + LIST_TOP;
        for (OpenClanBrowsePacket.ClanRow row : clans) {
            ClanRelation rel = ClanRelation.byIndexOrNull(row.relationOrdinal());
            int nameColor = rel == ClanRelation.ALLY ? 0xFF55DD55
                    : rel == ClanRelation.ENEMY ? 0xFFDD5555 : 0xFFFFFFFF;
            g.drawString(this.font, row.name() + " [" + row.tag() + "]  x" + row.memberCount(),
                    left + 14, y + 2, nameColor, false);

            String status;
            int statusColor;
            if (row.underSiege()) {
                status = "UNDER SIEGE";
                statusColor = 0xFFFF6666;
            } else if (row.windowOpen()) {
                status = "raidable now (" + row.raidWindow() + ")";
                statusColor = 0xFFFFC24B;
            } else {
                status = "window " + row.raidWindow();
                statusColor = 0xFF8FA0AA;
            }
            g.drawString(this.font, status, left + PANEL_W - 14 - this.font.width(status), y + 2, statusColor, false);
            y += ROW_H;
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (ctxItems != null) {
            int h = ctxItems.size() * 12 + 4;
            g.fill(ctxX, ctxY, ctxX + CTX_W, ctxY + h, 0xF0161222);
            g.renderOutline(ctxX, ctxY, CTX_W, h, 0xFF57C97A);
            for (int i = 0; i < ctxItems.size(); i++) {
                int iy = ctxY + 2 + i * 12;
                boolean hover = mouseX >= ctxX && mouseX <= ctxX + CTX_W && mouseY >= iy && mouseY < iy + 12;
                if (hover) {
                    g.fill(ctxX + 1, iy, ctxX + CTX_W - 1, iy + 12, 0x4057C97A);
                }
                g.drawString(this.font, ctxItems.get(i).label(), ctxX + 6, iy + 2, 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
