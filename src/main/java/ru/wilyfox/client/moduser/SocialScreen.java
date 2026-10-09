package ru.wilyfox.client.moduser;

import ru.wilyfox.client.audio.UiSoundScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.widget.HudBlur;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.List;

/** Displays players currently online with FrogHelper in this game region. */
public class SocialScreen extends UiSoundScreen {
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
        super(Component.translatable("screen.froghelper.online"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void init() {
        super.init();
        layout();
    }

    private void layout() {
        int count = PresenceStore.knownCount();
        int availableRows = Math.max(1, (height - HEADER_HEIGHT - PANEL_PADDING - 24) / ROW_HEIGHT);
        visibleRows = Math.max(1, Math.min(count, Math.min(MAX_VISIBLE_ROWS, availableRows)));
        panelHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PANEL_PADDING;
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - panelHeight) / 2;
        clampScroll(count);
    }

    private void clampScroll(int count) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - visibleRows)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ru.wilyfox.client.audio.UiSounds.update();
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("ui/SocialScreen/render")) {
            layout();
            Minecraft minecraft = Minecraft.getInstance();
            List<String> names = PresenceStore.knownDisplayNames();

            graphics.fill(0, 0, width, height, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0x66));
            HudBlur.beginFrame(graphics);
            HudSurface.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, WidgetChrome.FROST, HudSurface.nativeRenderer());
            graphics.text(minecraft.font, ModUserBadge.prefix(Component.translatable("screen.froghelper.online_count", names.size())),
                    panelX + PANEL_PADDING, panelY + 10, WidgetTheme.TITLE);
            graphics.fill(panelX + PANEL_PADDING, panelY + HEADER_HEIGHT - 6,
                    panelX + PANEL_WIDTH - PANEL_PADDING, panelY + HEADER_HEIGHT - 5, WidgetTheme.ACCENT_LINE);

            if (names.isEmpty()) {
                graphics.text(minecraft.font, Component.translatable(BackendSocialClient.statusText()),
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
                graphics.text(minecraft.font, ModUserBadge.prefix(Component.literal(names.get(index))),
                        rowX, rowY + 2, WidgetTheme.TEXT_PRIMARY);
            }

            if (names.size() > visibleRows) {
                drawScrollbar(graphics, names.size(), listTop);
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int count, int listTop) {
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
        int previous = scroll;
        scroll -= (int) Math.signum(scrollY);
        clampScroll(PresenceStore.knownCount());
        if (previous != scroll) ru.wilyfox.client.audio.UiSounds.scroll();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (KeyBinds.SOCIAL.matches(event) || event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
