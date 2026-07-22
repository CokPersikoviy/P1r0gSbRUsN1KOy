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

/** Displays players discovered to be using FrogHelper. */
public class SocialScreen extends Screen {
    private static final int PANEL_WIDTH = 264;
    private static final int PANEL_PADDING = 14;
    private static final int HEADER_HEIGHT = 38;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 14;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int visibleRows;
    private int scroll;

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
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - panelHeight) / 2;
        clampScroll(count);
    }

    private void clampScroll(int count) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - visibleRows)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/SocialScreen/render")) {
            layout();
            Minecraft minecraft = Minecraft.getInstance();
            List<String> names = ModUserStorage.knownDisplayNames();

            graphics.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x66));
            HudBlur.beginFrame(graphics);
            HudSurface.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, WidgetChrome.FROST, HudSurface.nativeRenderer());
            graphics.drawString(minecraft.font, ModUserBadge.prefix(Component.literal("FrogHelper users")),
                    panelX + PANEL_PADDING, panelY + 10, WidgetTheme.TITLE);
            graphics.fill(panelX + PANEL_PADDING, panelY + HEADER_HEIGHT - 6,
                    panelX + PANEL_WIDTH - PANEL_PADDING, panelY + HEADER_HEIGHT - 5, WidgetTheme.ACCENT_LINE);

            if (names.isEmpty()) {
                graphics.drawString(minecraft.font, "No users discovered",
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
                int rowY = listTop + i * ROW_HEIGHT;
                graphics.drawString(minecraft.font, ModUserBadge.prefix(Component.literal(names.get(index))),
                        rowX, rowY + 2, WidgetTheme.TEXT_PRIMARY);
            }

            if (names.size() > visibleRows) {
                drawScrollbar(graphics, names.size(), listTop);
            }
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
