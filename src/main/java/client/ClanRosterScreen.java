package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.clan.ClanPermissions;
import net.robmc.claimguard.clan.ClanRank;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanMemberActionPacket;
import net.robmc.claimguard.network.OpenClanRosterPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The clan roster: header, All / Online filter, one row per member (online dot,
 * name, rank), and a right-click context menu with Whisper plus whatever
 * management actions the viewer's rank allows.
 */
public class ClanRosterScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 232;
    private static final int PANEL_BG = 0xD0100C1A;
    private static final int PANEL_BORDER = 0xFF57C97A;
    private static final int ROW_H = 15;
    private static final int LIST_TOP_OFFSET = 54;

    private final String clanName;
    private final String clanTag;
    private final String motd;
    private final ClanRank viewerRank;
    private final UUID viewerId;
    private final List<OpenClanRosterPacket.MemberRow> allMembers;

    private boolean onlineOnly = false;

    // right-click context menu state
    private UUID ctxTarget;
    private int ctxX;
    private int ctxY;
    private List<CtxItem> ctxItems;

    private record CtxItem(String label, Runnable action) {
    }

    public ClanRosterScreen(OpenClanRosterPacket data) {
        super(Component.literal(data.clanName()));
        this.clanName = data.clanName();
        this.clanTag = data.clanTag();
        this.motd = data.motd();
        this.viewerRank = ClanRank.byIndex(data.viewerRankOrdinal());
        this.allMembers = data.members();
        this.viewerId = net.minecraft.client.Minecraft.getInstance().player != null
                ? net.minecraft.client.Minecraft.getInstance().player.getUUID()
                : new UUID(0, 0);
    }

    private List<OpenClanRosterPacket.MemberRow> visibleMembers() {
        if (!onlineOnly) {
            return allMembers;
        }
        List<OpenClanRosterPacket.MemberRow> out = new ArrayList<>();
        for (OpenClanRosterPacket.MemberRow row : allMembers) {
            if (row.online()) {
                out.add(row);
            }
        }
        return out;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;

        addRenderableWidget(Button.builder(Component.literal("All"), b -> {
            onlineOnly = false;
            ctxItems = null;
        }).bounds(cx - 84, top + 32, 80, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Online"), b -> {
            onlineOnly = true;
            ctxItems = null;
        }).bounds(cx + 4, top + 32, 80, 16).build());

        int barY = top + PANEL_H - 46;
        int barLeft = cx - PANEL_W / 2 + 14;
        int barW = PANEL_W - 28;
        int third = barW / 3 - 4;
        addRenderableWidget(Button.builder(Component.literal("Invite"), b ->
                minecraft.setScreen(new ClanInviteScreen(clanName))
        ).bounds(barLeft, barY, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("MOTD"), b ->
                minecraft.setScreen(new ClanMotdScreen(clanName, motd, canEditMotd()))
        ).bounds(barLeft + third + 6, barY, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Ban list"), b ->
                ClaimGuardNetwork.CHANNEL.sendToServer(new net.robmc.claimguard.network.OpenClanBanListRequestPacket())
        ).bounds(barLeft + 2 * (third + 6), barY, third, 18).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(cx - 60, top + PANEL_H - 24, 120, 20).build());
    }

    private boolean canEditMotd() {
        return viewerRank == ClanRank.LEADER || viewerRank == ClanRank.OFFICER;
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
                ctxItems = null; // click outside dismisses
            }
            return true;
        }

        if (button == 1) {
            OpenClanRosterPacket.MemberRow row = rowAt(my);
            if (row != null && !row.id().equals(viewerId)) {
                openContextMenu(row, (int) mx, (int) my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private OpenClanRosterPacket.MemberRow rowAt(double my) {
        int top = (this.height - PANEL_H) / 2 + LIST_TOP_OFFSET;
        List<OpenClanRosterPacket.MemberRow> visible = visibleMembers();
        int i = (int) ((my - top) / ROW_H);
        if (i >= 0 && i < visible.size()) {
            return visible.get(i);
        }
        return null;
    }

    private void openContextMenu(OpenClanRosterPacket.MemberRow row, int x, int y) {
        ClanRank targetRank = ClanRank.byIndex(row.rankOrdinal());
        List<CtxItem> items = new ArrayList<>();

        items.add(new CtxItem("Whisper", () -> {
            onClose();
            minecraft.setScreen(new ChatScreen("/tell " + row.name() + " "));
        }));
        if (ClanPermissions.canPromote(viewerRank, targetRank)) {
            items.add(new CtxItem("Promote", () -> sendAction(row.id(), ClanMemberActionPacket.Action.PROMOTE)));
        }
        if (ClanPermissions.canDemote(viewerRank, targetRank)) {
            items.add(new CtxItem("Demote", () -> sendAction(row.id(), ClanMemberActionPacket.Action.DEMOTE)));
        }
        if (ClanPermissions.canKick(viewerRank, targetRank)) {
            items.add(new CtxItem("Kick", () -> sendAction(row.id(), ClanMemberActionPacket.Action.KICK)));
        }
        if (ClanPermissions.canBan(viewerRank, targetRank)) {
            items.add(new CtxItem("Ban", () -> sendAction(row.id(), ClanMemberActionPacket.Action.BAN)));
        }

        this.ctxTarget = row.id();
        this.ctxItems = items;
        this.ctxX = x;
        this.ctxY = y;
    }

    private void sendAction(UUID target, ClanMemberActionPacket.Action action) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new ClanMemberActionPacket(target, action));
        // The server re-sends the roster; nothing else to do here.
    }

    private static final int CTX_W = 92;

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
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_BG);
        g.renderOutline(left, top, PANEL_W, PANEL_H, PANEL_BORDER);

        g.drawCenteredString(this.font, clanName.toUpperCase() + " CLAN", cx, top + 8, 0xFFFFFF);
        g.drawCenteredString(this.font, allMembers.size() + " Members", cx, top + 20, 0xA9C7D6);

        int rowLeft = left + 16;
        int rowRight = left + PANEL_W - 16;
        int y = top + LIST_TOP_OFFSET;
        for (OpenClanRosterPacket.MemberRow row : visibleMembers()) {
            int dot = row.online() ? 0xFF55DD55 : 0xFF5A5A5A;
            g.fill(rowLeft, y + 3, rowLeft + 5, y + 8, dot);

            int nameColor = row.id().equals(viewerId) ? 0xFFF0C24B : 0xFFFFFFFF;
            g.drawString(this.font, row.name(), rowLeft + 12, y + 2, nameColor, false);

            String rank = ClanRank.byIndex(row.rankOrdinal()).displayName();
            int rankColor = rankColor(row.rankOrdinal());
            g.drawString(this.font, rank, rowRight - this.font.width(rank), y + 2, rankColor, false);
            y += ROW_H;
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (ctxItems != null) {
            renderContextMenu(g, mouseX, mouseY);
        }
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
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

    private static int rankColor(int ordinal) {
        return switch (ClanRank.byIndex(ordinal)) {
            case LEADER -> 0xFFF0C24B;
            case OFFICER -> 0xFF5AD1E0;
            case MEMBER -> 0xFFDDDDDD;
            case RECRUIT -> 0xFF8FA0AA;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
