package ru.wilyfox.client.moduser;

import ru.wilyfox.client.audio.UiSoundScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int PANEL_WIDTH = 330;
    private static final int BUTTON_WIDTH = 64;
    private static final int FOOTER_HEIGHT = 22;
    private static final int PANEL_PADDING = 14;
    private static final int HEADER_HEIGHT = 38;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 14;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int visibleRows;
    private int scroll;
    private String requestedName;
    private Component timerStatus = Component.empty();
    private java.util.concurrent.CompletableFuture<Component> pending;

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
        int availableRows = Math.max(1, (height - HEADER_HEIGHT - PANEL_PADDING - FOOTER_HEIGHT - 24) / ROW_HEIGHT);
        visibleRows = Math.max(1, Math.min(count, Math.min(MAX_VISIBLE_ROWS, availableRows)));
        panelHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PANEL_PADDING + FOOTER_HEIGHT;
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
                String name = names.get(index);
                var player = PresenceStore.player(name);
                int buttonX = panelX + PANEL_WIDTH - PANEL_PADDING - BUTTON_WIDTH;
                Component label = playerLabel(name);
                graphics.enableScissor(rowX, rowY, buttonX - 5, rowY + ROW_HEIGHT);
                graphics.text(minecraft.font, label, rowX, rowY + 2, WidgetTheme.TEXT_PRIMARY);
                graphics.disableScissor();
                boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                boolean waiting = pending != null && !pending.isDone();
                String unavailable = BackendSocialClient.timerUnavailable(player);
                boolean enabled = unavailable == null && !waiting;
                HudSurface.fillRounded(graphics, buttonX, rowY, BUTTON_WIDTH, ROW_HEIGHT - 2, 3,
                        hovered && enabled ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
                graphics.centeredText(minecraft.font, Component.translatable(waiting && name.equals(requestedName)
                        ? "social.froghelper.timers.loading" : "social.froghelper.timers.request"),
                        buttonX + BUTTON_WIDTH / 2, rowY + 2, enabled ? WidgetTheme.TITLE : WidgetTheme.TEXT_MUTED);
                if (hovered) graphics.setTooltipForNextFrame(minecraft.font, Component.translatable(unavailable != null ? unavailable
                        : waiting ? "social.froghelper.timers.busy" : "social.froghelper.timers.request_hint"), mouseX, mouseY);
                else if (mouseX >= rowX && mouseX < buttonX - 5 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    Component tooltip = player != null && player.protocolBadge()
                            ? Component.translatable("social.froghelper.timers.protocol_hint", player.timerCount()) : label;
                    graphics.setTooltipForNextFrame(minecraft.font, tooltip, mouseX, mouseY);
                }
            }

            if (names.size() > visibleRows) {
                drawScrollbar(graphics, names.size(), listTop);
            }
            if (pending != null && pending.isDone()) {
                timerStatus = pending.getNow(Component.translatable("social.froghelper.timers.failed"));
                pending = null;
            }
            int footerY = listTop + visibleRows * ROW_HEIGHT + 5;
            var lines = minecraft.font.split(timerStatus, PANEL_WIDTH - PANEL_PADDING * 2);
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                graphics.text(minecraft.font, lines.get(i), rowX, footerY + i * minecraft.font.lineHeight, WidgetTheme.TEXT_SOFT);
        }
    }

    static Component playerLabel(String name) {
        var player = PresenceStore.player(name);
        Component protocol = player != null && player.protocolBadge()
                ? Component.literal("P ").withStyle(net.minecraft.ChatFormatting.AQUA) : Component.empty();
        String count = player == null || player.timerCount() == null ? "—" : player.timerCount().toString();
        return ModUserBadge.prefix(Component.empty().append(protocol).append(Component.literal(name + " [" + count + "]")));
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        layout();
        int buttonX = panelX + PANEL_WIDTH - PANEL_PADDING - BUTTON_WIDTH;
        int row = (int) ((event.y() - panelY - HEADER_HEIGHT) / ROW_HEIGHT);
        if (event.x() < buttonX || event.x() >= buttonX + BUTTON_WIDTH || event.y() < panelY + HEADER_HEIGHT
                || row < 0 || row >= visibleRows || event.y() >= panelY + HEADER_HEIGHT + row * ROW_HEIGHT + ROW_HEIGHT - 2) return false;
        var names = PresenceStore.knownDisplayNames();
        if (scroll + row >= names.size() || pending != null && !pending.isDone()) return true;
        String name = names.get(scroll + row);
        String unavailable = BackendSocialClient.timerUnavailable(PresenceStore.player(name));
        if (unavailable != null) { timerStatus = Component.translatable(unavailable); return true; }
        requestedName = name;
        timerStatus = Component.translatable("social.froghelper.timers.loading");
        ru.wilyfox.client.audio.UiSounds.click();
        pending = BackendSocialClient.requestTimers(name);
        return true;
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
