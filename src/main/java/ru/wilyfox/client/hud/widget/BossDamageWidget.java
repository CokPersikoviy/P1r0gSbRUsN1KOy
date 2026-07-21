package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.boss.BossDamageInfo;
import ru.wilyfox.client.boss.BossDamageStore;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BossDamageWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int EMPTY_WIDTH = 118;
    private static final int EMPTY_HEIGHT = 28;
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));

    private final BossDamageStore store;

    public BossDamageWidget(int x, int y, HudLayer layer, BossDamageStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        BossDamageInfo info = store.getCurrent();

        if (info == null) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        String bossText = formatBoss(info);
        String damageText = formatDamage(info.damage());
        int width = getUnscaledWidth(bossText, damageText);
        int height = getUnscaledHeight();

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);
        context.drawString(mc.font, bossText, PADDING_X, PADDING_Y, WidgetTheme.TEXT_PRIMARY);
        context.drawString(mc.font, damageText, PADDING_X, PADDING_Y + mc.font.lineHeight + 2, WidgetTheme.TEXT_ACCENT);

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        BossDamageInfo info = store.getCurrent();
        if (info == null) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        return Math.round(getUnscaledWidth(formatBoss(info), formatDamage(info.damage())) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight() * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().bossDamage.active && (store.hasActiveEntry() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Boss Damage";
    }

    private int getUnscaledWidth(String bossText, String damageText) {
        Minecraft mc = Minecraft.getInstance();
        int maxWidth = Math.max(mc.font.width(bossText), mc.font.width(damageText));
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        return PADDING_Y * 2 + Minecraft.getInstance().font.lineHeight * 2 + 2;
    }

    private String formatBoss(BossDamageInfo info) {
        if (info.bossLevel() > 0) {
            return info.bossName() + " [" + info.bossLevel() + "]";
        }

        return info.bossName();
    }

    private String formatDamage(long damage) {
        return DAMAGE_FORMAT.format(Math.max(0L, damage));
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Boss Damage", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No recent hit", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

