package ru.wilyfox.client.moduser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.List;

/**
 * The "Social" screen (default keybind C): the roster of players discovered to run FrogHelper. As group
 * LEADER, left-click a row to invite/remove that player from your keepalive party (max 4); the roster syncs
 * to all members. A "Выйти/Распустить" control leaves or disbands. Online = a fresh keepalive received (the
 * DW tab list spans many subservers and is unreliable). Styled to match the mod's frosted "Frost" panels.
 */
public class SocialScreen extends Screen {
    private static final int PANEL_WIDTH = 264;
    private static final int PANEL_PADDING = 14;
    private static final int HEADER_HEIGHT = 52;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 14;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int visibleRows;
    private int listWidth;
    private int scroll;

    private boolean leaveVisible;
    private int leaveX0;
    private int leaveX1;
    private int leaveY0;
    private int leaveY1;

    public SocialScreen() {
        super(Component.literal("Social"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground() {
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        int count = ModUserStorage.knownCount();
        visibleRows = Math.max(1, Math.min(count, MAX_VISIBLE_ROWS));
        panelHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PANEL_PADDING;
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;
        listWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        clampScroll(count);
    }

    private void clampScroll(int count) {
        int maxScroll = Math.max(0, count - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/SocialScreen/render")) {
            layout();
            Minecraft minecraft = Minecraft.getInstance();
            List<String> names = ModUserStorage.knownDisplayNames();

            graphics.fill(0, 0, this.width, this.height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x66));
            HudBlur.beginFrame(graphics);
            HudSurface.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, WidgetChrome.FROST, HudSurface.nativeRenderer());

            renderHeader(graphics, minecraft, mouseX, mouseY);
            graphics.fill(panelX + PANEL_PADDING, panelY + HEADER_HEIGHT - 6,
                    panelX + PANEL_WIDTH - PANEL_PADDING, panelY + HEADER_HEIGHT - 5, WidgetTheme.ACCENT_LINE);

            if (names.isEmpty()) {
                graphics.drawString(minecraft.font, "Игроки с модом ещё не найдены",
                        panelX + PANEL_PADDING, panelY + HEADER_HEIGHT + 4, WidgetTheme.TEXT_MUTED);
                return;
            }

            int rowX = panelX + PANEL_PADDING;
            int listTop = panelY + HEADER_HEIGHT;
            for (int i = 0; i < visibleRows; i++) {
                int index = scroll + i;
                if (index >= names.size()) {
                    break;
                }
                renderRow(graphics, minecraft, names, index, rowX, listTop + i * ROW_HEIGHT, mouseX, mouseY);
            }

            if (names.size() > visibleRows) {
                drawScrollbar(graphics, names.size(), listTop);
            }
        }
    }

    private void renderHeader(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        Component title = ModUserBadge.prefix(Component.literal("Social"));
        graphics.drawString(minecraft.font, title, panelX + PANEL_PADDING, panelY + 10, WidgetTheme.TITLE);

        String status;
        String hint;
        switch (SocialGroup.role()) {
            case LEADER -> {
                status = "Лидер · " + SocialGroup.roster().size() + "/" + SocialGroup.MAX;
                hint = "ЛКМ по игроку — пригласить / убрать";
            }
            case MEMBER -> {
                status = "В группе: " + SocialGroup.leader();
                hint = "чтобы сменить группу — жми справа";
            }
            default -> {
                status = "Группы нет";
                hint = "ЛКМ по игроку — создать группу";
            }
        }
        graphics.drawString(minecraft.font, status, panelX + PANEL_PADDING, panelY + 24, WidgetTheme.TEXT_SECONDARY);
        graphics.drawString(minecraft.font, hint, panelX + PANEL_PADDING, panelY + 36, WidgetTheme.TEXT_MUTED);

        // Leave / disband control, top-right.
        leaveVisible = SocialGroup.isInGroup();
        if (leaveVisible) {
            String label = SocialGroup.isLeader() ? "Распустить" : "Выйти";
            int labelWidth = minecraft.font.width(label);
            int bx = panelX + PANEL_WIDTH - PANEL_PADDING - labelWidth;
            int by = panelY + 10;
            leaveX0 = bx - 4;
            leaveX1 = bx + labelWidth + 2;
            leaveY0 = by - 3;
            leaveY1 = by + 11;
            boolean hovered = mouseX >= leaveX0 && mouseX <= leaveX1 && mouseY >= leaveY0 && mouseY <= leaveY1;
            graphics.drawString(minecraft.font, label, bx, by, hovered ? WidgetTheme.STATUS_ERROR : WidgetTheme.TEXT_SECONDARY);
        }
    }

    private void renderRow(GuiGraphics graphics, Minecraft minecraft, List<String> names, int index, int rowX, int rowY, int mouseX, int mouseY) {
        String name = names.get(index);
        boolean inGroup = SocialGroup.inRoster(name);
        ModUserProtocol.Status status = ModUserProtocol.getStatus(name);
        boolean online = status != null;

        boolean hovered = mouseX >= rowX - 6 && mouseX <= panelX + PANEL_WIDTH - PANEL_PADDING
                && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2;
        if (hovered) {
            HudSurface.fillRounded(graphics, rowX - 6, rowY - 2, listWidth + 12, ROW_HEIGHT - 1, 3, WidgetTheme.PANEL_BG_SOFT);
        }
        if (inGroup) {
            graphics.fill(rowX - 8, rowY - 1, rowX - 6, rowY + ROW_HEIGHT - 3, WidgetTheme.ACCENT_LINE);
        }

        int dotColor = online ? 0xFF6FCF6F : WidgetTheme.withAlpha(WidgetTheme.TEXT_MUTED, 0x88);
        graphics.fill(rowX, rowY + 4, rowX + 3, rowY + 7, dotColor);

        int nameColor = inGroup ? WidgetTheme.TITLE : (online ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_MUTED);
        graphics.drawString(minecraft.font, ModUserBadge.prefix(Component.literal(name)), rowX + 9, rowY + 2, nameColor);

        if (status != null) {
            String info = "ур " + status.level() + "  ♥" + status.hp() + "/" + status.maxHp();
            int rightEdge = panelX + PANEL_WIDTH - PANEL_PADDING - (names.size() > visibleRows ? 8 : 0);
            graphics.drawString(minecraft.font, info, rightEdge - minecraft.font.width(info), rowY + 2, WidgetTheme.TEXT_SECONDARY);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int count, int listTop) {
        int trackX = panelX + PANEL_WIDTH - 5;
        int trackHeight = visibleRows * ROW_HEIGHT;
        graphics.fill(trackX, listTop, trackX + 2, listTop + trackHeight, WidgetTheme.BAR_BG);

        int thumbHeight = Math.max(10, Math.round((float) visibleRows / count * trackHeight));
        int maxScroll = Math.max(1, count - visibleRows);
        int thumbY = listTop + Math.round((float) scroll / maxScroll * (trackHeight - thumbHeight));
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, WidgetTheme.ACCENT_LINE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (leaveVisible && mouseX >= leaveX0 && mouseX <= leaveX1 && mouseY >= leaveY0 && mouseY <= leaveY1) {
                ModUserProtocol.leaveGroup();
                return true;
            }
            List<String> names = ModUserStorage.knownDisplayNames();
            int rowX = panelX + PANEL_PADDING;
            int listTop = panelY + HEADER_HEIGHT;
            for (int i = 0; i < visibleRows; i++) {
                int index = scroll + i;
                if (index >= names.size()) {
                    break;
                }
                int rowY = listTop + i * ROW_HEIGHT;
                if (mouseX >= rowX - 8 && mouseX <= panelX + PANEL_WIDTH - PANEL_PADDING
                        && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2) {
                    String name = names.get(index);
                    if (SocialGroup.isLeader() && SocialGroup.inRoster(name)) {
                        ModUserProtocol.kick(name);
                    } else {
                        ModUserProtocol.invite(name);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        clampScroll(ModUserStorage.knownCount());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_C || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
