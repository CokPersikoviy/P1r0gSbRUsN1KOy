package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpNotification;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSeverity;

import java.util.List;

public final class PopUpsWidget extends AbstractWidget {
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 6;
    private static final int ROW_GAP = 5;
    private static final int BOX_GAP = 6;
    private static final int MIN_WIDTH = 170;
    private static final int PREVIEW_WIDTH = 210;
    private static final int SLIDE_DISTANCE = 18;
    private static final int EMPTY_WIDTH = 170;
    private static final int EMPTY_HEIGHT = 30;

    public PopUpsWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (isEditorPreview() && !hasActiveNotifications()) {
            renderPlaceholder(context, mc);
            return;
        }

        List<PopUpNotification> notifications = getNotificationsForRender();
        int stackWidth = getStackWidth(mc, notifications);
        int boxHeight = getNotificationHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        int y = 0;
        for (int index = 0; index < notifications.size(); index++) {
            PopUpNotification notification = notifications.get(index);
            int boxWidth = getNotificationWidth(mc, notification);
            float alpha = getNotificationAlpha(notification, System.currentTimeMillis());
            int offsetX = Math.round((1.0f - alpha) * SLIDE_DISTANCE);
            int boxX = stackWidth - boxWidth - offsetX;

            renderNotification(context, mc, notification, boxX, y, boxWidth, boxHeight, alpha);
            y += boxHeight + BOX_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        if (isEditorPreview() && !hasActiveNotifications()) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        Minecraft mc = Minecraft.getInstance();
        return Math.round(getStackWidth(mc, getNotificationsForMeasure()) * getScale());
    }

    @Override
    public int getHeight() {
        if (isEditorPreview() && !hasActiveNotifications()) {
            return Math.round(EMPTY_HEIGHT * getScale());
        }

        List<PopUpNotification> notifications = getNotificationsForMeasure();
        int count = Math.max(1, notifications.size());
        int unscaledHeight = count * getNotificationHeight(Minecraft.getInstance()) + (count - 1) * BOX_GAP;
        return Math.round(unscaledHeight * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().popUps.active || Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    @Override
    public String getDisplayName() {
        return "Pop-Ups";
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, "Pop-Ups", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active pop-up's", PADDING_X, 16, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private void renderNotification(
            GuiGraphics context,
            Minecraft mc,
            PopUpNotification notification,
            int x,
            int y,
            int width,
            int height,
            float alpha
    ) {
        int panelColor = withScaledAlpha(WidgetTheme.WIDGET_PANEL_BG, alpha);
        int bodyColor = withScaledAlpha(WidgetTheme.TEXT_PRIMARY, alpha);
        int titleColor = withScaledAlpha(getTitleColor(notification.severity()), alpha);
        int accentColor = withScaledAlpha(getAccentColor(notification.severity()), alpha);
        int borderColor = withScaledAlpha(WidgetTheme.WIDGET_ACCENT_LINE, alpha * 0.35f);
        int glowColor = withScaledAlpha(WidgetTheme.WIDGET_PANEL_BG_SOFT, alpha);

        context.fill(x, y, x + width, y + height, panelColor);
        context.fill(x, y, x + width, y + 1, accentColor);
        context.fill(x, y, x + 1, y + height, borderColor);
        context.fill(x + 1, y + height - 1, x + width, y + height, glowColor);

        int textX = x + PADDING_X;
        int titleY = y + PADDING_Y;
        int messageY = titleY + mc.font.lineHeight + ROW_GAP / 2;

        context.drawString(mc.font, notification.title(), textX, titleY, titleColor);
        context.drawString(mc.font, trimToWidth(mc, notification.message(), width - PADDING_X * 2), textX, messageY, bodyColor);
    }

    private List<PopUpNotification> getNotificationsForRender() {
        return PopUpManager.getInstance().getVisibleNotifications(ConfigManager.get().popUps.maxVisible);
    }

    private List<PopUpNotification> getNotificationsForMeasure() {
        return PopUpManager.getInstance().getVisibleNotifications(ConfigManager.get().popUps.maxVisible);
    }

    private int getStackWidth(Minecraft mc, List<PopUpNotification> notifications) {
        int widestBox = MIN_WIDTH;
        for (PopUpNotification notification : notifications) {
            widestBox = Math.max(widestBox, getNotificationWidth(mc, notification));
        }
        return widestBox + SLIDE_DISTANCE;
    }

    private int getNotificationWidth(Minecraft mc, PopUpNotification notification) {
        int titleWidth = mc.font.width(notification.title());
        int messageWidth = mc.font.width(trimToWidth(mc, notification.message(), PREVIEW_WIDTH));
        return Math.max(MIN_WIDTH, Math.max(titleWidth, messageWidth) + PADDING_X * 2);
    }

    private int getNotificationHeight(Minecraft mc) {
        return PADDING_Y * 2 + mc.font.lineHeight * 2 + ROW_GAP / 2;
    }

    private float getNotificationAlpha(PopUpNotification notification, long now) {
        long age = Math.max(0L, now - notification.createdAtMs());
        int fadeIn = Math.max(1, notification.fadeInMs());
        int holdStart = fadeIn;
        int holdEnd = holdStart + Math.max(0, notification.holdMs());
        int fadeOutEnd = holdEnd + Math.max(1, notification.fadeOutMs());

        if (age < fadeIn) {
            return clamp01(age / (float) fadeIn);
        }
        if (age < holdEnd) {
            return 1.0f;
        }
        if (age < fadeOutEnd) {
            return clamp01(1.0f - ((age - holdEnd) / (float) Math.max(1, notification.fadeOutMs())));
        }
        return 0.0f;
    }

    private boolean hasActiveNotifications() {
        return !PopUpManager.getInstance().getVisibleNotifications(ConfigManager.get().popUps.maxVisible).isEmpty();
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private String trimToWidth(Minecraft mc, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
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

    private int withScaledAlpha(int color, float alphaMultiplier) {
        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * clamp01(alphaMultiplier))));
        return (scaledAlpha << 24) | rgb;
    }

    private int getAccentColor(PopUpSeverity severity) {
        return switch (severity) {
            case SUCCESS -> WidgetTheme.STATUS_SUCCESS;
            case WARNING -> WidgetTheme.STATUS_WARNING;
            case ERROR -> WidgetTheme.STATUS_ERROR;
            case INFO -> WidgetTheme.STATUS_INFO;
        };
    }

    private int getTitleColor(PopUpSeverity severity) {
        return switch (severity) {
            case SUCCESS, INFO -> WidgetTheme.TITLE;
            case WARNING -> WidgetTheme.TEXT_ACCENT;
            case ERROR -> WidgetTheme.TEXT_SOFT;
        };
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

