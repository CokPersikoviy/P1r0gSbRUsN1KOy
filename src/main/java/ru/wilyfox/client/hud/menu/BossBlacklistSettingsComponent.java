package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.boss.BossBlacklist;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwBossType;

import java.util.List;

public final class BossBlacklistSettingsComponent extends SettingsComponent {
    private static final int HEADER_HEIGHT = 24;
    private static final int ACTION_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int SECTION_GAP = 4;

    private boolean expanded;

    public BossBlacklistSettingsComponent() {
        super(0, 0, 0, 0, "Boss blacklist");
    }

    @Override
    public int getPreferredHeight() {
        if (!expanded) {
            return HEADER_HEIGHT;
        }
        int rows = bossTypes().size();
        return HEADER_HEIGHT + SECTION_GAP + ACTION_HEIGHT + ROW_GAP + rows * (ROW_HEIGHT + ROW_GAP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<DwBossType> types = bossTypes();
        long hiddenCount = types.stream().filter(BossBlacklist::isBlocked).count();

        boolean headerHovered = contains(mouseX, mouseY, x, y, width, HEADER_HEIGHT);
        HudSurface.fillRounded(
                graphics,
                x,
                y,
                width,
                HEADER_HEIGHT,
                4,
                headerHovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT
        );
        if (expanded) {
            graphics.fill(x + 4, y, x + width - 4, y + 1, WidgetTheme.ACCENT_LINE);
        }

        int textY = y + (HEADER_HEIGHT - font.lineHeight) / 2;
        graphics.drawString(font, label, x + 8, textY, headerHovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);

        String summary = hiddenCount + " hidden";
        int expandX = x + width - 14;
        int summaryX = expandX - 8 - font.width(summary);
        graphics.drawString(font, summary, summaryX, textY, hiddenCount > 0 ? WidgetTheme.STATUS_ERROR : WidgetTheme.TEXT_MUTED);
        graphics.drawCenteredString(font, expanded ? "-" : "+", expandX, textY, headerHovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY);

        if (!expanded) {
            return;
        }

        int rowY = y + HEADER_HEIGHT + SECTION_GAP;
        boolean clearHovered = contains(mouseX, mouseY, x, rowY, width, ACTION_HEIGHT);
        HudSurface.fillRounded(
                graphics,
                x,
                rowY,
                width,
                ACTION_HEIGHT,
                3,
                clearHovered ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG
        );
        graphics.drawCenteredString(
                font,
                "Show all bosses",
                x + width / 2,
                rowY + (ACTION_HEIGHT - font.lineHeight) / 2,
                hiddenCount > 0 ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_MUTED
        );

        rowY += ACTION_HEIGHT + ROW_GAP;
        for (DwBossType type : types) {
            renderBossRow(graphics, font, type, rowY, mouseX, mouseY);
            rowY += ROW_HEIGHT + ROW_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        if (contains(mouseX, mouseY, x, y, width, HEADER_HEIGHT)) {
            expanded = !expanded;
            return true;
        }
        if (!expanded) {
            return false;
        }

        int rowY = y + HEADER_HEIGHT + SECTION_GAP;
        if (contains(mouseX, mouseY, x, rowY, width, ACTION_HEIGHT)) {
            BossBlacklist.clear();
            return true;
        }

        rowY += ACTION_HEIGHT + ROW_GAP;
        for (DwBossType type : bossTypes()) {
            if (contains(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
                BossBlacklist.toggle(type);
                return true;
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        return false;
    }

    @Override
    public String getTooltip(int mouseX, int mouseY) {
        if (!expanded) {
            return null;
        }

        int rowY = y + HEADER_HEIGHT + SECTION_GAP + ACTION_HEIGHT + ROW_GAP;
        for (DwBossType type : bossTypes()) {
            if (contains(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
                return type.id();
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }
        return null;
    }

    private void renderBossRow(
            GuiGraphics graphics,
            Font font,
            DwBossType type,
            int rowY,
            int mouseX,
            int mouseY
    ) {
        boolean hidden = BossBlacklist.isBlocked(type);
        boolean hovered = contains(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
        int background = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG;
        HudSurface.fillRounded(graphics, x, rowY, width, ROW_HEIGHT, 3, background);

        int checkSize = 10;
        int checkX = x + 7;
        int checkY = rowY + (ROW_HEIGHT - checkSize) / 2;
        int checkColor = hidden ? WidgetTheme.HARD_ACCENT : WidgetTheme.OUTLINE_SOFT;
        graphics.fill(checkX, checkY, checkX + checkSize, checkY + 1, checkColor);
        graphics.fill(checkX, checkY + checkSize - 1, checkX + checkSize, checkY + checkSize, checkColor);
        graphics.fill(checkX, checkY, checkX + 1, checkY + checkSize, checkColor);
        graphics.fill(checkX + checkSize - 1, checkY, checkX + checkSize, checkY + checkSize, checkColor);
        if (hidden) {
            graphics.drawCenteredString(font, "x", checkX + checkSize / 2, checkY + 1, WidgetTheme.HARD_ACCENT);
        }

        String level = type.level() > 0 ? "[" + type.level() + "]" : "[?]";
        int levelX = x + width - 8 - font.width(level);
        int nameX = checkX + checkSize + 7;
        int availableNameWidth = Math.max(0, levelX - nameX - 8);
        String name = type.name() == null || type.name().isBlank() ? type.id() : type.name();
        if (font.width(name) > availableNameWidth) {
            String suffix = "...";
            name = font.plainSubstrByWidth(name, Math.max(0, availableNameWidth - font.width(suffix))) + suffix;
        }

        int baseline = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
        int textColor = hidden ? WidgetTheme.STATUS_ERROR : (hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);
        graphics.drawString(font, name, nameX, baseline, textColor);
        graphics.drawString(font, level, levelX, baseline, hidden ? WidgetTheme.HARD_ACCENT : WidgetTheme.TEXT_SECONDARY);
    }

    private static List<DwBossType> bossTypes() {
        return DiamondWorldProtocolClient.getKnownBossTypes();
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
