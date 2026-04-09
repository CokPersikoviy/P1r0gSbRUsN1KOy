package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

public class OutgoingChatQueueWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int EMPTY_WIDTH = 160;
    private static final int EMPTY_HEIGHT = 28;

    public OutgoingChatQueueWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatDispatchQueue.DebugSnapshot snapshot = ChatDispatchQueue.getDebugSnapshot();
        boolean hasContent = snapshot.size() > 0;

        String title = "Chat Queue: " + snapshot.size();
        String preview = snapshot.blockedRemainingMs() > 0
                ? "Blocked: " + formatBlockedSeconds(snapshot.blockedRemainingMs())
                : (hasContent ? snapshot.preview() : "Queue is empty");
        preview = trimPreview(mc, preview);

        int width = hasContent ? getUnscaledWidth(mc, title, preview) : EMPTY_WIDTH;
        int height = getUnscaledHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, hasContent ? WidgetTheme.WIDGET_PANEL_BG : WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, title, PADDING_X, PADDING_Y, WidgetTheme.TITLE);
        context.drawString(mc.font, preview, PADDING_X, PADDING_Y + mc.font.lineHeight + 2, hasContent ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        ChatDispatchQueue.DebugSnapshot snapshot = ChatDispatchQueue.getDebugSnapshot();
        if (snapshot.size() <= 0) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        String title = "Chat Queue: " + snapshot.size();
        String preview = snapshot.blockedRemainingMs() > 0
                ? formatBlockedSeconds(snapshot.blockedRemainingMs())
                : snapshot.preview();
        preview = trimPreview(mc, preview);
        return Math.round(getUnscaledWidth(mc, title, preview) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().outgoingChatQueue.active || Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    @Override
    public String getDisplayName() {
        return "Chat Queue";
    }

    private int getUnscaledWidth(Minecraft mc, String title, String preview) {
        int maxWidth = Math.max(mc.font.width(title), mc.font.width(preview));
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc) {
        return PADDING_Y * 2 + mc.font.lineHeight * 2 + 2;
    }

    private String trimPreview(Minecraft mc, String preview) {
        if (preview == null || preview.isBlank()) {
            return "Unknown";
        }

        String normalized = preview.replace('\n', ' ').trim();
        int maxWidth = 160;
        if (mc.font.width(normalized) <= maxWidth) {
            return normalized;
        }

        String ellipsis = "...";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            String candidate = builder.toString() + normalized.charAt(index) + ellipsis;
            if (mc.font.width(candidate) > maxWidth) {
                break;
            }
            builder.append(normalized.charAt(index));
        }

        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }

    private String formatBlockedSeconds(long blockedRemainingMs) {
        long seconds = Math.max(1L, (blockedRemainingMs + 999L) / 1000L);
        return "Blocked: " + seconds + "s";
    }
}

